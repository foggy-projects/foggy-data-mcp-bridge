# -*- coding: utf-8 -*-
"""
Unit tests for FieldMappingRegistry.

Tests the metadata parsing, caching, and reverse map construction
without requiring Foggy MCP Server or Odoo runtime.

Run with:  python -m pytest tests/test_field_mapping_registry.py -v
"""
import sys
import os
import types
import time
import pytest

# ── Stub out Odoo imports (needed for tool_registry import chain) ──

_odoo = types.ModuleType('odoo')
_odoo_osv = types.ModuleType('odoo.osv')
_odoo_osv_expression = types.ModuleType('odoo.osv.expression')
_odoo_osv_expression.normalize_domain = lambda d: d
_odoo_osv.expression = _odoo_osv_expression
_odoo_tools = types.ModuleType('odoo.tools')
_odoo_tools_safe_eval = types.ModuleType('odoo.tools.safe_eval')
_odoo_tools_safe_eval.safe_eval = lambda expr, ctx=None: eval(expr, ctx or {})
_odoo_tools_safe_eval.time = __import__('time')
_odoo_tools_safe_eval.datetime = __import__('datetime')
_odoo_tools.safe_eval = _odoo_tools_safe_eval

sys.modules['odoo'] = _odoo
sys.modules['odoo.osv'] = _odoo_osv
sys.modules['odoo.osv.expression'] = _odoo_osv_expression
sys.modules['odoo.tools'] = _odoo_tools
sys.modules['odoo.tools.safe_eval'] = _odoo_tools_safe_eval

# Stub the package hierarchy
_bridge_dir = os.path.join(os.path.dirname(__file__), '..', 'foggy_mcp', 'services')
_fake_services = types.ModuleType('foggy_mcp.services')
_fake_foggy_mcp = types.ModuleType('foggy_mcp')
_fake_foggy_mcp.services = _fake_services
sys.modules['foggy_mcp'] = _fake_foggy_mcp
sys.modules['foggy_mcp.services'] = _fake_services

# Import tool_registry to provide MODEL_MAPPING
import importlib.util
_tr_spec = importlib.util.spec_from_file_location(
    'foggy_mcp.services.tool_registry',
    os.path.join(_bridge_dir, 'tool_registry.py'),
)
_tr_mod = importlib.util.module_from_spec(_tr_spec)
sys.modules['foggy_mcp.services.tool_registry'] = _tr_mod
_fake_services.tool_registry = _tr_mod
_tr_spec.loader.exec_module(_tr_mod)

# Import foggy_client stub
_fc_spec = importlib.util.spec_from_file_location(
    'foggy_mcp.services.foggy_client',
    os.path.join(_bridge_dir, 'foggy_client.py'),
)
_fc_mod = importlib.util.module_from_spec(_fc_spec)
sys.modules['foggy_mcp.services.foggy_client'] = _fc_mod
_fake_services.foggy_client = _fc_mod
# Don't exec — we'll mock the client

# Import FieldMappingRegistry
_fmr_spec = importlib.util.spec_from_file_location(
    'foggy_mcp.services.field_mapping_registry',
    os.path.join(_bridge_dir, 'field_mapping_registry.py'),
)
_fmr_mod = importlib.util.module_from_spec(_fmr_spec)
sys.modules['foggy_mcp.services.field_mapping_registry'] = _fmr_mod
_fake_services.field_mapping_registry = _fmr_mod
_fmr_spec.loader.exec_module(_fmr_mod)

FieldMappingRegistry = _fmr_mod.FieldMappingRegistry


# ─── Mock Foggy Client ────────────────────────────────────────────

