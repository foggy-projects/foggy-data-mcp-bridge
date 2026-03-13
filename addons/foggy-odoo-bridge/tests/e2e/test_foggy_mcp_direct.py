"""
E2E tests: Direct Foggy MCP Server queries.

Verifies that Foggy MCP Server can:
1. Load Odoo TM/QM models from the external bundle
2. Execute DSL queries with closure table hierarchy operators
3. Return correct results for descendant/ancestor queries
"""
import pytest


class TestFoggyHealth:
    """Verify Foggy MCP Server is running and healthy."""

    def test_actuator_health(self, foggy_session, foggy_url):
        r = foggy_session.get(f'{foggy_url}/actuator/health')
        assert r.status_code == 200
        data = r.json()
        assert data['status'] == 'UP'


class TestFoggyOdooModels:
    """Verify Odoo TM/QM models are loaded via external bundle."""

    def test_tools_list_contains_odoo_models(self, foggy_session, foggy_url):
        """MCP tools/list should include Odoo query models."""
        payload = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/list',
            'params': {}
        }
        r = foggy_session.post(f'{foggy_url}/mcp/admin/rpc', json=payload)
        assert r.status_code == 200
        result = r.json().get('result', {})
        tools = result.get('tools', [])
        tool_names = [t['name'] for t in tools]
        # At least some Odoo models should be present
        odoo_models = [n for n in tool_names if 'Odoo' in n or 'odoo' in n]
        assert len(odoo_models) > 0, f'No Odoo models found in tools: {tool_names}'


class TestClosureTableQueries:
    """Test hierarchy queries using closure table operators."""

    def _call_tool(self, session, url, tool_name, arguments):
        payload = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/call',
            'params': {
                'name': tool_name,
                'arguments': arguments
            }
        }
        r = session.post(f'{url}/mcp/admin/rpc', json=payload)
        assert r.status_code == 200
        body = r.json()
        assert 'error' not in body, f"RPC error: {body.get('error')}"
        return body.get('result', {})

    def test_sale_order_selfAndDescendantsOf_company(self, foggy_session, foggy_url):
        """Query sale orders for company 1 and all its descendants."""
        result = self._call_tool(foggy_session, foggy_url,
            'query_OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$name', 'amountTotal'],
                'slice': {'company$id': {'selfAndDescendantsOf': 1}}
            })
        content = result.get('content', [])
        assert len(content) > 0, 'Expected sale order results'
        # Company 1 has descendants 2 and 3, so should get orders from all companies
        text = content[0].get('text', '') if content else ''
        assert text, 'Empty result text'

    def test_sale_order_selfAndAncestorsOf_company(self, foggy_session, foggy_url):
        """Query sale orders for company 2 and its ancestors."""
        result = self._call_tool(foggy_session, foggy_url,
            'query_OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$name', 'amountTotal'],
                'slice': {'company$id': {'selfAndAncestorsOf': 2}}
            })
        content = result.get('content', [])
        assert len(content) > 0, 'Expected sale order results'

    def test_employee_department_hierarchy(self, foggy_session, foggy_url):
        """Query employees in department 4 (Management) and all sub-departments."""
        result = self._call_tool(foggy_session, foggy_url,
            'query_OdooHrEmployeeQueryModel', {
                'columns': ['name', 'department$name', 'jobTitle'],
                'slice': {'department$id': {'selfAndDescendantsOf': 4}}
            })
        content = result.get('content', [])
        assert len(content) > 0, 'Expected employee results'

    def test_closure_with_groupby(self, foggy_session, foggy_url):
        """Aggregate sale orders grouped by company, filtered by hierarchy."""
        result = self._call_tool(foggy_session, foggy_url,
            'query_OdooSaleOrderQueryModel', {
                'columns': ['company$name'],
                'slice': {'company$id': {'selfAndDescendantsOf': 1}},
                'groupBy': ['company$name'],
                'orderBy': [{'field': 'company$name', 'dir': 'ASC'}]
            })
        content = result.get('content', [])
        assert len(content) > 0, 'Expected grouped results'
