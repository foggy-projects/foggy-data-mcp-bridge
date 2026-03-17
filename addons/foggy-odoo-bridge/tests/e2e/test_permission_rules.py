"""
E2E tests: ir.rule permission filtering across models and users.

Verifies that the Odoo MCP Gateway correctly applies ir.rule (row-level)
permission filtering for different user roles.

Test matrix (models × users):
  - purchase.order:  demo=11, sales_mgr=11, admin=11  (all company 1)
  - account.move:    demo=24, sales_mgr=24, admin=24  (tautology group rule)
  - stock.picking:   demo=25, sales_mgr=17, admin=25  (company isolation!)
  - hr.employee:     demo=20, sales_mgr=20, admin=20  (company + [False])

NOTE: res.partner is excluded from multi-user E2E tests because its global
ir.rule uses 'partner_share' (boolean field not in QM model). The domain
conversion produces a field reference that Foggy cannot resolve. This is
a known limitation documented in test_permission_bridge.py.

Environment variables:
  ODOO_MCP_URL       - Odoo MCP Gateway URL (default: http://localhost:8069)
  ODOO_API_KEY_DEMO  - API key for demo user
  ODOO_API_KEY_MGR   - API key for sales_mgr user
  ODOO_API_KEY_ADMIN - API key for admin user

Run:
  cd addons/foggy-odoo-bridge
  ODOO_API_KEY_DEMO=fmcp_xxx ODOO_API_KEY_MGR=fmcp_yyy ODOO_API_KEY_ADMIN=fmcp_zzz \
    python -m pytest tests/e2e/test_permission_rules.py -v
"""
import json
import os
import pytest
import requests

ODOO_MCP_URL = os.getenv('ODOO_MCP_URL', 'http://localhost:8069')

# API keys for three user roles
API_KEYS = {
    'demo': os.getenv('ODOO_API_KEY_DEMO', ''),
    'sales_mgr': os.getenv('ODOO_API_KEY_MGR', ''),
    'admin': os.getenv('ODOO_API_KEY_ADMIN', ''),
}

needs_all_keys = pytest.mark.skipif(
    not all(API_KEYS.values()),
    reason='All three API keys required: ODOO_API_KEY_DEMO, ODOO_API_KEY_MGR, ODOO_API_KEY_ADMIN')


def _make_session(api_key):
    """Create a requests session with the given API key."""
    s = requests.Session()
    s.headers.update({
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {api_key}',
    })
    return s


def _query_model(session, model, payload):
    """Query a model through the Odoo MCP Gateway."""
    rpc_payload = {
        'jsonrpc': '2.0', 'id': 1,
        'method': 'tools/call',
        'params': {
            'name': 'dataset.query_model',
            'arguments': {'model': model, 'payload': payload},
        }
    }
    r = session.post(f'{ODOO_MCP_URL}/foggy-mcp/rpc', json=rpc_payload)
    assert r.status_code == 200, f'HTTP {r.status_code}: {r.text[:200]}'
    body = r.json()

    if 'error' in body:
        return None, body['error'].get('message', 'Unknown error')

    content = body.get('result', {}).get('content', [])
    if not content:
        return None, 'Empty content'

    text = content[0].get('text', '')
    try:
        data = json.loads(text)
        if data.get('code') == 200:
            return data['data'], None
        return None, data.get('msg', f'Non-200: {data.get("code")}')
    except (json.JSONDecodeError, KeyError, TypeError) as e:
        return None, f'Parse error: {e}'


def _count_rows(session, model, columns=None):
    """Query a model and return total row count.

    Uses pagination.returned (actual rows returned) rather than totalCount
    (which may be null). Requests a high limit to get all rows.
    """
    payload = {
        'columns': columns or ['id'],
        'limit': 10000,
    }
    data, err = _query_model(session, model, payload)
    if data is None:
        return -1, err
    returned = data.get('pagination', {}).get('returned', 0)
    return returned, None


# ═══════════════════════════════════════════════════════════════
#  Connectivity check
# ═══════════════════════════════════════════════════════════════

@pytest.fixture(scope='module')
def check_odoo():
    """Skip all tests in this module if Odoo is not reachable."""
    try:
        r = requests.get(f'{ODOO_MCP_URL}/foggy-mcp/health', timeout=5)
        if r.status_code != 200:
            pytest.skip(f'Odoo MCP Gateway not reachable at {ODOO_MCP_URL}')
    except requests.ConnectionError:
        pytest.skip(f'Odoo MCP Gateway not reachable at {ODOO_MCP_URL}')


@pytest.fixture(scope='module')
def sessions(check_odoo):
    """Create sessions for all three users."""
    return {role: _make_session(key) for role, key in API_KEYS.items() if key}


# ═══════════════════════════════════════════════════════════════
#  purchase.order — company isolation only
# ═══════════════════════════════════════════════════════════════

