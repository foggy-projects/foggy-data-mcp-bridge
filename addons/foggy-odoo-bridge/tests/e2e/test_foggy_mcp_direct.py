"""
E2E tests: Direct Foggy MCP Server queries.

Verifies that Foggy MCP Server can:
1. Load Odoo TM/QM models from the external bundle
2. Execute DSL queries against the Odoo PostgreSQL database
3. Handle closure table hierarchy operators
4. Support DISTINCT queries
5. Support withSubtotals aggregation
"""
import json
import pytest
from .conftest import call_foggy_tool, query_model


# ═══════════════════════════════════════════════════════════════
#  Health & Tool Discovery
# ═══════════════════════════════════════════════════════════════

class TestFoggyHealth:
    """Verify Foggy MCP Server is running and healthy."""

    def test_actuator_health(self, foggy_session, foggy_url):
        r = foggy_session.get(f'{foggy_url}/actuator/health')
        assert r.status_code == 200
        data = r.json()
        assert data['status'] == 'UP'

    def test_tools_list_contains_query_model(self, foggy_session, foggy_url):
        """MCP tools/list should include dataset.query_model."""
        payload = {
            'jsonrpc': '2.0', 'id': 1,
            'method': 'tools/list', 'params': {}
        }
        r = foggy_session.post(f'{foggy_url}/mcp/admin/rpc', json=payload)
        assert r.status_code == 200
        result = r.json().get('result', {})
        tools = result.get('tools', [])
        tool_names = [t['name'] for t in tools]
        assert 'dataset.query_model' in tool_names, \
            f'dataset.query_model not in tools: {tool_names}'


# ═══════════════════════════════════════════════════════════════
#  Basic Model Queries (all 8 models)
# ═══════════════════════════════════════════════════════════════

MODELS = [
    ('OdooSaleOrderQueryModel',     ['name', 'amountTotal']),
    ('OdooSaleOrderLineQueryModel', ['name', 'priceSubtotal']),
    ('OdooPurchaseOrderQueryModel', ['name', 'amountTotal']),
    ('OdooAccountMoveQueryModel',   ['name', 'amountTotalSigned']),
    ('OdooStockPickingQueryModel',  ['name', 'state']),
    ('OdooHrEmployeeQueryModel',    ['name', 'jobTitle']),
    ('OdooResPartnerQueryModel',    ['completeName', 'email']),
    ('OdooResCompanyQueryModel',    ['name', 'email']),
]


class TestBasicModelQueries:
    """Every Odoo model should return data with basic columns."""

    @pytest.mark.parametrize('model,columns', MODELS,
                             ids=[m[0] for m in MODELS])
    def test_model_returns_data(self, foggy_session, foggy_url, model, columns):
        data, err = query_model(foggy_session, foggy_url, model, {
            'columns': columns, 'limit': 5,
        })
        assert data is not None, f'{model} failed: {err}'
        assert data['pagination']['returned'] > 0, f'{model} returned 0 rows'


# ═══════════════════════════════════════════════════════════════
#  Dimension Caption Queries (VARCHAR + JSONB)
# ═══════════════════════════════════════════════════════════════

CAPTION_TESTS = [
    # VARCHAR captions
    ('OdooSaleOrderQueryModel',     'partner$caption',      'Partner'),
    ('OdooSaleOrderQueryModel',     'company$caption',      'Company'),
    ('OdooHrEmployeeQueryModel',    'department$caption',   'Department'),
    ('OdooHrEmployeeQueryModel',    'workLocation$caption', 'WorkLocation'),
    ('OdooResCompanyQueryModel',    'currency$caption',     'Currency'),
    ('OdooStockPickingQueryModel',  'locationSrc$caption',  'SrcLocation'),
    # JSONB captions (Odoo 17 translatable fields)
    ('OdooHrEmployeeQueryModel',    'job$caption',          'Job-JSONB'),
    ('OdooResPartnerQueryModel',    'country$caption',      'Country-JSONB'),
    ('OdooSaleOrderQueryModel',     'salesTeam$caption',    'SalesTeam-JSONB'),
    ('OdooPurchaseOrderQueryModel', 'pickingType$caption',  'PickingType-JSONB'),
    ('OdooAccountMoveQueryModel',   'journal$caption',      'Journal-JSONB'),
    ('OdooStockPickingQueryModel',  'pickingType$caption',  'PickingType2-JSONB'),
    ('OdooSaleOrderLineQueryModel', 'uom$caption',          'UoM-JSONB'),
]


