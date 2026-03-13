"""
E2E tests: Odoo MCP Gateway full chain.

Verifies the complete flow:
  AI Client → Odoo MCP Gateway → Foggy MCP Server → PostgreSQL

Tests cover:
1. Health endpoint diagnostics
2. tools/list with permission filtering
3. tools/call with payload.slice injection (including hierarchy operators)
"""
import pytest


class TestOdooMcpHealth:
    """Verify Odoo MCP Gateway health endpoint."""

    def test_health_endpoint(self, odoo_session, odoo_url):
        r = odoo_session.get(f'{odoo_url}/foggy-mcp/health')
        assert r.status_code == 200
        data = r.json()
        assert 'foggy_server' in data
        assert 'odoo' in data

    def test_health_foggy_reachable(self, odoo_session, odoo_url):
        r = odoo_session.get(f'{odoo_url}/foggy-mcp/health')
        data = r.json()
        foggy = data.get('foggy_server', {})
        assert foggy.get('status') == 'ok', \
            f"Foggy server not reachable: {foggy}"


class TestOdooToolsList:
    """Verify tools/list returns Odoo models filtered by user permissions."""

    def test_tools_list_returns_tools(self, odoo_session, odoo_url):
        payload = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/list',
            'params': {}
        }
        r = odoo_session.post(f'{odoo_url}/foggy-mcp/rpc', json=payload)
        assert r.status_code == 200
        result = r.json().get('result', {})
        tools = result.get('tools', [])
        assert len(tools) > 0, 'Expected at least one tool'


class TestOdooHierarchyQueries:
    """Test hierarchy queries through the full Odoo MCP Gateway chain."""

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
        r = session.post(f'{url}/foggy-mcp/rpc', json=payload)
        assert r.status_code == 200
        body = r.json()
        assert 'error' not in body, f"RPC error: {body.get('error')}"
        return body.get('result', {})

    def test_query_sale_orders(self, odoo_session, odoo_url):
        """Basic sale order query through the gateway."""
        result = self._call_tool(odoo_session, odoo_url,
            'query_OdooSaleOrderQueryModel', {
                'columns': ['name', 'partner$name', 'amountTotal'],
            })
        content = result.get('content', [])
        assert len(content) > 0, 'Expected sale order results'

    def test_query_with_hierarchy_slice_injected(self, odoo_session, odoo_url):
        """
        When Odoo ir.rule contains child_of/parent_of, the gateway should
        inject selfAndDescendantsOf/selfAndAncestorsOf into payload.slice.

        This test verifies the result is not empty (permission injection
        doesn't block the query).
        """
        result = self._call_tool(odoo_session, odoo_url,
            'query_OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$name', 'amountTotal'],
            })
        content = result.get('content', [])
        # User should see at least their company's data
        assert len(content) > 0, 'Expected results (permission may be too restrictive)'

    def test_query_employees_through_gateway(self, odoo_session, odoo_url):
        """Query employees through the gateway."""
        result = self._call_tool(odoo_session, odoo_url,
            'query_OdooHrEmployeeQueryModel', {
                'columns': ['name', 'department$name', 'company$name'],
            })
        content = result.get('content', [])
        assert len(content) > 0, 'Expected employee results'


class TestClosureTableIntegrity:
    """Verify closure tables are properly populated in PostgreSQL."""

    def test_company_closure_populated(self, odoo_session, odoo_url):
        """
        Query sale orders with selfAndDescendantsOf on root company.
        If closure table is empty, the query returns no results.
        """
        result = self._call_tool_direct(odoo_session, odoo_url,
            'query_OdooSaleOrderQueryModel', {
                'columns': ['name', 'company$name'],
                'slice': {'company$id': {'selfAndDescendantsOf': 1}},
            })
        content = result.get('content', [])
        assert len(content) > 0, \
            'Closure table may not be populated. Run: SELECT refresh_all_closures();'

    def _call_tool_direct(self, session, url, tool_name, arguments):
        """Call tool bypassing permission injection (for testing closure tables)."""
        payload = {
            'jsonrpc': '2.0',
            'id': 1,
            'method': 'tools/call',
            'params': {
                'name': tool_name,
                'arguments': arguments
            }
        }
        # Use direct Foggy endpoint if available, otherwise fall back to gateway
        import os
        foggy_url = os.getenv('FOGGY_MCP_URL', 'http://localhost:8080')
        try:
            r = session.post(f'{foggy_url}/mcp/admin/rpc', json=payload)
            if r.status_code == 200:
                body = r.json()
                if 'error' not in body:
                    return body.get('result', {})
        except Exception:
            pass
        # Fallback to gateway
        r = session.post(f'{url}/foggy-mcp/rpc', json=payload)
        assert r.status_code == 200
        return r.json().get('result', {})