class MockFoggyClient:
    """Mock FoggyClient that returns predefined metadata responses.

    Supports two-phase loading:
    - Phase 1: dataset.get_metadata → returns discovery_response
    - Phase 2: dataset.describe_model_internal → returns per-model responses
    """

    def __init__(self, responses=None, discovery_response=None):
        """
        Args:
            responses: dict of {model_name: response_data} for describe_model_internal
            discovery_response: response for dataset.get_metadata (Phase 1)
        """
        self._responses = responses or {}
        self._discovery_response = discovery_response
        self.call_count = 0
        self.calls = []  # [(tool_name, arguments), ...]

    def call_tools_call(self, tool_name, arguments, trace_id=None):
        self.call_count += 1
        self.calls.append((tool_name, dict(arguments)))

        if tool_name == 'dataset.get_metadata':
            if self._discovery_response is not None:
                return self._discovery_response
            raise ValueError("No discovery response configured")

        # dataset.describe_model_internal
        model = arguments.get('model', '')
        data = self._responses.get(model)
        if data is None:
            raise ValueError(f"No mock response for model: {model}")
        return data


def _make_mcp_response(metadata):
    """Wrap metadata dict in MCP JSON-RPC response structure."""
    import json
    return {
        'result': {
            'content': [
                {
                    'type': 'text',
                    'text': json.dumps(metadata)
                }
            ]
        }
    }


# ═══════════════════════════════════════════════════════════════════
# _parse_model_metadata tests
# ═══════════════════════════════════════════════════════════════════

class TestParseModelMetadata:
    """Test the static metadata parsing method."""

    def test_basic_dimension_mapping(self):
        """sourceColumn in dimension $id field → reverse map entry."""
        data = {
            'fields': {
                'salesperson$id': {
                    'fieldName': 'salesperson$id',
                    'sourceColumn': 'user_id',
                    'type': 'INTEGER',
                },
                'salesperson$caption': {
                    'fieldName': 'salesperson$caption',
                    'type': 'TEXT',
                    # No sourceColumn for caption fields
                },
            },
            'models': {
                'OdooSaleOrderQueryModel': {
                    'name': '销售订单',
                    'factTable': 'sale_order',
                }
            }
        }
        column_map, fact_table = FieldMappingRegistry._parse_model_metadata(
            'OdooSaleOrderQueryModel', data)

        assert column_map == {'user_id': 'salesperson$id'}
        assert fact_table == 'sale_order'

    def test_multiple_fields(self):
        """Multiple fields with sourceColumn all get mapped."""
        data = {
            'fields': {
                'salesperson$id': {'sourceColumn': 'user_id'},
                'company$id': {'sourceColumn': 'company_id'},
                'partner$id': {'sourceColumn': 'partner_id'},
                'state': {'sourceColumn': 'state'},
                'amountTotal': {'sourceColumn': 'amount_total'},
            },
            'models': {
                'OdooSaleOrderQueryModel': {
                    'factTable': 'sale_order',
                }
            }
        }
        column_map, fact_table = FieldMappingRegistry._parse_model_metadata(
            'OdooSaleOrderQueryModel', data)

        assert column_map == {
            'user_id': 'salesperson$id',
            'company_id': 'company$id',
            'partner_id': 'partner$id',
            'state': 'state',
            'amount_total': 'amountTotal',
        }
        assert fact_table == 'sale_order'

    def test_no_source_column_skipped(self):
        """Fields without sourceColumn are skipped."""
        data = {
            'fields': {
                'salesperson$caption': {'type': 'TEXT'},
                'partner$caption': {'type': 'TEXT'},
            },
            'models': {}
        }
        column_map, fact_table = FieldMappingRegistry._parse_model_metadata(
            'TestModel', data)

        assert column_map == {}
        assert fact_table is None

    def test_empty_fields(self):
        """Empty fields dict → empty map."""
        data = {'fields': {}, 'models': {}}
        column_map, fact_table = FieldMappingRegistry._parse_model_metadata(
            'TestModel', data)

        assert column_map == {}
        assert fact_table is None

    def test_missing_fields_key(self):
        """No 'fields' key → empty map."""
        data = {'models': {}}
        column_map, fact_table = FieldMappingRegistry._parse_model_metadata(
            'TestModel', data)

        assert column_map == {}
        assert fact_table is None

    def test_model_not_in_models(self):
        """Model name not in models dict → factTable is None."""
        data = {
            'fields': {'x$id': {'sourceColumn': 'x_id'}},
            'models': {
                'DifferentModel': {'factTable': 'other_table'}
            }
        }
        column_map, fact_table = FieldMappingRegistry._parse_model_metadata(
            'TestModel', data)

        assert column_map == {'x_id': 'x$id'}
        assert fact_table is None

    def test_per_model_user_id_difference(self):
        """Different models map user_id to different QM fields.

        sale.order: user_id → salesperson$id
        hr.employee: user_id → user$id
        """
        sale_data = {
            'fields': {
                'salesperson$id': {'sourceColumn': 'user_id'},
            },
            'models': {'OdooSaleOrderQueryModel': {'factTable': 'sale_order'}}
        }
        hr_data = {
            'fields': {
                'user$id': {'sourceColumn': 'user_id'},
            },
            'models': {'OdooHrEmployeeQueryModel': {'factTable': 'hr_employee'}}
        }

        sale_map, _ = FieldMappingRegistry._parse_model_metadata(
            'OdooSaleOrderQueryModel', sale_data)
        hr_map, _ = FieldMappingRegistry._parse_model_metadata(
            'OdooHrEmployeeQueryModel', hr_data)

        assert sale_map['user_id'] == 'salesperson$id'
        assert hr_map['user_id'] == 'user$id'


