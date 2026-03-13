# -*- coding: utf-8 -*-
{
    'name': 'Foggy MCP Gateway',
    'version': '17.0.1.0.0',
    'category': 'Technical',
    'summary': 'MCP Gateway for AI-powered natural language data queries via Foggy Framework',
    'description': """
Foggy MCP Gateway
==================

Provides an MCP (Model Context Protocol) endpoint for AI clients (Claude Desktop, Cursor, etc.)
to query Odoo business data using natural language.

Architecture:
    AI Client --MCP--> Odoo (this addon) --HTTP--> Foggy MCP Server --SQL--> PostgreSQL

Features:
    - MCP JSON-RPC 2.0 endpoint at /foggy-mcp/rpc
    - API Key authentication (Bearer token)
    - Per-user tool filtering based on ir.model.access
    - Automatic permission injection via X-Forced-Filters (ir.rule → WHERE clauses)
    - Tool registry with caching from Foggy MCP Server
    - Multi-company support

Security:
    - Users only see data they have access to (enforced by Odoo ir.rule)
    - Forced filters are injected server-side and cannot be bypassed by the AI client
    - API keys are scoped to individual users
    """,
    'author': 'Foggy Framework',
    'website': 'https://github.com/nicholasgasior/foggy-data-mcp-bridge',
    'license': 'Apache-2.0',
    'depends': ['base', 'sale', 'purchase', 'account', 'stock', 'hr'],
    'data': [
        'security/ir.model.access.csv',
        'security/foggy_security.xml',
        'views/foggy_config_views.xml',
        'views/foggy_api_key_views.xml',
        'data/foggy_data.xml',
    ],
    'installable': True,
    'application': False,
    'auto_install': False,
}
