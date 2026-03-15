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