# ═══════════════════════════════════════════════════════════════════
# _extract_metadata tests
# ═══════════════════════════════════════════════════════════════════

class TestExtractMetadata:
    """Test MCP response parsing."""

    def test_text_content_block(self):
        """Standard MCP response with text content block."""
        import json
        metadata = {'fields': {'x$id': {'sourceColumn': 'x_id'}}, 'models': {}}
        response = _make_mcp_response(metadata)
        result = FieldMappingRegistry._extract_metadata(response)
        assert result == metadata

    def test_none_response(self):
        """None response → None."""
        assert FieldMappingRegistry._extract_metadata(None) is None

    def test_empty_response(self):
        """Empty dict → None."""
        assert FieldMappingRegistry._extract_metadata({}) is None

    def test_no_content(self):
        """Response without content → None."""
        response = {'result': {}}
        assert FieldMappingRegistry._extract_metadata(response) is None

    def test_invalid_json_in_text(self):
        """Content text with invalid JSON → None."""
        response = {
            'result': {
                'content': [{'type': 'text', 'text': 'not json'}]
            }
        }
        assert FieldMappingRegistry._extract_metadata(response) is None


# ─── Shared mixin for tests that mutate MODEL_MAPPING ─────────────

class _ModelMappingGuard:
    """Mixin: saves/restores MODEL_MAPPING + QM_TO_ODOO_MODEL around each test."""

    @staticmethod
    def _get_model_mapping():
        return sys.modules['foggy_mcp.services.tool_registry'].MODEL_MAPPING

    @staticmethod
    def _get_qm_to_odoo():
        return sys.modules['foggy_mcp.services.tool_registry'].QM_TO_ODOO_MODEL

    def setup_method(self):
        self._saved_mapping = dict(self._get_model_mapping())
        self._saved_reverse = dict(self._get_qm_to_odoo())

    def teardown_method(self):
        m = self._get_model_mapping(); m.clear(); m.update(self._saved_mapping)
        r = self._get_qm_to_odoo(); r.clear(); r.update(self._saved_reverse)


# ═══════════════════════════════════════════════════════════════════
# Full integration (with mock client)
# ═══════════════════════════════════════════════════════════════════

