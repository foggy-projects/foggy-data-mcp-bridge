# -*- coding: utf-8 -*-
import logging

from . import models  # noqa: F401
from . import controllers  # noqa: F401
from . import wizard  # noqa: F401

_logger = logging.getLogger(__name__)


def post_init_hook(cr, registry):
    """
    Post-init hook to ensure safe module installation.

    This hook runs after module installation/upgrade and handles:
    1. Ensures config parameters have default values
    2. Logs successful installation for troubleshooting

    This prevents crashes when new fields are added to res.config.settings
    without manual database migration.
    """
    _logger.info("Foggy MCP Gateway: Running post-init hook...")

    # Set default config parameters if not exists
    # These are read via ir.config_parameter, so they don't need DB columns
    default_params = {
        'foggy_mcp.server_url': '',
        'foggy_mcp.endpoint_path': '/mcp/analyst/rpc',
        'foggy_mcp.request_timeout': '30',
        'foggy_mcp.namespace': 'odoo',
        'foggy_mcp.cache_ttl': '300',
        'foggy_mcp.auth_token': '',
        'foggy_mcp.llm_provider': 'openai',
        'foggy_mcp.llm_api_key': '',
        'foggy_mcp.llm_model': 'gpt-4o-mini',
        'foggy_mcp.llm_base_url': '',
        'foggy_mcp.llm_temperature': '0.3',
        'foggy_mcp.llm_custom_prompt': '',
    }

    try:
        for key, default_value in default_params.items():
            cr.execute("""
                SELECT 1 FROM ir_config_parameter WHERE key = %s
            """, (key,))
            if not cr.fetchone():
                cr.execute("""
                    INSERT INTO ir_config_parameter (key, value)
                    VALUES (%s, %s)
                """, (key, default_value))
                _logger.debug("Created config parameter: %s", key)

        _logger.info("Foggy MCP Gateway: Post-init hook completed successfully")
    except Exception as e:
        # Log but don't fail - the module can still work
        _logger.warning("Foggy MCP Gateway: Post-init hook encountered an issue: %s", e)
