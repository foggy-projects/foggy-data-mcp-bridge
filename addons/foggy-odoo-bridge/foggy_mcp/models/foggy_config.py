# -*- coding: utf-8 -*-
import logging

from odoo import fields, models

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
