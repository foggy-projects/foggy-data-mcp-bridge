# -*- coding: utf-8 -*-
"""
MCP JSON-RPC 2.0 Controller for Odoo

Provides a single endpoint at /foggy-mcp/rpc that:
1. Authenticates via API Key (Bearer token) or Odoo session cookie
2. Routes MCP methods (initialize, tools/list, tools/call, ping)
3. Injects permission conditions into payload.slice for tools/call
4. Forwards tools/call requests to the Foggy MCP Server

Odoo 17 compatibility:
    Uses type='http' + request.get_json_data() instead of the removed
    type='json' + request.jsonrequest pattern.
"""
import json
import logging
import uuid

from odoo import http
from odoo.http import request, Response

from ..services.foggy_client import FoggyClient
from ..services.tool_registry import ToolRegistry, MODEL_MAPPING, QM_TO_ODOO_MODEL
from ..services.permission_bridge import compute_permission_slices

_logger = logging.getLogger(__name__)

# Protocol version supported
PROTOCOL_VERSION = '2024-11-05'

# Singleton-like registry (lazily initialized per worker process)
_tool_registry = None
_foggy_client = None


def _get_foggy_client(env):
    """Get or create the FoggyClient singleton."""
    global _foggy_client
    if _foggy_client is None:
        _foggy_client = FoggyClient.from_config(env)
    return _foggy_client


def _get_tool_registry(env):
    """Get or create the ToolRegistry singleton."""
    global _tool_registry
    if _tool_registry is None:
        client = _get_foggy_client(env)
        cache_ttl = int(env['ir.config_parameter'].sudo().get_param(
            'foggy_mcp.cache_ttl', '300'))
        _tool_registry = ToolRegistry(client, cache_ttl)
    return _tool_registry


def _reset_singletons():
    """Reset singletons (for testing or config changes)."""
    global _tool_registry, _foggy_client
    _tool_registry = None
    _foggy_client = None


def _json_response(data, status=200):
    """Build a JSON HTTP response."""
    return Response(
        json.dumps(data, ensure_ascii=False),
        status=status,
        headers={
            'Content-Type': 'application/json; charset=utf-8',
            'Access-Control-Allow-Origin': '*',
        }
    )