class TestRegistryIntegration(_ModelMappingGuard):
    """Test the full registry with mock client."""

    def _make_registry(self, model_responses, cache_ttl=300, discovery_response=None):
        """Create a registry with mock responses for known models.

        Builds Phase 1 discovery response automatically from model_responses
        (extracting the 'models' section from each), unless discovery_response
        is explicitly provided.
        """
        # Build per-model describe responses (Phase 2)
        wrapped = {}
        for model_name, metadata in model_responses.items():
            wrapped[model_name] = _make_mcp_response(metadata)

        # Build discovery response (Phase 1) from merged models sections
        if discovery_response is None and model_responses:
            merged_models = {}
            for model_name, metadata in model_responses.items():
                models_section = metadata.get('models', {})
                merged_models.update(models_section)
            discovery_data = {'fields': {}, 'models': merged_models}
            discovery_response = _make_mcp_response(discovery_data)

        client = MockFoggyClient(
            responses=wrapped,
            discovery_response=discovery_response,
        )
        return FieldMappingRegistry(client, cache_ttl=cache_ttl), client

    def test_load_single_model(self):
        """Load metadata for a single model and query column map."""
        metadata = {
            'fields': {
                'salesperson$id': {'sourceColumn': 'user_id'},
                'company$id': {'sourceColumn': 'company_id'},
            },
            'models': {
                'OdooSaleOrderQueryModel': {'factTable': 'sale_order'}
            }
        }
        registry, client = self._make_registry({
            'OdooSaleOrderQueryModel': metadata,
        })

        column_map = registry.get_column_map('OdooSaleOrderQueryModel')
        assert column_map == {
            'user_id': 'salesperson$id',
            'company_id': 'company$id',
        }

    def test_table_to_model_map(self):
        """factTable → QM model reverse mapping."""
        metadata = {
            'fields': {},
            'models': {
                'OdooSaleOrderQueryModel': {'factTable': 'sale_order'}
            }
        }
        registry, _ = self._make_registry({
            'OdooSaleOrderQueryModel': metadata,
        })

        table_map = registry.get_table_to_model_map()
        assert table_map.get('sale_order') == 'OdooSaleOrderQueryModel'

    def test_unknown_model_returns_empty(self):
        """Querying an unknown model returns empty dict."""
        registry, _ = self._make_registry({})

        column_map = registry.get_column_map('NonExistentModel')
        assert column_map == {}

    def test_cache_reuse(self):
        """Second call uses cache (no additional API calls)."""
        metadata = {
            'fields': {'x$id': {'sourceColumn': 'x_id'}},
            'models': {'OdooSaleOrderQueryModel': {'factTable': 'test'}}
        }
        registry, client = self._make_registry({
            'OdooSaleOrderQueryModel': metadata,
        })

        registry.get_column_map('OdooSaleOrderQueryModel')
        call_count_1 = client.call_count

        registry.get_column_map('OdooSaleOrderQueryModel')
        assert client.call_count == call_count_1, "Cache should prevent additional calls"

    def test_cache_ttl_expires(self):
        """After TTL, cache refreshes (new API calls)."""
        metadata = {
            'fields': {'x$id': {'sourceColumn': 'x_id'}},
            'models': {'OdooSaleOrderQueryModel': {'factTable': 'test'}}
        }
        registry, client = self._make_registry({
            'OdooSaleOrderQueryModel': metadata,
        }, cache_ttl=0)  # 0 TTL = always expired

        registry.get_column_map('OdooSaleOrderQueryModel')
        call_count_1 = client.call_count

        # Force expiry
        registry._cache_timestamp = 0

        registry.get_column_map('OdooSaleOrderQueryModel')
        assert client.call_count > call_count_1, "Expired cache should trigger reload"

    def test_invalidate_cache(self):
        """invalidate_cache() forces a refresh."""
        metadata = {
            'fields': {'x$id': {'sourceColumn': 'x_id'}},
            'models': {'OdooSaleOrderQueryModel': {'factTable': 'test'}}
        }
        registry, client = self._make_registry({
            'OdooSaleOrderQueryModel': metadata,
        })

        registry.get_column_map('OdooSaleOrderQueryModel')
        call_count_1 = client.call_count

        registry.invalidate_cache()
        registry.get_column_map('OdooSaleOrderQueryModel')
        assert client.call_count > call_count_1

    def test_stale_cache_fallback(self):
        """If reload fails, stale cache is preserved."""
        metadata = {
            'fields': {'x$id': {'sourceColumn': 'x_id'}},
            'models': {'OdooSaleOrderQueryModel': {'factTable': 'test'}}
        }
        registry, client = self._make_registry({
            'OdooSaleOrderQueryModel': metadata,
        })

        # Initial load succeeds
        column_map = registry.get_column_map('OdooSaleOrderQueryModel')
        assert column_map == {'x_id': 'x$id'}

        # Now make client fail
        client._responses = {}
        registry._cache_timestamp = 0  # Force expiry

        # Should fall back to stale cache
        column_map = registry.get_column_map('OdooSaleOrderQueryModel')
        assert column_map == {'x_id': 'x$id'}

    def test_all_models_fail_first_load(self):
        """If all models fail on first load, returns empty maps."""
        client = MockFoggyClient(responses={})  # No responses → all fail
        registry = FieldMappingRegistry(client, cache_ttl=300)

        column_map = registry.get_column_map('OdooSaleOrderQueryModel')
        assert column_map == {}


