# -*- coding: utf-8 -*-
"""
Field Mapping Registry — loads model metadata from Foggy MCP Server
and builds per-model DB column → QM field reverse maps.

This enables dynamic permission field resolution without maintaining
a static DIRECT_FIELD_MAP for every model.

Architecture (two-phase loading):
    Phase 1 — Discovery:
        → call dataset.get_metadata (JSON) once
        → parse models section → {fact_table: qm_model} + qm_model list
    Phase 2 — Detail:
        → call dataset.describe_model_internal × N (per model)
        → parse fields[*].sourceColumn → build {db_col: qm_field} per-model reverse map

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

        Used by auto_discover_model_mapping() to match Odoo models
        (via env[model]._table) to QM models (via factTable).

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
        Two-phase metadata loading:

        Phase 1 (Discovery): call dataset.get_metadata once to discover all
            QM model names and their factTable mappings.
        Phase 2 (Detail): call dataset.describe_model_internal per model to
            build per-model column maps (sourceColumn → qm_field).

        Falls back to MODEL_MAPPING for the QM model list if Phase 1 fails,
        and to stale cache if Phase 2 fails entirely.
        """
        # ── Phase 1: Discovery ──
        qm_models, new_table_to_model = self._discover_models()

        if not qm_models:
            # Fallback: get QM model list from static MODEL_MAPPING
            try:
                from .tool_registry import MODEL_MAPPING
                qm_models = list(set(MODEL_MAPPING.values()))
            except Exception:
                qm_models = []

        if not qm_models:
            _logger.warning("No QM models discovered, skipping metadata load")
            return

        # ── Phase 2: Per-model column maps ──
        _logger.info("Loading field metadata from Foggy for %d models...", len(qm_models))

        new_column_maps = {}

        for qm_model in qm_models:
            try:
                response = self._client.call_tools_call(
                    'dataset.describe_model_internal',
                    {'model': qm_model, 'format': 'json'}
                )
                data = self._extract_metadata(response)
                if data is None:
                    _logger.warning("No metadata returned for model %s", qm_model)
                    continue

                column_map, fact_table = self._parse_model_metadata(qm_model, data)
                new_column_maps[qm_model] = column_map

                # Supplement table_to_model if Phase 1 missed this model
                if fact_table:
                    bare = self._strip_schema(fact_table)
                    if bare not in new_table_to_model:
                        new_table_to_model[bare] = qm_model

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

    def _discover_models(self):
        """
        Phase 1: Call dataset.get_metadata to discover all QM models and factTables.

        This breaks the circular dependency on MODEL_MAPPING — the QM model list
        comes directly from the Foggy server.

        Returns:
            tuple: (qm_models, table_to_model)
                - qm_models: list of QM model names
                - table_to_model: {bare_table_name: qm_model}
        """
        try:
            response = self._client.call_tools_call(
                'dataset.get_metadata', {'format': 'json'}
            )
            data = self._extract_metadata(response)
            if not data:
                _logger.debug("get_metadata returned no data, falling back to MODEL_MAPPING")
                return [], {}

            models_section = data.get('models', {})
            qm_models = []
            table_to_model = {}
            for qm_name, info in models_section.items():
                qm_models.append(qm_name)
                fact_table = info.get('factTable') if isinstance(info, dict) else None
                if fact_table:
                    bare = self._strip_schema(fact_table)
                    table_to_model[bare] = qm_name

            _logger.info("Discovery: %d QM models, %d factTable mappings",
                         len(qm_models), len(table_to_model))
            return qm_models, table_to_model

        except Exception as e:
            _logger.warning("Model discovery via get_metadata failed: %s", e)
            return [], {}

    @staticmethod
    def _strip_schema(table_name):
        """Strip schema prefix from a table name.

        MySQL tables can be schema-qualified (e.g., 'other_db.sale_order').
        Odoo's env[model]._table returns bare names ('sale_order').
        Strip the schema part for matching.
        """
        return table_name.rsplit('.', 1)[-1] if '.' in table_name else table_name

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
