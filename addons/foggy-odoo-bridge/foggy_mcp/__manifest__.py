# -*- coding: utf-8 -*-
{
    'name': 'Foggy MCP Gateway',
    'version': '17.0.1.4.0',
    'category': 'Technical',
    'summary': 'MCP Gateway for AI-powered natural language data queries via Foggy Framework',
    'description': """
Foggy MCP Gateway
==================

Provides an MCP (Model Context Protocol) endpoint for AI clients (Claude Desktop, Cursor, etc.)
to query Odoo business data using natural language.

Architecture::

    AI Client --MCP--> Odoo (this addon) --HTTP--> Foggy MCP Server --SQL--> PostgreSQL

Features:
    - MCP JSON-RPC 2.0 endpoint at /foggy-mcp/rpc
    - API Key authentication (Bearer token)
    - Per-user tool filtering based on ir.model.access
    - Automatic permission injection (ir.rule -> DSL slice conditions)
    - Closure table hierarchy queries (child_of/parent_of -> selfAndDescendantsOf/selfAndAncestorsOf)
    - Tool registry with caching from Foggy MCP Server
    - Multi-company support
    - 8 pre-built Odoo business models (Sale, Purchase, Invoice, Stock, HR, Partner, Company, CRM)
    - Built-in AI Chat: talk to your data directly from Odoo (supports OpenAI, Anthropic, DeepSeek, Ollama)

Security:
    - Users only see data they have access to (enforced by Odoo ir.rule)
    - Forced filters are injected server-side and cannot be bypassed by the AI client
    - API keys are scoped to individual users
    - Fail-closed: permission errors deny access rather than granting it

Data Privacy Notice:
    This addon forwards query requests to a Foggy MCP Server that YOU deploy
    (typically on the same network or server as your Odoo instance).
    No data is sent to any third-party cloud service.
    All communication uses HTTP between Odoo and Foggy MCP Server.
    HTTPS is strongly recommended for production environments.
    The Foggy MCP Server connects directly to your PostgreSQL database (read-only queries).
    """,
    'author': 'Foggy Framework',
    'website': 'https://github.com/nicholasgasior/foggy-data-mcp-bridge',
    'license': 'Other OSI approved licence',
    'depends': ['base', 'sale', 'purchase', 'account', 'stock', 'hr', 'crm'],
    'external_dependencies': {
        'python': ['litellm'],
    },
    'data': [
        'security/ir.model.access.csv',
        'security/foggy_security.xml',
        'views/foggy_api_key_views.xml',
        'views/foggy_config_views.xml',
        'wizard/foggy_setup_wizard_views.xml',
        'views/foggy_chat_views.xml',
        'views/connection_test_result_views.xml',
        'data/foggy_data.xml',
    ],
    'assets': {
        'web.assets_backend': [
            'foggy_mcp/static/src/components/**/*.js',
            'foggy_mcp/static/src/components/**/*.xml',
            'foggy_mcp/static/src/components/**/*.scss',
        ],
    },
    'images': ['static/description/banner.png'],
    'price': 0,
    'currency': 'EUR',
    'installable': True,
    'application': True,
    'auto_install': False,
    'post_init_hook': 'post_init_hook',
}