# ═══════════════════════════════════════════════════════════════════
# Phase 1 Discovery (_discover_models) tests
# ═══════════════════════════════════════════════════════════════════

class TestDiscoverModels:
    """Test the Phase 1 discovery via dataset.get_metadata."""

    def test_discover_models_basic(self):
        """Discovery extracts QM model names and factTables."""
        discovery_data = {
            'fields': {},
            'models': {
                'OdooSaleOrderQueryModel': {'factTable': 'sale_order', 'name': '销售订单'},
                'OdooResPartnerQueryModel': {'factTable': 'res_partner', 'name': '合作伙伴'},
            }
        }
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
        )
        registry = FieldMappingRegistry(client)

        qm_models, table_to_model = registry._discover_models()

        assert set(qm_models) == {'OdooSaleOrderQueryModel', 'OdooResPartnerQueryModel'}
        assert table_to_model == {
            'sale_order': 'OdooSaleOrderQueryModel',
            'res_partner': 'OdooResPartnerQueryModel',
        }

    def test_discover_models_with_schema_prefix(self):
        """MySQL schema-qualified factTable is stripped to bare name."""
        discovery_data = {
            'fields': {},
            'models': {
                'Model1': {'factTable': 'other_db.sale_order'},
                'Model2': {'factTable': 'sale_order_line'},  # no schema
            }
        }
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
        )
        registry = FieldMappingRegistry(client)

        qm_models, table_to_model = registry._discover_models()

        assert table_to_model == {
            'sale_order': 'Model1',
            'sale_order_line': 'Model2',
        }

    def test_discover_models_no_fact_table(self):
        """Models without factTable are discovered but not in table_to_model."""
        discovery_data = {
            'fields': {},
            'models': {
                'ModelA': {'factTable': 'table_a'},
                'ModelB': {'name': 'No fact table'},  # no factTable key
            }
        }
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
        )
        registry = FieldMappingRegistry(client)

        qm_models, table_to_model = registry._discover_models()

        assert set(qm_models) == {'ModelA', 'ModelB'}
        assert table_to_model == {'table_a': 'ModelA'}

    def test_discover_models_failure_returns_empty(self):
        """If get_metadata fails, returns empty lists."""
        client = MockFoggyClient()  # No discovery_response → will raise
        registry = FieldMappingRegistry(client)

        qm_models, table_to_model = registry._discover_models()

        assert qm_models == []
        assert table_to_model == {}

    def test_discover_models_empty_response(self):
        """Empty models section → empty results."""
        discovery_data = {'fields': {}, 'models': {}}
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
        )
        registry = FieldMappingRegistry(client)

        qm_models, table_to_model = registry._discover_models()

        assert qm_models == []
        assert table_to_model == {}


# ═══════════════════════════════════════════════════════════════════
# Two-phase loading integration tests
# ═══════════════════════════════════════════════════════════════════

