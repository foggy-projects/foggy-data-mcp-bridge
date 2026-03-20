# -*- coding: utf-8 -*-
"""
Foggy MCP Gateway Settings

Configuration stored in ir.config_parameter (no DB column needed).
Fields with config_parameter attribute are automatically mapped.

IMPORTANT: After adding new fields, always run module upgrade:
    docker exec <odoo_container> odoo -u foggy_mcp -d <database> --stop-after-init

This ensures Odoo properly registers the new fields without requiring
database columns (TransientModel fields are stored in ir.config_parameter).
"""
import logging

from odoo import _, api, fields, models
from odoo.exceptions import UserError

_logger = logging.getLogger(__name__)


class ResConfigSettings(models.TransientModel):
    _inherit = 'res.config.settings'

    foggy_mcp_url = fields.Char(
        string='Foggy MCP Server URL',
        config_parameter='foggy_mcp.server_url',
        help='Base URL of the Foggy MCP Server (e.g., http://foggy-mcp:8080)',
    )
    foggy_mcp_endpoint = fields.Char(
        string='MCP Endpoint Path',
        config_parameter='foggy_mcp.endpoint_path',
        default='/mcp/analyst/rpc',
        help='MCP JSON-RPC endpoint path on the Foggy server',
    )
    foggy_mcp_timeout = fields.Integer(
        string='Request Timeout (seconds)',
        config_parameter='foggy_mcp.request_timeout',
        default=30,
        help='HTTP request timeout when calling the Foggy MCP Server',
    )
    foggy_mcp_namespace = fields.Char(
        string='Namespace',
        config_parameter='foggy_mcp.namespace',
        default='odoo',
        help='Namespace for Odoo models in Foggy (X-NS header value)',
    )
    foggy_mcp_cache_ttl = fields.Integer(
        string='Tool Cache TTL (seconds)',
        config_parameter='foggy_mcp.cache_ttl',
        default=300,
        help='How long to cache the tools/list response from Foggy',
    )
    foggy_mcp_auth_token = fields.Char(
        string='Auth Token',
        config_parameter='foggy_mcp.auth_token',
        help='Bearer token for authenticating with Foggy MCP Server. Leave empty if auth is disabled on the server.',
    )

    # ── AI Chat (LLM) Settings ──────────────────────────

    foggy_llm_provider = fields.Selection([
        ('openai', 'OpenAI'),
        ('anthropic', 'Anthropic (Claude)'),
        ('deepseek', 'DeepSeek'),
        ('ollama', 'Ollama (Local)'),
        ('custom', 'Custom (OpenAI-compatible)'),
    ], string='LLM Provider',
        config_parameter='foggy_mcp.llm_provider',
        default='openai',
        help='AI model provider for the embedded chat feature.',
    )
    foggy_llm_api_key = fields.Char(
        string='LLM API Key',
        config_parameter='foggy_mcp.llm_api_key',
        help='API key for the LLM provider (not needed for Ollama).',
    )
    foggy_llm_model = fields.Char(
        string='Model Name',
        config_parameter='foggy_mcp.llm_model',
        default='gpt-4o-mini',
        help='Model identifier (e.g., gpt-4o, claude-3-5-sonnet-20241022, deepseek-chat, llama3).',
    )
    foggy_llm_base_url = fields.Char(
        string='API Base URL',
        config_parameter='foggy_mcp.llm_base_url',
        help='Custom API endpoint. Required for Ollama (http://localhost:11434/v1) and custom providers.',
    )
    foggy_llm_temperature = fields.Float(
        string='Temperature',
        config_parameter='foggy_mcp.llm_temperature',
        default=0.3,
        help='Controls randomness. Lower = more focused, higher = more creative. (0.0 - 1.0)',
    )
    foggy_llm_custom_prompt = fields.Char(
        string='Business Context Prompt',
        config_parameter='foggy_mcp.llm_custom_prompt',
        help='Custom business context injected into the AI system prompt.',
    )

    # ── Connection Test ─────────────────────────────────────

    foggy_connection_status = fields.Text(
        string='Connection Status',
        readonly=True,
    )

    # ── Safe Execute Override ─────────────────────────────────────

    def execute(self):
        """
        Override execute to provide better error messages for schema issues.

        When new config_parameter fields are added without running module upgrade,
        Odoo may raise "column does not exist" errors. This override catches
        those errors and provides actionable guidance.
        """
        try:
            return super().execute()
        except Exception as e:
            error_msg = str(e)

            # Check for common schema-related errors
            if 'column' in error_msg.lower() and 'does not exist' in error_msg.lower():
                _logger.error("Schema error when saving settings: %s", error_msg)
                raise UserError(_(
                    "Settings cannot be saved because the module needs to be upgraded.\n\n"
                    "Please run the following command:\n"
                    "    docker exec <odoo_container> odoo -u foggy_mcp -d <database> --stop-after-init\n\n"
                    "Then restart Odoo and try again.\n\n"
                    "Technical details: %s"
                ) % error_msg)

            # Re-raise other errors as-is
            raise

    def action_upgrade_module(self):
        """
        Action to trigger module upgrade from settings UI.
        Opens a wizard or shows instructions.
        """
        self.ensure_one()
        return {
            'type': 'ir.actions.client',
            'tag': 'display_notification',
            'params': {
                'title': _('Module Upgrade Required'),
                'message': _(
                    'Please run: docker exec <odoo_container> odoo -u foggy_mcp -d <database> --stop-after-init'
                ),
                'type': 'warning',
                'sticky': True,
            }
        }

    def action_test_connection(self):
        """
        Test connection to Foggy MCP Server with detailed error guidance.
        Provides user-friendly troubleshooting steps when connection fails.
        """
        self.ensure_one()

        url = self.foggy_mcp_url or 'http://localhost:8080'
        endpoint = self.foggy_mcp_endpoint or '/mcp/analyst/rpc'
        full_url = f"{url.rstrip('/')}{endpoint}"

        try:
            import requests

            # Try health check first
            try:
                health_url = f"{url.rstrip('/')}/actuator/health"
                r = requests.get(health_url, timeout=5)

                if r.status_code == 200:
                    # Server is healthy, try MCP endpoint
                    mcp_payload = {
                        "jsonrpc": "2.0",
                        "method": "initialize",
                        "params": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {},
                            "clientInfo": {"name": "odoo-test", "version": "1.0"}
                        },
                        "id": 1
                    }

                    headers = {'Content-Type': 'application/json'}
                    if self.foggy_mcp_auth_token:
                        headers['Authorization'] = f'Bearer {self.foggy_mcp_auth_token}'

                    r_mcp = requests.post(full_url, json=mcp_payload, headers=headers, timeout=10)

                    if r_mcp.status_code == 200:
                        result = r_mcp.json()
                        if 'result' in result:
                            server_info = result['result'].get('serverInfo', {})
                            status_msg = _(
                                "✅ Connection successful!\n\n"
                                "Server: %(name)s v%(version)s\n"
                                "Protocol: %(protocol)s\n"
                                "URL: %(url)s"
                            ) % {
                                'name': server_info.get('name', 'Foggy MCP'),
                                'version': server_info.get('version', 'unknown'),
                                'protocol': result['result'].get('protocolVersion', 'unknown'),
                                'url': url,
                            }
                        else:
                            status_msg = _(
                                "⚠️ Server responded but MCP initialization failed.\n\n"
                                "Response: %(response)s"
                            ) % {'response': r_mcp.text[:200]}
                    elif r_mcp.status_code == 401:
                        status_msg = _(
                            "❌ Authentication failed.\n\n"
                            "The server requires a valid Auth Token.\n"
                            "Please check your Auth Token setting."
                        )
                    else:
                        status_msg = _(
                            "❌ MCP endpoint returned HTTP %(status)d.\n\n"
                            "Response: %(response)s"
                        ) % {'status': r_mcp.status_code, 'response': r_mcp.text[:200]}
                else:
                    status_msg = self._build_error_guidance(
                        url, f"Health check failed with HTTP {r.status_code}"
                    )

            except requests.exceptions.ConnectionError as e:
                status_msg = self._build_error_guidance(url, str(e))
            except requests.exceptions.Timeout:
                status_msg = self._build_error_guidance(
                    url, "Connection timed out after 5 seconds"
                )

        except ImportError:
            status_msg = _(
                "❌ Missing required library.\n\n"
                "Please install: pip install requests"
            )

        # Show result in a modal dialog with the status message
        return {
            'type': 'ir.actions.act_window',
            'res_model': 'foggy.connection.test.result',
            'view_mode': 'form',
            'target': 'new',
            'context': {
                'default_status_message': status_msg,
            }
        }

    def _build_error_guidance(self, url, error_detail):
        """
        Build user-friendly error message with troubleshooting steps.

        Args:
            url: The URL that failed to connect
            error_detail: Technical error message

        Returns:
            str: User-friendly error message with troubleshooting guidance
        """
        import os

        # Detect environment
        in_docker = os.path.exists('/.dockerenv')

        guidance_lines = [
            _("❌ Cannot connect to Foggy MCP Server"),
            "",
            _("URL: %(url)s") % {'url': url},
            _("Error: %(error)s") % {'error': error_detail},
            "",
            "═══════════════════════════════════════",
            _("Troubleshooting steps:"),
            "",
            _("1. Check if Foggy MCP container is running:"),
            "   docker ps | grep foggy-mcp",
            "",
            _("2. Check container logs for errors:"),
            "   docker logs foggy-mcp",
            "",
        ]

        if in_docker:
            guidance_lines.extend([
                _("3. Both Odoo and Foggy are in Docker:"),
                _("   • Ensure they're on the same network: docker network ls"),
                _("   • Try URL: http://foggy-mcp:8080"),
                "",
                _("4. Or use Odoo's container network:"),
                "   docker run --network container:odoo ...",
            ])
        else:
            guidance_lines.extend([
                _("3. Check if port is accessible:"),
                f"   curl http://localhost:8080/actuator/health",
                "",
                _("4. If running in Docker, check port mapping:"),
                "   docker port foggy-mcp",
            ])

        guidance_lines.extend([
            "",
            _("5. Verify Auth Token matches server config."),
        ])

        return '\n'.join(guidance_lines)
