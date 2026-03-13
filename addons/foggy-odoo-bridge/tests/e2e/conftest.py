"""
E2E integration test fixtures.

Environment variables:
  FOGGY_MCP_URL  - Foggy MCP Server base URL (default: http://localhost:8080)
  ODOO_MCP_URL   - Odoo MCP Gateway base URL  (default: http://localhost:8069)
  ODOO_API_KEY   - Odoo MCP API key (fmcp_ prefix)
"""
import os
import pytest
import requests

FOGGY_MCP_URL = os.getenv('FOGGY_MCP_URL', 'http://localhost:8080')
ODOO_MCP_URL = os.getenv('ODOO_MCP_URL', 'http://localhost:8069')
ODOO_API_KEY = os.getenv('ODOO_API_KEY', '')


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
def foggy_session(foggy_url):
    """Session-level requests session for Foggy MCP Server."""
    s = requests.Session()
    s.headers.update({'Content-Type': 'application/json'})
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
