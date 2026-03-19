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
