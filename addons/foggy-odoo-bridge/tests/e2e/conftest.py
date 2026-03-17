"""
E2E integration test fixtures.

Environment variables:
  FOGGY_MCP_URL  - Foggy MCP Server base URL (default: http://localhost:7108)
  ODOO_MCP_URL   - Odoo MCP Gateway base URL  (default: http://localhost:8069)
  ODOO_API_KEY   - Odoo MCP API key (fmcp_ prefix)
  FOGGY_NS       - Foggy namespace (default: odoo)
"""
import json
import os
import pytest
import requests

FOGGY_MCP_URL = os.getenv('FOGGY_MCP_URL', 'http://localhost:7108')
ODOO_MCP_URL = os.getenv('ODOO_MCP_URL', 'http://localhost:8069')
ODOO_API_KEY = os.getenv('ODOO_API_KEY', '')
FOGGY_NS = os.getenv('FOGGY_NS', 'odoo')


@pytest.fixture(scope='session')
def foggy_url():
    return FOGGY_MCP_URL


@pytest.fixture(scope='session')
def odoo_url():
    return ODOO_MCP_URL


@pytest.fixture(scope='session')
def api_key():
    return ODOO_API_KEY


@pytest.fixture(scope='session')
def namespace():
    return FOGGY_NS


@pytest.fixture(scope='session')
def foggy_session(foggy_url, namespace):
    """Session-level requests session for Foggy MCP Server (with X-NS header)."""
    s = requests.Session()
    s.headers.update({
        'Content-Type': 'application/json',
        'X-NS': namespace,
    })
    # Verify connectivity
    try:
        r = s.get(f'{foggy_url}/actuator/health', timeout=5)
        if r.status_code != 200:
            pytest.skip(f'Foggy MCP not reachable at {foggy_url}')
    except requests.ConnectionError:
        pytest.skip(f'Foggy MCP not reachable at {foggy_url}')
    return s


@pytest.fixture(scope='session')
def odoo_session(odoo_url, api_key):
    """Session-level requests session for Odoo MCP Gateway."""
    s = requests.Session()
    s.headers.update({'Content-Type': 'application/json'})
    if api_key:
        s.headers.update({'Authorization': f'Bearer {api_key}'})
    # Verify connectivity
    try:
        r = s.get(f'{odoo_url}/foggy-mcp/health', timeout=5)
        if r.status_code != 200:
            pytest.skip(f'Odoo MCP Gateway not reachable at {odoo_url}')
    except requests.ConnectionError:
        pytest.skip(f'Odoo MCP Gateway not reachable at {odoo_url}')
    return s


def call_foggy_tool(session, url, tool_name, arguments):
    """
    Call a Foggy MCP tool and return (data, error).

    Returns:
        tuple: (data_dict, None) on success, (None, error_message) on failure
    """
    payload = {
        'jsonrpc': '2.0',
        'id': 1,
        'method': 'tools/call',
        'params': {'name': tool_name, 'arguments': arguments}
    }
    r = session.post(f'{url}/mcp/admin/rpc', json=payload)
    assert r.status_code == 200, f'HTTP {r.status_code}: {r.text[:200]}'
    body = r.json()
    if 'error' in body:
        return None, body['error'].get('message', 'Unknown RPC error')
    text = body['result']['content'][0]['text']
    data = json.loads(text)
    if data.get('code') == 200:
        return data['data'], None
    return None, data.get('msg', f'Non-200 code: {data.get("code")}')


def query_model(session, url, model, payload):
    """
    Convenience: call dataset.query_model with model + payload.

    Returns:
        tuple: (data_dict, error_message)
    """
    return call_foggy_tool(session, url, 'dataset.query_model', {
        'model': model,
        'payload': payload,
    })