@needs_all_keys
class TestPurchaseOrderPermissions:
    """purchase.order: Global rule [('company_id', 'in', company_ids)].

    All purchase orders are company 1 → all users see all 11 orders.
    demo (companies [1,2]), sales_mgr (companies [1]), admin (companies [1,2]).
    """

    def test_demo_sees_all_purchases(self, sessions):
        total, err = _count_rows(sessions['demo'],
                                 'OdooPurchaseOrderQueryModel', ['name', 'amountTotal'])
        assert err is None, f'Demo purchase query failed: {err}'
        assert total == 11, f'Demo expected 11 purchases, got {total}'

    def test_sales_mgr_sees_all_purchases(self, sessions):
        total, err = _count_rows(sessions['sales_mgr'],
                                 'OdooPurchaseOrderQueryModel', ['name', 'amountTotal'])
        assert err is None, f'Sales mgr purchase query failed: {err}'
        assert total == 11, f'Sales mgr expected 11 purchases, got {total}'

    def test_admin_sees_all_purchases(self, sessions):
        total, err = _count_rows(sessions['admin'],
                                 'OdooPurchaseOrderQueryModel', ['name', 'amountTotal'])
        assert err is None, f'Admin purchase query failed: {err}'
        assert total == 11, f'Admin expected 11 purchases, got {total}'


# ═══════════════════════════════════════════════════════════════
#  account.move — company isolation + tautology group rules
# ═══════════════════════════════════════════════════════════════

@needs_all_keys
class TestAccountMovePermissions:
    """account.move: Global rule + Billing tautology.

    Global: [('company_id', 'in', company_ids)] → all 24 moves (company 1)
    Group: Billing group [(1,'=',1)] → tautology → no additional filter.
    All users have Billing group → all see 24 entries.
    """

    def test_demo_sees_all_moves(self, sessions):
        total, err = _count_rows(sessions['demo'],
                                 'OdooAccountMoveQueryModel', ['name', 'amountTotalSigned'])
        assert err is None, f'Demo account.move query failed: {err}'
        assert total == 24, f'Demo expected 24 moves, got {total}'

    def test_sales_mgr_sees_all_moves(self, sessions):
        total, err = _count_rows(sessions['sales_mgr'],
                                 'OdooAccountMoveQueryModel', ['name', 'amountTotalSigned'])
        assert err is None, f'Sales mgr account.move query failed: {err}'
        assert total == 24, f'Sales mgr expected 24 moves, got {total}'

    def test_admin_sees_all_moves(self, sessions):
        total, err = _count_rows(sessions['admin'],
                                 'OdooAccountMoveQueryModel', ['name', 'amountTotalSigned'])
        assert err is None, f'Admin account.move query failed: {err}'
        assert total == 24, f'Admin expected 24 moves, got {total}'


# ═══════════════════════════════════════════════════════════════
#  stock.picking — company isolation with difference!
# ═══════════════════════════════════════════════════════════════

@needs_all_keys
class TestStockPickingPermissions:
    """stock.picking: Global rule [('company_id', 'in', company_ids)].

    Company 1: 17 pickings, Company 2: 8 pickings = 25 total.
    - demo (companies [1,2]): sees 25
    - sales_mgr (companies [1]): sees 17  ← RESTRICTED!
    - admin (companies [1,2]): sees 25
    """

    def test_demo_sees_all_pickings(self, sessions):
        total, err = _count_rows(sessions['demo'],
                                 'OdooStockPickingQueryModel', ['name', 'state'])
        assert err is None, f'Demo stock.picking query failed: {err}'
        assert total == 25, f'Demo expected 25 pickings, got {total}'

    def test_sales_mgr_restricted_to_company1(self, sessions):
        """sales_mgr has only company 1 → sees only 17 pickings (not 25)."""
        total, err = _count_rows(sessions['sales_mgr'],
                                 'OdooStockPickingQueryModel', ['name', 'state'])
        assert err is None, f'Sales mgr stock.picking query failed: {err}'
        assert total == 17, f'Sales mgr expected 17 pickings (company 1 only), got {total}'

    def test_admin_sees_all_pickings(self, sessions):
        total, err = _count_rows(sessions['admin'],
                                 'OdooStockPickingQueryModel', ['name', 'state'])
        assert err is None, f'Admin stock.picking query failed: {err}'
        assert total == 25, f'Admin expected 25 pickings, got {total}'

    def test_sales_mgr_data_is_company1_only(self, sessions):
        """Verify sales_mgr only sees company 1 pickings (data-level check)."""
        data, err = _query_model(sessions['sales_mgr'],
            'OdooStockPickingQueryModel', {
                'columns': ['company$caption'],
                'distinct': True,
            })
        assert err is None, f'Sales mgr company check failed: {err}'
        items = data.get('items', [])
        companies = {r.get('company$caption') for r in items}
        # Should only have one company
        assert len(companies) == 1, \
            f'Sales mgr should see only 1 company, got: {companies}'


# ═══════════════════════════════════════════════════════════════
#  hr.employee — company isolation with + [False]
# ═══════════════════════════════════════════════════════════════

