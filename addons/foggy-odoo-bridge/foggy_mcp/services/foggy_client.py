# -*- coding: utf-8 -*-
"""
Foggy MCP Server HTTP Client

Handles HTTP communication with the Foggy MCP Server (Java side).
Odoo MCP Gateway injects permission conditions into payload.slice before
forwarding — no custom headers needed for permission control.
"""
import logging
import uuid

import requests

_logger = logging.getLogger(__name__)


class FoggyClient:
    """HTTP client for the Foggy MCP Server."""

    def __init__(self, base_url, endpoint_path='/mcp/analyst/rpc', timeout=30, namespace='', auth_token=None):
        self.base_url = base_url.rstrip('/')
        self.endpoint_path = endpoint_path
        self.timeout = timeout
        self.namespace = namespace
        self.auth_token = auth_token
        self._url = f"{self.base_url}{self.endpoint_path}"

    @classmethod
    def from_config(cls, env):
        """Create a FoggyClient from Odoo system parameters."""
        ICP = env['ir.config_parameter'].sudo()
        base_url = ICP.get_param('foggy_mcp.server_url', '')
        endpoint_path = ICP.get_param('foggy_mcp.endpoint_path', '/mcp/analyst/rpc')
        timeout = int(ICP.get_param('foggy_mcp.request_timeout', '30'))
        # Handle empty string - treat as default namespace (empty string, not 'odoo')
        namespace = ICP.get_param('foggy_mcp.namespace', '') or ''
        auth_token = ICP.get_param('foggy_mcp.auth_token', '')

        if not base_url:
            raise ValueError("Foggy MCP Server URL not configured. Go to Settings > Foggy MCP.")

        return cls(base_url, endpoint_path, timeout, namespace, auth_token)

    def call_tools_list(self):
        """
        Call tools/list on the Foggy MCP Server.

        Returns:
            list: List of tool definitions from Foggy
        """
        request_body = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "tools/list",
            "params": {}
        }
        response = self._send_request(request_body)
        result = response.get('result', {})
        return result.get('tools', [])

    def call_tools_call(self, tool_name, arguments, trace_id=None):
        """
        Forward a tools/call request to Foggy MCP Server.

        Permission conditions are already injected into arguments['payload']['slice']
        by the Odoo MCP Gateway before calling this method.

        Args:
            tool_name: The MCP tool name (e.g., 'dataset.query_model')
            arguments: Tool arguments dict (with permission slices already merged)
            trace_id: AI session trace ID

        Returns:
            dict: The full MCP response from Foggy
        """
        request_body = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments
            }
        }

        headers = {}
        if trace_id:
            headers['X-Trace-Id'] = trace_id

        _logger.info("FoggyClient.call_tools_call: tool=%s, arguments=%s", tool_name, arguments)
        response = self._send_request(request_body, extra_headers=headers)
        return response

    def call_initialize(self):
        """
        Call initialize on the Foggy MCP Server.

        Returns:
            dict: Server capabilities
        """
        request_body = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {
                    "name": "foggy-odoo-gateway",
                    "version": "1.0.0"
                }
            }
        }
        return self._send_request(request_body)

    def ping(self):
        """
        Ping the Foggy MCP Server to check connectivity.

        Returns:
            bool: True if server is reachable
        """
        try:
            request_body = {
                "jsonrpc": "2.0",
                "id": str(uuid.uuid4()),
                "method": "ping",
                "params": {}
            }
            self._send_request(request_body)
            return True
        except Exception:
            return False

    def _send_request(self, body, extra_headers=None):
        """
        Send a JSON-RPC request to the Foggy MCP Server.

        Args:
            body: JSON-RPC request body
            extra_headers: Additional HTTP headers

        Returns:
            dict: Parsed JSON response

        Raises:
            requests.RequestException: On network errors
            ValueError: On JSON-RPC error responses
        """
        headers = {
            'Content-Type': 'application/json',
            'X-Request-Id': str(uuid.uuid4()),
        }

        # Only add X-NS header if namespace is non-empty
        # Empty namespace means use default namespace on Java side
        if self.namespace and self.namespace.strip():
            headers['X-NS'] = self.namespace

        # Add Authorization header if auth token is configured
        if self.auth_token:
            headers['Authorization'] = f'Bearer {self.auth_token}'

        if extra_headers:
            headers.update(extra_headers)

        _logger.debug("Foggy request: url=%s, method=%s, headers=%s", self._url, body.get('method'), headers)

        try:
            resp = requests.post(
                self._url,
                json=body,
                headers=headers,
                timeout=self.timeout,
            )
            resp.raise_for_status()
            data = resp.json()

            # Check for JSON-RPC error
            if 'error' in data:
                error = data['error']
                error_msg = error.get('message', 'Unknown error')
                _logger.error("Foggy MCP error: code=%s, message=%s",
                              error.get('code'), error_msg)
                raise ValueError(f"Foggy MCP error: {error_msg}")

            return data

        except requests.Timeout:
            _logger.error("Foggy MCP request timed out: url=%s", self._url)
            raise
        except requests.ConnectionError:
            _logger.error("Cannot connect to Foggy MCP Server: url=%s", self._url)
            raise
