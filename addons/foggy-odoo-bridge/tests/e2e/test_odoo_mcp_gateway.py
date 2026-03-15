"""
E2E tests: Odoo MCP Gateway full chain.

Verifies the complete flow:
  AI Client → Odoo MCP Gateway → Foggy MCP Server → PostgreSQL

Tests cover:
1. Health endpoint diagnostics
2. tools/list with permission filtering
3. tools/call through the gateway (including hierarchy operators)

NOTE: These tests require both Odoo and Foggy MCP Server running.
      Tests that call tools require a valid ODOO_API_KEY (fmcp_ prefix).
      Set env var:  ODOO_API_KEY=fmcp_xxxxxxxx
      Tests are skipped if Odoo is not reachable.
"""
import json
import os
import pytest

ODOO_API_KEY = os.getenv('ODOO_API_KEY', '')
needs_api_key = pytest.mark.skipif(
    not ODOO_API_KEY,
    reason='ODOO_API_KEY not set — gateway auth tests skipped')


def _call_gateway(session, url, method, params=None):
    """Call the Odoo MCP Gateway JSON-RPC endpoint."""
    payload = {
        'jsonrpc': '2.0', 'id': 1,
        'method': method,
        'params': params or {}
    }
    r = session.post(f'{url}/foggy-mcp/rpc', json=payload)
    assert r.status_code == 200, f'HTTP {r.status_code}: {r.text[:200]}'
    return r.json()


def _call_tool(session, url, tool_name, arguments):
    """Call a tool through the Odoo MCP Gateway."""
    body = _call_gateway(session, url, 'tools/call', {
        'name': tool_name,
        'arguments': arguments,
    })
    if 'error' in body:
        return None, body['error'].get('message', 'Unknown RPC error')
    content = body.get('result', {}).get('content', [])
    if not content:
        return None, 'Empty content'
    text = content[0].get('text', '')
    try:
        data = json.loads(text)
        if data.get('code') == 200:
            return data['data'], None
        return None, data.get('msg', f'Non-200 code: {data.get("code")}')
    except (json.JSONDecodeError, KeyError, TypeError) as e:
        # Some tools return plain text, not JSON
        return {'text': text}, None


def _query_model(session, url, model, payload):
    """Convenience: call dataset.query_model via gateway."""
    return _call_tool(session, url, 'dataset.query_model', {
        'model': model,
        'payload': payload,
    })


# ═══════════════════════════════════════════════════════════════
#  Health Endpoint  (no auth required)
# ═══════════════════════════════════════════════════════════════

class TestOdooMcpHealth:
    """Verify Odoo MCP Gateway health endpoint."""

    def test_health_endpoint(self, odoo_session, odoo_url):
        """Health endpoint returns status and checks."""
        r = odoo_session.get(f'{odoo_url}/foggy-mcp/health')
        assert r.status_code == 200
        data = r.json()
        assert data.get('status') == 'ok', \
            f"Expected status 'ok', got: {data.get('status')}"
        assert 'checks' in data, f"Missing 'checks' in health: {list(data.keys())}"

    def test_health_foggy_reachable(self, odoo_session, odoo_url):
        """Health confirms Foggy MCP Server is reachable."""
        r = odoo_session.get(f'{odoo_url}/foggy-mcp/health')
        data = r.json()
        checks = data.get('checks', {})
        foggy = checks.get('foggy_server', {})
        assert foggy.get('status') == 'ok', \
            f"Foggy server not reachable: {foggy}"

    def test_health_models_mapped(self, odoo_session, odoo_url):
        """Health shows mapped Odoo models."""
        r = odoo_session.get(f'{odoo_url}/foggy-mcp/health')
        data = r.json()
        models = data.get('checks', {}).get('models', {})
        assert models.get('mapped_count', 0) >= 7, \
            f"Expected ≥7 mapped models, got: {models}"


# ═══════════════════════════════════════════════════════════════
#  tools/list  (requires API key)
# ═══════════════════════════════════════════════════════════════