class TestTwoPhaseLoading:
    """Test that Phase 1 (discovery) + Phase 2 (detail) work together."""

    def test_discovery_drives_detail_loading(self):
        """Phase 1 discovers models, Phase 2 loads their column maps."""
        discovery_data = {
            'fields': {},
            'models': {
                'OdooSaleOrderQueryModel': {'factTable': 'sale_order'},
            }
        }
        detail_metadata = {
            'fields': {
                'salesperson$id': {'sourceColumn': 'user_id'},
                'company$id': {'sourceColumn': 'company_id'},
            },
            'models': {'OdooSaleOrderQueryModel': {'factTable': 'sale_order'}}
        }
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
            responses={'OdooSaleOrderQueryModel': _make_mcp_response(detail_metadata)},
        )
        registry = FieldMappingRegistry(client)

        column_map = registry.get_column_map('OdooSaleOrderQueryModel')
        assert column_map == {
            'user_id': 'salesperson$id',
            'company_id': 'company$id',
        }

        table_map = registry.get_table_to_model_map()
        assert table_map['sale_order'] == 'OdooSaleOrderQueryModel'

    def test_discovery_failure_falls_back_to_model_mapping(self):
        """If Phase 1 fails, falls back to MODEL_MAPPING for QM model list."""
        # Patch MODEL_MAPPING with a test model
        mapping = sys.modules['foggy_mcp.services.tool_registry'].MODEL_MAPPING
        original = dict(mapping)
        try:
            mapping.clear()
            mapping['test.model'] = 'TestQmModel'

            detail_metadata = {
                'fields': {'x$id': {'sourceColumn': 'x_id'}},
                'models': {'TestQmModel': {'factTable': 'test_table'}}
            }
            client = MockFoggyClient(
                # No discovery_response → Phase 1 fails
                responses={'TestQmModel': _make_mcp_response(detail_metadata)},
            )
            registry = FieldMappingRegistry(client)

            column_map = registry.get_column_map('TestQmModel')
            assert column_map == {'x_id': 'x$id'}
        finally:
            mapping.clear()
            mapping.update(original)

    def test_phase1_call_then_phase2_calls(self):
        """Verify the call sequence: get_metadata → describe_model_internal × N."""
        discovery_data = {
            'fields': {},
            'models': {
                'ModelA': {'factTable': 'table_a'},
                'ModelB': {'factTable': 'table_b'},
            }
        }
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
            responses={
                'ModelA': _make_mcp_response({
                    'fields': {'a$id': {'sourceColumn': 'a_id'}},
                    'models': {'ModelA': {'factTable': 'table_a'}},
                }),
                'ModelB': _make_mcp_response({
                    'fields': {'b$id': {'sourceColumn': 'b_id'}},
                    'models': {'ModelB': {'factTable': 'table_b'}},
                }),
            },
        )
        registry = FieldMappingRegistry(client)
        registry.get_column_map('ModelA')

        # First call should be get_metadata (Phase 1)
        assert client.calls[0][0] == 'dataset.get_metadata'
        # Subsequent calls should be describe_model_internal (Phase 2)
        phase2_calls = [c for c in client.calls if c[0] == 'dataset.describe_model_internal']
        assert len(phase2_calls) == 2

    def test_schema_prefix_stripped_in_table_to_model(self):
        """Schema-qualified factTables are stripped for table_to_model keys."""
        discovery_data = {
            'fields': {},
            'models': {
                'Model1': {'factTable': 'mydb.orders'},
            }
        }
        client = MockFoggyClient(
            discovery_response=_make_mcp_response(discovery_data),
            responses={
                'Model1': _make_mcp_response({
                    'fields': {},
                    'models': {'Model1': {'factTable': 'mydb.orders'}},
                }),
            },
        )
        registry = FieldMappingRegistry(client)

        table_map = registry.get_table_to_model_map()
        assert 'orders' in table_map
        assert 'mydb.orders' not in table_map


# ═══════════════════════════════════════════════════════════════════
# _strip_schema tests
# ═══════════════════════════════════════════════════════════════════

