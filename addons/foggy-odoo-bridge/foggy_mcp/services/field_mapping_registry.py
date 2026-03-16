# -*- coding: utf-8 -*-
"""
Field Mapping Registry — loads model metadata from Foggy MCP Server
and builds per-model DB column → QM field reverse maps.

This enables dynamic permission field resolution without maintaining
a static DIRECT_FIELD_MAP for every model.

Architecture:
    Odoo startup / TTL refresh
        → call dataset.describe_model_internal (JSON format) for each QM model
        → parse fields[*].sourceColumn → build {db_col: qm_field} reverse map
        → parse models[*].factTable → build {db_table: qm_model} map

Usage:
    registry = FieldMappingRegistry(foggy_client)
    column_map = registry.get_column_map('OdooSaleOrderQueryModel')
    # {'user_id': 'salesperson$id', 'partner_id': 'partner$id', ...}
"""
import logging
import time

_logger = logging.getLogger(__name__)


class FieldMappingRegistry:
    """
    Loads model metadata from Foggy MCP Server and builds per-model
    DB column name → QM field name reverse maps.

    Reuses the lazy-load + TTL cache pattern from ToolRegistry.
    """

    def __init__(self, foggy_client, cache_ttl=300):
        """
        Args:
            foggy_client: FoggyClient instance for JSON-RPC calls
            cache_ttl: Cache time-to-live in seconds (default 300s = 5 min)
        """
        self._client = foggy_client
        self._cache_ttl = cache_ttl
        # {qm_model: {db_col: qm_field}}
        self._column_maps = None
        # {db_table_name: qm_model}
        self._table_to_model = None
        self._cache_timestamp = 0

    def get_column_map(self, qm_model_name):
        """
        Get the DB column → QM field mapping for a specific QM model.

        Args:
            qm_model_name: e.g. 'OdooSaleOrderQueryModel'

        Returns:
            dict: {db_column: qm_field} e.g. {'user_id': 'salesperson$id'}
                  Returns empty dict if model not found or not loaded.
        """
        self._ensure_loaded()
        return (self._column_maps or {}).get(qm_model_name, {})

    def get_table_to_model_map(self):
        """
        Get the DB table name → QM model name mapping.

        Useful for auto-discovering which QM model corresponds to which
        Odoo model (via env[model]._table matching factTable).

        Returns:
            dict: {db_table: qm_model} e.g. {'sale_order': 'OdooSaleOrderQueryModel'}
        """
        self._ensure_loaded()
        return dict(self._table_to_model or {})

    def invalidate_cache(self):
        """Force a full refresh on the next access."""
        self._column_maps = None
        self._table_to_model = None
        self._cache_timestamp = 0

    def _ensure_loaded(self):
        """Load metadata if cache is empty or expired."""
        now = time.time()
        if self._column_maps is not None and (now - self._cache_timestamp) < self._cache_ttl:
            return
        self._load_all_models()

    def _load_all_models(self):
        """
        Fetch metadata for all known QM models and build reverse maps.

        Uses dataset.describe_model_internal with format=json for each model.
        On failure, falls back to stale cache if available.
        """
        # Import here to avoid circular dependency
        from .tool_registry import MODEL_MAPPING

        qm_models = list(set(MODEL_MAPPING.values()))
        if not qm_models:
            _logger.warning("No QM models configured in MODEL_MAPPING, skipping metadata load")
            return

        _logger.info("Loading field metadata from Foggy for %d models...", len(qm_models))

        new_column_maps = {}
        new_table_to_model = {}

        for qm_model in qm_models:
            try:
                response = self._client.call_tools_call(
                    'dataset.describe_model_internal',
                    {'model': qm_model, 'format': 'json'}
                )
                # Extract the metadata from the MCP response
                data = self._extract_metadata(response)
                if data is None:
                    _logger.warning("No metadata returned for model %s", qm_model)
                    continue

                column_map, fact_table = self._parse_model_metadata(qm_model, data)
                new_column_maps[qm_model] = column_map
                if fact_table:
                    new_table_to_model[fact_table] = qm_model

                _logger.debug("Model %s: %d column mappings, factTable=%s",
                              qm_model, len(column_map), fact_table)

            except Exception as e:
                _logger.warning("Failed to load metadata for model %s: %s", qm_model, e)

        if new_column_maps:
            self._column_maps = new_column_maps
            self._table_to_model = new_table_to_model
            self._cache_timestamp = time.time()
            _logger.info("Field metadata loaded: %d models, %d table mappings",
                         len(new_column_maps), len(new_table_to_model))
        else:
            # All models failed — keep stale cache if available
            if self._column_maps is not None:
                _logger.warning("All model metadata loads failed, using stale cache")
            else:
                _logger.error("All model metadata loads failed, no cache available")
                self._column_maps = {}
                self._table_to_model = {}
                self._cache_timestamp = time.time()

    @staticmethod
    def _extract_metadata(mcp_response):
        """
        Extract the metadata dict from an MCP JSON-RPC response.

        The describe_model_internal tool returns its data inside the
        standard MCP result structure.

        Args:
            mcp_response: Full JSON-RPC response dict

        Returns:
            dict or None: The metadata dict containing 'fields' and 'models'
        """
        if not mcp_response or not isinstance(mcp_response, dict):
            return None

        result = mcp_response.get('result', {})

        # MCP tools return content as list of content blocks
        content = result.get('content', [])
        if isinstance(content, list):
            for block in content:
                if isinstance(block, dict) and block.get('type') == 'text':
                    text = block.get('text', '')
                    try:
                        import json
                        return json.loads(text)
                    except (json.JSONDecodeError, TypeError):
                        pass

        # Fallback: try direct data access (RX wrapper)
        data = result.get('data')
        if isinstance(data, dict):
            # SemanticMetadataResponse has a 'data' field with the metadata
            inner = data.get('data')
            if isinstance(inner, dict) and 'fields' in inner:
                return inner
            if 'fields' in data:
                return data

        return None

    @staticmethod
    def _parse_model_metadata(qm_model_name, data):
        """
        Parse the describe_model_internal JSON response into reverse maps.

        Args:
            qm_model_name: The QM model name
            data: The metadata dict with 'fields' and 'models' keys

        Returns:
            tuple: (column_map, fact_table)
                - column_map: {db_column: qm_field_name}
                - fact_table: str or None
        """
        column_map = {}
        fields = data.get('fields', {})
        for field_name, field_info in fields.items():
            if not isinstance(field_info, dict):
                continue
            source_col = field_info.get('sourceColumn')
            if source_col:
                column_map[source_col] = field_name

        # Extract factTable from models info
        models = data.get('models', {})
        model_info = models.get(qm_model_name, {})
        fact_table = model_info.get('factTable') if isinstance(model_info, dict) else None

        return column_map, fact_table
