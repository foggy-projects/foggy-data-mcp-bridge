# -*- coding: utf-8 -*-
"""
Tool Registry — loads tools from Foggy MCP Server and filters per user.

On startup (or first request), fetches the full tool list from Foggy via tools/list,
caches it, and for each tools/list request from an AI client, filters tools based on
the user's ir.model.access permissions.
"""
import logging
import time

_logger = logging.getLogger(__name__)

# Mapping: Odoo model name → QM model name(s) that query it
# Used to determine which tools a user can access based on ir.model.access
MODEL_MAPPING = {
    'sale.order': 'OdooSaleOrderQueryModel',
    'sale.order.line': 'OdooSaleOrderLineQueryModel',
    'purchase.order': 'OdooPurchaseOrderQueryModel',
    'account.move': 'OdooAccountMoveQueryModel',
    'stock.picking': 'OdooStockPickingQueryModel',
    'hr.employee': 'OdooHrEmployeeQueryModel',
    'res.partner': 'OdooResPartnerQueryModel',
    'res.company': 'OdooResCompanyQueryModel',
    'crm.lead': 'OdooCrmLeadQueryModel',
}

# Reverse mapping: QM model name → Odoo model name
QM_TO_ODOO_MODEL = {v: k for k, v in MODEL_MAPPING.items()}

# Tool names that are model-specific (contain a model parameter)
MODEL_TOOL_NAMES = {'dataset.query_model'}

# Tool names that are always available (no model restriction)
# Note: actual Foggy tool names are dataset.get_metadata and dataset.describe_model_internal
UNIVERSAL_TOOL_NAMES = {
    'dataset.list_models', 'dataset.get_schema', 'dataset_nl.query',  # legacy names
    'dataset.get_metadata', 'dataset.describe_model_internal',         # current Foggy names
}

# Tools blocked in this version (deferred to future releases)
# - export_with_chart: requires chart-render-service integration
# - validate: language-level schema validation, planned for v1.2+
BLOCKED_TOOL_NAMES = {'dataset.export_with_chart', 'semantic_layer.validate'}


class ToolRegistry:
    """
    Caches and filters Foggy MCP tools per user.

    Usage:
        registry = ToolRegistry(foggy_client, cache_ttl=300)
        tools = registry.get_tools_for_user(env, uid)
    """

    def __init__(self, foggy_client, cache_ttl=300):
        self._client = foggy_client
        self._cache_ttl = cache_ttl
        self._cached_tools = None
        self._cache_timestamp = 0

    def get_all_tools(self):
        """
        Get the full tool list from Foggy (cached).

        Returns:
            list: All tool definitions from Foggy MCP Server
        """
        now = time.time()
        if self._cached_tools is not None and (now - self._cache_timestamp) < self._cache_ttl:
            return self._cached_tools

        _logger.info("Refreshing tool cache from Foggy MCP Server...")
        try:
            tools = self._client.call_tools_list()
            self._cached_tools = tools
            self._cache_timestamp = now
            _logger.info("Tool cache refreshed: %d tools loaded", len(tools))
            return tools
        except Exception as e:
            _logger.error("Failed to load tools from Foggy: %s", e)
            # Return stale cache if available
            if self._cached_tools is not None:
                _logger.warning("Using stale tool cache")
                return self._cached_tools
            return []

    def invalidate_cache(self):
        """Force cache refresh on next call."""
        self._cached_tools = None
        self._cache_timestamp = 0

    def get_tools_for_user(self, env, uid):
        """
        Get tools filtered by user's ir.model.access permissions.

        Args:
            env: Odoo environment
            uid: User ID

        Returns:
            list: Filtered tool definitions
        """
        all_tools = self.get_all_tools()
        if not all_tools:
            return []

        # Get the list of Odoo models the user can read
        accessible_models = self._get_accessible_models(env, uid)
        # Map to QM model names
        accessible_qm_models = set()
        for odoo_model in accessible_models:
            qm_name = MODEL_MAPPING.get(odoo_model)
            if qm_name:
                accessible_qm_models.add(qm_name)

        _logger.debug("User %s can access QM models: %s", uid, accessible_qm_models)

        # Filter tools
        filtered_tools = []
        for tool in all_tools:
            tool_name = tool.get('name', '')

            # Skip blocked tools (deferred to future releases)
            if tool_name in BLOCKED_TOOL_NAMES:
                continue

            # Universal tools are always included
            if tool_name in UNIVERSAL_TOOL_NAMES:
                enhanced = self._enhance_tool(tool, accessible_qm_models)
                filtered_tools.append(enhanced)
                continue

            # Model-specific tools: include if user has access to any model
            if tool_name in MODEL_TOOL_NAMES:
                if accessible_qm_models:
                    enhanced = self._enhance_tool(tool, accessible_qm_models)
                    filtered_tools.append(enhanced)
                continue

            # Unknown tools: include by default (safe — Foggy enforces permissions)
            filtered_tools.append(tool)

        _logger.debug("User %s: %d/%d tools available", uid, len(filtered_tools), len(all_tools))
        return filtered_tools

    def _get_accessible_models(self, env, uid):
        """
        Get Odoo model names the user has read access to.

        Uses ir.model.access.check() with the user's environment (not sudo)
        to properly evaluate group-based access control.

        Args:
            env: Odoo environment (should be bound to the target user via env(user=uid))
            uid: User ID

        Returns:
            set: Set of Odoo model names (e.g., {'sale.order', 'res.partner'})
        """
        accessible = set()

        # Use user-scoped env for proper permission evaluation
        user_env = env(user=uid)

        for odoo_model in MODEL_MAPPING:
            try:
                # Check that the model exists in this Odoo installation
                if odoo_model not in user_env:
                    continue

                # ir.model.access.check() raises AccessError if denied,
                # returns True if allowed.  raise_exception=False returns bool.
                has_access = user_env['ir.model.access'].check(
                    odoo_model, 'read', raise_exception=False
                )
                if has_access:
                    accessible.add(odoo_model)
            except Exception:
                # Model might not be installed or registry issue
                pass

        return accessible

    def _enhance_tool(self, tool, accessible_qm_models):
        """
        Enhance a tool definition with user-specific information.

        - Inject available model list into description
        - Add enum constraint to model parameter in inputSchema

        Args:
            tool: Original tool definition from Foggy
            accessible_qm_models: Set of QM model names user can access

        Returns:
            dict: Enhanced tool definition (copy)
        """
        enhanced = dict(tool)

        if not accessible_qm_models:
            return enhanced

        model_list = sorted(accessible_qm_models)
        model_names_str = ', '.join(model_list)

        # Enhance description
        desc = enhanced.get('description', '')
        enhanced['description'] = f"{desc}\n\nAvailable models: {model_names_str}"

        # Enhance inputSchema — add enum to 'model' parameter
        schema = enhanced.get('inputSchema')
        if schema and isinstance(schema, dict):
            schema = dict(schema)
            props = schema.get('properties', {})
            if 'model' in props:
                props = dict(props)
                model_prop = dict(props['model'])
                model_prop['enum'] = model_list
                props['model'] = model_prop
                schema['properties'] = props
            enhanced['inputSchema'] = schema

        return enhanced