class TestDimensionCaptions:
    """All dimension $caption columns should return readable values."""

    @pytest.mark.parametrize('model,caption_col,desc', CAPTION_TESTS,
                             ids=[f'{t[0]}.{t[2]}' for t in CAPTION_TESTS])
    def test_caption_returns_value(self, foggy_session, foggy_url,
                                   model, caption_col, desc):
        data, err = query_model(foggy_session, foggy_url, model, {
            'columns': [caption_col], 'limit': 3,
        })
        assert data is not None, f'{caption_col} failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0, f'{caption_col}: no items returned'
        # Caption should be a non-empty string (not raw JSONB)
        val = items[0].get(caption_col)
        assert val is not None, f'{caption_col}: first row value is null'
        if isinstance(val, str):
            assert not val.startswith('{'), \
                f'{caption_col} returned raw JSONB: {val[:80]}'


# ═══════════════════════════════════════════════════════════════
#  Closure Table Hierarchy Queries
# ═══════════════════════════════════════════════════════════════

class TestClosureTableQueries:
    """Test hierarchy queries using closure table operators."""

    def test_department_selfAndDescendantsOf(self, foggy_session, foggy_url):
        """Employees in department 2 (Management) and sub-departments."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooHrEmployeeQueryModel', {
                'columns': ['name', 'department$caption', 'job$caption'],
                'slice': [{'field': 'department$id',
                           'op': 'selfAndDescendantsOf', 'value': 2}],
                'limit': 50,
            })
        assert data is not None, f'Dept hierarchy failed: {err}'
        assert data['pagination']['returned'] > 0

    def test_company_selfAndDescendantsOf_via_sale_order(self, foggy_session, foggy_url):
        """Sale orders for company 1 and all descendants."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$caption', 'amountTotal'],
                'slice': [{'field': 'company$id',
                           'op': 'selfAndDescendantsOf', 'value': 1}],
                'limit': 5,
            })
        assert data is not None, f'Company hierarchy failed: {err}'
        assert data['pagination']['returned'] > 0

    def test_groupby_with_hierarchy(self, foggy_session, foggy_url):
        """Aggregate employees grouped by department, filtered by hierarchy."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooHrEmployeeQueryModel', {
                'columns': ['department$caption', 'count(name) as cnt'],
                'slice': [{'field': 'department$id',
                           'op': 'selfAndDescendantsOf', 'value': 2}],
            })
        assert data is not None, f'GroupBy hierarchy failed: {err}'
        assert data['pagination']['returned'] > 0

    def test_self_referencing_company_hierarchy(self, foggy_session, foggy_url):
        """ResCompany self-referential parent$caption (fact = dimension).

        NOTE: Odoo demo data may have all companies with parent_id=NULL,
        in which case this returns 0 rows. We only assert no error occurs.
        """
        data, err = query_model(foggy_session, foggy_url,
            'OdooResCompanyQueryModel', {
                'columns': ['name', 'parent$caption', 'currency$caption'],
                'slice': [{'field': 'parent$id',
                           'op': 'selfAndDescendantsOf', 'value': 1}],
            })
        assert data is not None, f'Self-ref hierarchy failed: {err}'
        # May return 0 rows if no company has a parent — that's OK


# ═══════════════════════════════════════════════════════════════
#  Aggregation Queries
# ═══════════════════════════════════════════════════════════════

class TestAggregationQueries:
    """Test groupBy / inline aggregate expressions."""

    def test_sale_order_sum_by_company(self, foggy_session, foggy_url):
        data, err = query_model(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['company$caption', 'sum(amountTotal) as totalSales'],
                'orderBy': [{'field': 'totalSales', 'dir': 'desc'}],
            })
        assert data is not None, f'Sum by company failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0
        first = items[0]
        assert 'totalSales' in first
        assert isinstance(first['totalSales'], (int, float))

    def test_employee_count_by_department(self, foggy_session, foggy_url):
        data, err = query_model(foggy_session, foggy_url,
            'OdooHrEmployeeQueryModel', {
                'columns': ['department$caption', 'count(name) as headCount'],
                'orderBy': [{'field': 'headCount', 'dir': 'desc'}],
            })
        assert data is not None, f'Count by dept failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0
        assert items[0]['headCount'] > 0


# ═══════════════════════════════════════════════════════════════
#  DISTINCT Queries
# ═══════════════════════════════════════════════════════════════

class TestDistinctQueries:
    """Test DISTINCT (SELECT DISTINCT) support."""

    def test_distinct_partner_types(self, foggy_session, foggy_url):
        """List all unique partner types (person/company)."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooResPartnerQueryModel', {
                'columns': ['type'],
                'distinct': True,
            })
        assert data is not None, f'Distinct query failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0
        values = [r.get('type') for r in items]
        assert len(values) == len(set(values)), \
            f'DISTINCT returned duplicates: {values}'

    def test_distinct_sale_order_states(self, foggy_session, foggy_url):
        """List all unique sale order states."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['state'],
                'distinct': True,
            })
        assert data is not None, f'Distinct states failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0

    def test_distinct_employee_departments(self, foggy_session, foggy_url):
        """List all unique departments with employees."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooHrEmployeeQueryModel', {
                'columns': ['department$caption'],
                'distinct': True,
            })
        assert data is not None, f'Distinct depts failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0