@needs_all_keys
class TestHrEmployeePermissions:
    """hr.employee: Global rule [('company_id', 'in', company_ids + [False])].

    The + [False] allows employees with no company. All 20 employees are
    company 1 → all users see all 20.
    """

    def test_demo_sees_all_employees(self, sessions):
        total, err = _count_rows(sessions['demo'],
                                 'OdooHrEmployeeQueryModel', ['name', 'jobTitle'])
        assert err is None, f'Demo hr.employee query failed: {err}'
        assert total == 20, f'Demo expected 20 employees, got {total}'

    def test_sales_mgr_sees_all_employees(self, sessions):
        total, err = _count_rows(sessions['sales_mgr'],
                                 'OdooHrEmployeeQueryModel', ['name', 'jobTitle'])
        assert err is None, f'Sales mgr hr.employee query failed: {err}'
        assert total == 20, f'Sales mgr expected 20 employees, got {total}'

    def test_admin_sees_all_employees(self, sessions):
        total, err = _count_rows(sessions['admin'],
                                 'OdooHrEmployeeQueryModel', ['name', 'jobTitle'])
        assert err is None, f'Admin hr.employee query failed: {err}'
        assert total == 20, f'Admin expected 20 employees, got {total}'


# ═══════════════════════════════════════════════════════════════
#  res.partner — direct Foggy query (no gateway permission)
# ═══════════════════════════════════════════════════════════════

class TestResPartnerDirect:
    """res.partner: Verify basic queryability through direct Foggy endpoint.

    The res.partner global ir.rule uses 'partner_share' (boolean field not
    in QM model), which cannot be cleanly converted to a DSL slice.

    These tests verify the model works via direct Foggy queries (bypassing
    Odoo permission bridge).
    """

    @pytest.fixture(scope='class')
    def foggy_session(self):
        """Direct Foggy MCP session (no Odoo auth)."""
        foggy_url = os.getenv('FOGGY_MCP_URL', 'http://localhost:7108')
        s = requests.Session()
        s.headers.update({
            'Content-Type': 'application/json',
            'X-NS': 'odoo',
        })
        try:
            r = s.get(f'{foggy_url}/actuator/health', timeout=5)
            if r.status_code != 200:
                pytest.skip(f'Foggy not reachable at {foggy_url}')
        except requests.ConnectionError:
            pytest.skip(f'Foggy not reachable at {foggy_url}')
        s._foggy_url = foggy_url
        return s

    def test_partner_basic_query(self, foggy_session):
        """res.partner returns data via direct Foggy query."""
        payload = {
            'jsonrpc': '2.0', 'id': 1,
            'method': 'tools/call',
            'params': {
                'name': 'dataset.query_model',
                'arguments': {
                    'model': 'OdooResPartnerQueryModel',
                    'payload': {
                        'columns': ['completeName', 'email'],
                        'limit': 5,
                    }
                }
            }
        }
        r = foggy_session.post(
            f'{foggy_session._foggy_url}/mcp/admin/rpc', json=payload)
        assert r.status_code == 200
        body = r.json()
        assert 'error' not in body, f'Foggy error: {body.get("error")}'
        text = body['result']['content'][0]['text']
        data = json.loads(text)
        assert data.get('code') == 200, f'Non-200: {data}'
        assert data['data']['pagination']['returned'] > 0

    def test_partner_count(self, foggy_session):
        """Verify total partner count (no permission filter)."""
        payload = {
            'jsonrpc': '2.0', 'id': 1,
            'method': 'tools/call',
            'params': {
                'name': 'dataset.query_model',
                'arguments': {
                    'model': 'OdooResPartnerQueryModel',
                    'payload': {
                        'columns': ['id'],
                        'limit': 10000,
                    }
                }
            }
        }
        r = foggy_session.post(
            f'{foggy_session._foggy_url}/mcp/admin/rpc', json=payload)
        body = r.json()
        text = body['result']['content'][0]['text']
        data = json.loads(text)
        returned = data['data']['pagination']['returned']
        # All active partners (58 in demo data)
        assert returned >= 50, f'Expected ≥50 partners, got {returned}'


# ═══════════════════════════════════════════════════════════════
#  Cross-model summary: verify permission differences
# ═══════════════════════════════════════════════════════════════

@needs_all_keys
class TestPermissionDifferences:
    """Verify that permissions produce measurable differences between users.

    The key test: stock.picking shows different counts for different users,
    proving that ir.rule → DSL slice injection is working end-to-end.
    """

    def test_stock_picking_differs_between_users(self, sessions):
        """stock.picking: sales_mgr sees fewer than demo/admin."""
        demo_total, err = _count_rows(sessions['demo'],
                                      'OdooStockPickingQueryModel', ['name'])
        assert err is None, f'Demo query failed: {err}'

        mgr_total, err = _count_rows(sessions['sales_mgr'],
                                     'OdooStockPickingQueryModel', ['name'])
        assert err is None, f'Sales mgr query failed: {err}'

        admin_total, err = _count_rows(sessions['admin'],
                                       'OdooStockPickingQueryModel', ['name'])
        assert err is None, f'Admin query failed: {err}'

        # Key assertion: sales_mgr is restricted
        assert mgr_total < demo_total, \
            f'sales_mgr ({mgr_total}) should see fewer pickings than demo ({demo_total})'
        assert demo_total == admin_total, \
            f'demo ({demo_total}) and admin ({admin_total}) should see the same'
