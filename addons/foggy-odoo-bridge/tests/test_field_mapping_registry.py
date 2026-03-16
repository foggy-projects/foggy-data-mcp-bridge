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
    """Mock FoggyClient that returns predefined metadata responses."""

    def __init__(self, responses=None):
        """
        Args:
            responses: dict of {model_name: response_data}
        """
        self._responses = responses or {}
        self.call_count = 0

    def call_tools_call(self, tool_name, arguments, trace_id=None):
        self.call_count += 1
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


# ═══════════════════════════════════════════════════════════════════
# Full integration (with mock client)
# ═══════════════════════════════════════════════════════════════════

class TestRegistryIntegration:
    """Test the full registry with mock client."""

    def _get_model_mapping(self):
        """Get the current MODEL_MAPPING dict from sys.modules.

        Uses sys.modules to always get the live reference, even if
        test_permission_bridge.py replaced the module object.
        """
        return sys.modules['foggy_mcp.services.tool_registry'].MODEL_MAPPING

    def setup_method(self):
        """Save original MODEL_MAPPING before each test."""
        self._original_mapping = dict(self._get_model_mapping())

    def teardown_method(self):
        """Restore original MODEL_MAPPING after each test."""
        mapping = self._get_model_mapping()
        mapping.clear()
        mapping.update(self._original_mapping)

    def _make_registry(self, model_responses, cache_ttl=300):
        """Create a registry with mock responses for known models.

        Also patches MODEL_MAPPING so _load_all_models() knows which
        QM models to iterate over.
        """
        # Patch MODEL_MAPPING to contain exactly the test models
        mapping = self._get_model_mapping()
        mapping.clear()
        for qm_model_name in model_responses:
            # Use a synthetic Odoo model name; value = QM model name
            mapping[f'test.{qm_model_name}'] = qm_model_name

        wrapped = {}
        for model_name, metadata in model_responses.items():
            wrapped[model_name] = _make_mcp_response(metadata)
        client = MockFoggyClient(responses=wrapped)
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