class McpController(http.Controller):
    """MCP JSON-RPC 2.0 endpoint for AI clients."""

    @http.route('/foggy-mcp/rpc', type='http', auth='none', methods=['POST', 'OPTIONS'],
                csrf=False, cors='*')
    def handle_rpc(self, **kwargs):
        """
        Main MCP JSON-RPC endpoint.

        Authentication:
            - Bearer token (API Key): Authorization header
            - Odoo session cookie: automatic session auth

        Supported methods:
            - initialize: Server capabilities
            - tools/list: Available tools (filtered by user permissions)
            - tools/call: Execute a tool (with permission slices injected)
            - ping: Health check

        Note: Uses type='http' for Odoo 17 compatibility.
              Odoo 17 removed request.jsonrequest; uses request.get_json_data() instead.
        """
        # Handle CORS preflight
        if request.httprequest.method == 'OPTIONS':
            return Response('', status=204, headers={
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Methods': 'POST, OPTIONS',
                'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Trace-Id',
            })

        jsonrpc_request = None
        try:
            # Parse request body (Odoo 17 compatible)
            jsonrpc_request = request.get_json_data()
            method = jsonrpc_request.get('method')
            params = jsonrpc_request.get('params', {})
            request_id = jsonrpc_request.get('id')
            trace_id = request.httprequest.headers.get('X-Trace-Id', str(uuid.uuid4()))

            _logger.info("MCP request: method=%s, id=%s, trace_id=%s", method, request_id, trace_id)

            # Authenticate
            user = self._authenticate()
            if not user:
                return self._jsonrpc_error(request_id, -32000, 'Authentication required')

            env = request.env(user=user.id)

            # Route by method
            if method == 'initialize':
                result = self._handle_initialize(request_id)
            elif method == 'tools/list':
                result = self._handle_tools_list(request_id, env, user)
            elif method == 'tools/call':
                result = self._handle_tools_call(request_id, params, env, user, trace_id)
            elif method == 'ping':
                result = self._handle_ping(request_id)
            else:
                return self._jsonrpc_error(request_id, -32601, f'Method not found: {method}')

            # Check if result is an error response
            if isinstance(result, dict) and 'error' in result:
                return self._jsonrpc_response(request_id, result=result)

            return self._jsonrpc_response(request_id, result=result)

        except Exception as e:
            _logger.error("MCP error: %s", e, exc_info=True)
            req_id = jsonrpc_request.get('id') if jsonrpc_request else None
            return self._jsonrpc_error(req_id, -32603, str(e))

    def _authenticate(self):
        """
        Authenticate the request.

        Tries:
        1. API Key (Bearer token in Authorization header)
        2. Odoo session (cookie-based)

        Returns:
            res.users record or None
        """
        auth_header = request.httprequest.headers.get('Authorization', '')

        # Try API Key auth
        if auth_header.startswith('Bearer fmcp_'):
            user = request.env['foggy.api.key'].sudo().authenticate_by_key(auth_header)
            if user:
                return user
            return None

        # Try session auth
        if request.session.uid:
            return request.env['res.users'].sudo().browse(request.session.uid)

        return None

    def _handle_initialize(self, request_id):
        """Handle MCP initialize request."""
        return {
            'protocolVersion': PROTOCOL_VERSION,
            'capabilities': {
                'tools': {'listChanged': False},
            },
            'serverInfo': {
                'name': 'foggy-odoo-gateway',
                'version': '1.0.0',
            }
        }

    def _handle_tools_list(self, request_id, env, user):
        """Handle MCP tools/list request — filtered by user permissions."""
        try:
            registry = _get_tool_registry(env)
            tools = registry.get_tools_for_user(env, user.id)
        except Exception as e:
            _logger.error("Failed to load tools: %s", e, exc_info=True)
            return {
                'error': {
                    'code': -32002,
                    'message': f'Failed to load tools from Foggy MCP Server: {e}'
                }
            }

        _logger.info("tools/list: user=%s, tools=%d", user.login, len(tools))
        return {'tools': tools}

    def _handle_tools_call(self, request_id, params, env, user, trace_id):
        """
        Handle MCP tools/call request.

        1. Validate tool name and arguments
        2. Check model-level access (ir.model.access)
        3. Compute permission slices from ir.rule and inject into payload.slice
        4. Forward to Foggy MCP Server
        """
        tool_name = params.get('name', '')
        arguments = params.get('arguments', {})

        if not tool_name:
            return {
                'error': {
                    'code': -32602,
                    'message': 'Missing tool name'
                }
            }

        _logger.info("tools/call: user=%s, tool=%s, trace_id=%s",
                      user.login, tool_name, trace_id)

        # For dataset.query_model: check access + inject permission slices
        if tool_name == 'dataset.query_model':
            model_name = arguments.get('model')
            if model_name:
                # Model-level access check (ir.model.access)
                odoo_model = QM_TO_ODOO_MODEL.get(model_name)
                if odoo_model:
                    has_access = env['ir.model.access'].check(
                        odoo_model, 'read', raise_exception=False
                    )
                    if not has_access:
                        _logger.warning(
                            "Access denied: user=%s, model=%s", user.login, odoo_model
                        )
                        return {
                            'error': {
                                'code': -32003,
                                'message': f'Access denied: no read permission on {odoo_model}'
                            }
                        }

                # Row-level access: compute permission slices and inject into payload.slice
                try:
                    perm_slices = compute_permission_slices(env, user.id, model_name)
                    if perm_slices:
                        payload = arguments.setdefault('payload', {})
                        existing_slice = payload.setdefault('slice', [])
                        existing_slice.extend(perm_slices)
                        _logger.debug("Permission slices injected for user %s on %s: %d conditions",
                                      user.login, model_name, len(perm_slices))
                except Exception as e:
                    _logger.error("Failed to compute permission slices: %s", e, exc_info=True)
                    # Fail closed: deny access if we can't compute permissions
                    return {
                        'error': {
                            'code': -32004,
                            'message': 'Failed to compute access permissions. Access denied for safety.'
                        }
                    }

        # Forward to Foggy MCP Server
        try:
            client = _get_foggy_client(env)
            response = client.call_tools_call(
                tool_name=tool_name,
                arguments=arguments,
                trace_id=trace_id,
            )
        except Exception as e:
            _logger.error("Foggy MCP Server error: %s", e, exc_info=True)
            return {
                'error': {
                    'code': -32005,
                    'message': f'Foggy MCP Server unavailable: {e}'
                }
            }

        # Return the Foggy response result directly
        return response.get('result', {})

    def _handle_ping(self, request_id):
        """Handle MCP ping request."""
        return {}

    def _jsonrpc_response(self, request_id, result=None):
        """Build a JSON-RPC 2.0 success response."""
        body = {
            'jsonrpc': '2.0',
            'id': request_id,
        }
        if result is not None:
            body['result'] = result
        return _json_response(body)

    def _jsonrpc_error(self, request_id, code, message):
        """Build a JSON-RPC 2.0 error response."""
        return _json_response({
            'jsonrpc': '2.0',
            'id': request_id,
            'error': {
                'code': code,
                'message': message,
            }
        })

    # ─── Diagnostics endpoint ────────────────────────────────────

    @http.route('/foggy-mcp/health', type='http', auth='none', methods=['GET'],
                csrf=False, cors='*')
    def handle_health(self, **kwargs):
        """
        Health check and connection diagnostics.

        Returns JSON with:
        - gateway status (always ok if this responds)
        - foggy server connectivity
        - tool cache status
        - configuration summary

        No authentication required (for monitoring tools).
        """
        import time as _time

        result = {
            'status': 'ok',
            'gateway': 'foggy-odoo-mcp',
            'timestamp': _time.strftime('%Y-%m-%dT%H:%M:%SZ', _time.gmtime()),
            'checks': {}
        }

        env = request.env(su=True)

        # Check 1: Foggy MCP Server connectivity
        try:
            client = _get_foggy_client(env)
            server_ok = client.ping()
            result['checks']['foggy_server'] = {
                'status': 'ok' if server_ok else 'error',
                'url': client._url,
            }
        except Exception as e:
            result['checks']['foggy_server'] = {
                'status': 'error',
                'error': str(e),
            }
            result['status'] = 'degraded'

        # Check 2: Tool cache status
        try:
            registry = _get_tool_registry(env)
            all_tools = registry.get_all_tools()
            cache_age = int(_time.time() - registry._cache_timestamp) if registry._cache_timestamp else -1
            result['checks']['tool_cache'] = {
                'status': 'ok' if all_tools else 'empty',
                'tool_count': len(all_tools),
                'cache_age_seconds': cache_age,
                'cache_ttl': registry._cache_ttl,
            }
        except Exception as e:
            result['checks']['tool_cache'] = {
                'status': 'error',
                'error': str(e),
            }

        # Check 3: Configuration
        ICP = env['ir.config_parameter'].sudo()
        result['checks']['config'] = {
            'server_url': ICP.get_param('foggy_mcp.server_url', '(not set)'),
            'namespace': ICP.get_param('foggy_mcp.namespace', 'odoo'),
            'timeout': ICP.get_param('foggy_mcp.request_timeout', '30'),
        }

        # Check 4: Model mapping
        result['checks']['models'] = {
            'mapped_count': len(MODEL_MAPPING),
            'models': list(MODEL_MAPPING.keys()),
        }

        headers = {'Content-Type': 'application/json'}
        return Response(
            json.dumps(result, indent=2),
            status=200, headers=headers
        )