# ═══════════════════════════════════════════════════════════════
#  withSubtotals Queries
# ═══════════════════════════════════════════════════════════════

class TestWithSubtotals:
    """Test withSubtotals (grand total + group subtotal rows)."""

    def test_subtotals_single_dimension(self, foggy_session, foggy_url):
        """Single dimension groupBy → should add grandTotal row."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['company$caption', 'sum(amountTotal) as total'],
                'withSubtotals': True,
            })
        assert data is not None, f'Subtotals single dim failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0
        row_types = [r.get('_rowType') for r in items]
        assert 'grandTotal' in row_types, \
            f'No grandTotal row found. rowTypes: {row_types}'
        data_rows = [r for r in items if r.get('_rowType') == 'data']
        assert len(data_rows) > 0

    def test_subtotals_multi_dimension(self, foggy_session, foggy_url):
        """Two dimensions → should add subtotal per first dimension + grandTotal."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['company$caption', 'state',
                            'sum(amountTotal) as total'],
                'withSubtotals': True,
            })
        assert data is not None, f'Subtotals multi dim failed: {err}'
        items = data.get('items', [])
        row_types = [r.get('_rowType') for r in items]
        assert 'grandTotal' in row_types, f'No grandTotal. rowTypes: {row_types}'
        assert 'subtotal' in row_types, f'No subtotal rows. rowTypes: {row_types}'


# ═══════════════════════════════════════════════════════════════
#  Slice / Filter Queries
# ═══════════════════════════════════════════════════════════════

class TestSliceQueries:
    """Test various filter operators."""

    def test_filter_by_state(self, foggy_session, foggy_url):
        """Filter sale orders by state = 'sale'."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['name', 'state', 'amountTotal'],
                'slice': [{'field': 'state', 'op': '=', 'value': 'sale'}],
                'limit': 10,
            })
        assert data is not None, f'State filter failed: {err}'
        items = data.get('items', [])
        for row in items:
            assert row.get('state') == 'sale', \
                f'Row state={row.get("state")}, expected sale'

    def test_filter_like(self, foggy_session, foggy_url):
        """Filter partners by name containing a common substring."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooResPartnerQueryModel', {
                'columns': ['completeName', 'email'],
                'slice': [{'field': 'completeName', 'op': 'like', 'value': 'a'}],
                'limit': 5,
            })
        assert data is not None, f'Like filter failed: {err}'

    def test_filter_is_not_null(self, foggy_session, foggy_url):
        """Filter employees where email is not null."""
        data, err = query_model(foggy_session, foggy_url,
            'OdooHrEmployeeQueryModel', {
                'columns': ['name', 'workEmail'],
                'slice': [{'field': 'workEmail', 'op': 'is not null'}],
                'limit': 5,
            })
        assert data is not None, f'is not null filter failed: {err}'
        items = data.get('items', [])
        for row in items:
            assert row.get('workEmail') is not None