@needs_api_key
class TestOdooToolsList:
    """Verify tools/list returns tools filtered by user permissions."""

    def test_tools_list_returns_tools(self, odoo_session, odoo_url):
        body = _call_gateway(odoo_session, odoo_url, 'tools/list')
        result = body.get('result', {})
        tools = result.get('tools', [])
        assert len(tools) > 0, 'Expected at least one tool'

    def test_tools_list_contains_query_model(self, odoo_session, odoo_url):
        body = _call_gateway(odoo_session, odoo_url, 'tools/list')
        result = body.get('result', {})
        tools = result.get('tools', [])
        tool_names = [t['name'] for t in tools]
        assert 'dataset.query_model' in tool_names, \
            f'dataset.query_model not found in: {tool_names}'


# ═══════════════════════════════════════════════════════════════
#  Query through Gateway  (requires API key)
# ═══════════════════════════════════════════════════════════════

@needs_api_key
class TestGatewayQueries:
    """Test queries flowing through the full Odoo MCP Gateway chain."""

    def test_query_sale_orders(self, odoo_session, odoo_url):
        """Basic sale order query through the gateway."""
        data, err = _query_model(odoo_session, odoo_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['name', 'partner$caption', 'amountTotal'],
                'limit': 5,
            })
        assert data is not None, f'Sale order query failed: {err}'
        assert data['pagination']['returned'] > 0

    def test_query_employees(self, odoo_session, odoo_url):
        """Query employees through the gateway."""
        data, err = _query_model(odoo_session, odoo_url,
            'OdooHrEmployeeQueryModel', {
                'columns': ['name', 'department$caption', 'company$caption'],
                'limit': 5,
            })
        assert data is not None, f'Employee query failed: {err}'
        assert data['pagination']['returned'] > 0

    def test_query_with_aggregation(self, odoo_session, odoo_url):
        """Aggregation query through the gateway."""
        data, err = _query_model(odoo_session, odoo_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['company$caption', 'sum(amountTotal) as total'],
            })
        assert data is not None, f'Aggregation query failed: {err}'
        items = data.get('items', [])
        assert len(items) > 0


# ═══════════════════════════════════════════════════════════════
#  Hierarchy Queries through Gateway  (requires API key)
# ═══════════════════════════════════════════════════════════════

@needs_api_key
class TestGatewayHierarchyQueries:
    """Test hierarchy queries through the full Odoo MCP Gateway chain."""

    def test_query_with_hierarchy_filter(self, odoo_session, odoo_url):
        """
        When user queries with hierarchy operators, the gateway should
        pass them through to Foggy's closure table engine.
        """
        data, err = _query_model(odoo_session, odoo_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$caption', 'amountTotal'],
                'slice': [{'field': 'company$id',
                           'op': 'selfAndDescendantsOf', 'value': 1}],
                'limit': 5,
            })
        assert data is not None, f'Hierarchy query failed: {err}'
        assert data['pagination']['returned'] > 0


# ═══════════════════════════════════════════════════════════════
#  Closure Table Integrity  (direct Foggy, no auth needed)
# ═══════════════════════════════════════════════════════════════

class TestClosureTableIntegrity:
    """Verify closure tables are properly populated in PostgreSQL."""

    def test_company_closure_populated(self, foggy_session, foggy_url):
        """
        Query sale orders with selfAndDescendantsOf on root company
        via direct Foggy endpoint.  If closure table is empty, returns 0 rows.
        """
        from .conftest import query_model as qm
        data, err = qm(foggy_session, foggy_url,
            'OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$caption'],
                'slice': [{'field': 'company$id',
                           'op': 'selfAndDescendantsOf', 'value': 1}],
                'limit': 5,
            })
        assert data is not None, \
            f'Closure query failed: {err}. Run: SELECT refresh_all_closures();'
        assert data['pagination']['returned'] > 0, \
            'Closure table may not be populated. Run: SELECT refresh_all_closures();'