class TestStripSchema:
    """Test the schema prefix stripping utility."""

    def test_no_schema(self):
        assert FieldMappingRegistry._strip_schema('sale_order') == 'sale_order'

    def test_with_schema(self):
        assert FieldMappingRegistry._strip_schema('other_db.sale_order') == 'sale_order'

    def test_multiple_dots(self):
        """Only the last part is kept (schema.catalog.table edge case)."""
        assert FieldMappingRegistry._strip_schema('catalog.schema.table') == 'table'

    def test_empty_string(self):
        assert FieldMappingRegistry._strip_schema('') == ''


# ═══════════════════════════════════════════════════════════════════
# auto_discover_model_mapping tests
# ═══════════════════════════════════════════════════════════════════

auto_discover_model_mapping = sys.modules['foggy_mcp.services.tool_registry'].auto_discover_model_mapping


class TestAutoDiscoverModelMapping(_ModelMappingGuard):
    """Test auto-discovery of MODEL_MAPPING from Foggy metadata + Odoo ORM."""

    def _make_mock_env(self, installed_models):
        """Create a mock Odoo env with model name → _table mappings.

        Args:
            installed_models: dict of {odoo_model_name: table_name}
        """
        class _Proxy:
            def __init__(self, table): self._table = table

        class _Env:
            def __contains__(self, key): return key in installed_models
            def __getitem__(self, key):
                if key in installed_models:
                    return _Proxy(installed_models[key])
                raise KeyError(key)

        return _Env()

    def _make_mock_registry(self, table_to_model):
        """Create a mock FieldMappingRegistry with a preset table_to_model map."""
        class MockRegistry:
            def get_table_to_model_map(self):
                return dict(table_to_model)
        return MockRegistry()

    def test_basic_discovery(self):
        """Matches Odoo models to QM models via table name."""
        env = self._make_mock_env({
            'sale.order': 'sale_order',
            'res.partner': 'res_partner',
            'ir.model': 'ir_model',  # no QM match
        })
        registry = self._make_mock_registry({
            'sale_order': 'OdooSaleOrderQueryModel',
            'res_partner': 'OdooResPartnerQueryModel',
        })

        result = auto_discover_model_mapping(env, registry)

        assert result is True
        mapping = self._get_model_mapping()
        assert mapping == {
            'sale.order': 'OdooSaleOrderQueryModel',
            'res.partner': 'OdooResPartnerQueryModel',
        }
        reverse = self._get_qm_to_odoo()
        assert reverse == {
            'OdooSaleOrderQueryModel': 'sale.order',
            'OdooResPartnerQueryModel': 'res.partner',
        }

    def test_empty_table_to_model_keeps_static(self):
        """If registry returns empty map, static MODEL_MAPPING is preserved."""
        original = dict(self._get_model_mapping())
        env = self._make_mock_env({'sale.order': 'sale_order'})
        registry = self._make_mock_registry({})

        result = auto_discover_model_mapping(env, registry)

        assert result is False
        assert self._get_model_mapping() == original

    def test_no_matches_keeps_static(self):
        """If no Odoo models match, static MODEL_MAPPING is preserved."""
        original = dict(self._get_model_mapping())
        env = self._make_mock_env({
            'my.custom.model': 'my_custom_table',  # not in Foggy
        })
        registry = self._make_mock_registry({
            'sale_order': 'OdooSaleOrderQueryModel',
        })

        result = auto_discover_model_mapping(env, registry)

        assert result is False
        assert self._get_model_mapping() == original

    def test_replaces_static_mapping(self):
        """Discovery replaces (not merges with) static MODEL_MAPPING."""
        # Set a static mapping that won't match
        mapping = self._get_model_mapping()
        mapping.clear()
        mapping['old.model'] = 'OldQmModel'

        env = self._make_mock_env({'sale.order': 'sale_order'})
        registry = self._make_mock_registry({
            'sale_order': 'OdooSaleOrderQueryModel',
        })

        auto_discover_model_mapping(env, registry)

        assert self._get_model_mapping() == {
            'sale.order': 'OdooSaleOrderQueryModel',
        }
        # Old mapping should be gone
        assert 'old.model' not in self._get_model_mapping()
