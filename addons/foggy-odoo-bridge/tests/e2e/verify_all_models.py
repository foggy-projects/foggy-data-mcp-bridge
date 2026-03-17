"""Quick verification script for all Odoo models via Foggy MCP direct."""
import requests
import json
import sys

FOGGY_URL = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:7108'
NAMESPACE = sys.argv[2] if len(sys.argv) > 2 else 'odoo'

def call_tool(tool_name, arguments):
    r = requests.post(f'{FOGGY_URL}/mcp/admin/rpc', json={
        'jsonrpc': '2.0', 'id': 1,
        'method': 'tools/call',
        'params': {'name': tool_name, 'arguments': arguments}
    }, headers={'X-NS': NAMESPACE}, timeout=15)
    body = r.json()
    if 'error' in body:
        return None, body['error'].get('message', 'Unknown RPC error')
    text = body['result']['content'][0]['text']
    data = json.loads(text)
    if data.get('code') == 200:
        return data['data'], None
    return None, data.get('msg', 'Unknown error')

def main():
    models = [
        ('OdooSaleOrderQueryModel',     ['name', 'amountTotal']),
        ('OdooSaleOrderLineQueryModel', ['name', 'priceSubtotal']),
        ('OdooPurchaseOrderQueryModel', ['name', 'amountTotal']),
        ('OdooAccountMoveQueryModel',   ['name', 'amountTotalSigned']),
        ('OdooStockPickingQueryModel',  ['name', 'state']),
        ('OdooHrEmployeeQueryModel',    ['name', 'jobTitle']),
        ('OdooResPartnerQueryModel',    ['completeName', 'email']),
        ('OdooResCompanyQueryModel',    ['name', 'email']),
    ]

    print("=" * 60)
    print("  Foggy MCP — All 8 Odoo Models Verification")
    print("=" * 60)
    print()

    passed = 0
    failed = 0

    # ── Basic Model Queries ──────────────────────────────────
    for model, cols in models:
        data, err = call_tool('dataset.query_model', {
            'model': model,
            'payload': {'columns': cols, 'limit': 3}
        })
        if data:
            count = data['pagination']['returned']
            print(f"  OK  {model}: {count} rows")
            passed += 1
        else:
            print(f"  FAIL {model}: {err}")
            failed += 1

    # ── Dimension Caption Queries ────────────────────────────
    print()
    print("-" * 60)
    print("  Dimension Caption Queries")
    print("-" * 60)
    print()

    caption_tests = [
        # VARCHAR caption columns
        ('OdooSaleOrderQueryModel',     'partner$caption',       'Partner (complete_name, VARCHAR)'),
        ('OdooSaleOrderQueryModel',     'company$caption',       'Company (name, VARCHAR)'),
        ('OdooHrEmployeeQueryModel',    'department$caption',    'Department (complete_name, VARCHAR)'),
        ('OdooHrEmployeeQueryModel',    'workLocation$caption',  'WorkLocation (name, VARCHAR)'),
        ('OdooResCompanyQueryModel',    'currency$caption',      'Currency (name, VARCHAR)'),
        ('OdooStockPickingQueryModel',  'locationSrc$caption',   'Source Location (complete_name, VARCHAR)'),
        # JSONB caption columns (Odoo 17 translatable fields, need ->> en_US)
        ('OdooHrEmployeeQueryModel',    'job$caption',           'Job (JSONB ->> en_US)'),
        ('OdooResPartnerQueryModel',    'country$caption',       'Country (JSONB ->> en_US)'),
        ('OdooSaleOrderQueryModel',     'salesTeam$caption',     'SalesTeam (JSONB ->> en_US)'),
        ('OdooPurchaseOrderQueryModel', 'pickingType$caption',   'PickingType (JSONB ->> en_US)'),
        ('OdooAccountMoveQueryModel',   'journal$caption',       'Journal (JSONB ->> en_US)'),
        ('OdooStockPickingQueryModel',  'pickingType$caption',   'PickingType (JSONB ->> en_US)'),
        ('OdooSaleOrderLineQueryModel', 'uom$caption',           'UoM (JSONB ->> en_US)'),
    ]

    for model, caption_col, desc in caption_tests:
        data, err = call_tool('dataset.query_model', {
            'model': model,
            'payload': {'columns': [caption_col], 'limit': 3}
        })
        if data:
            items = data.get('items', [])
            sample = items[0].get(caption_col, '(null)') if items else '(empty)'
            print(f"  OK  {caption_col:<25s} = {sample}")
            passed += 1
        else:
            print(f"  FAIL {caption_col:<25s} ({desc}): {err}")
            failed += 1

    # ── Closure Table Hierarchy Queries ──────────────────────
    print()
    print("-" * 60)
    print("  Closure Table Hierarchy Queries")
    print("-" * 60)
    print()

    # Department hierarchy: selfAndDescendantsOf (Management, id=2)
    data, err = call_tool('dataset.query_model', {
        'model': 'OdooHrEmployeeQueryModel',
        'payload': {
            'columns': ['name', 'department$caption', 'job$caption'],
            'slice': [{'field': 'department$id', 'op': 'selfAndDescendantsOf', 'value': 2}],
            'limit': 50
        }
    })
    if data:
        count = data['pagination']['returned']
        print(f"  OK  dept selfAndDescendantsOf(2): {count} employees")
        for item in data['items'][:3]:
            dept = item.get('department$caption', '?')
            job = item.get('job$caption', '?')
            print(f"       - {item.get('name', '?')} | {dept} | {job}")
        passed += 1
    else:
        print(f"  FAIL dept hierarchy: {err}")
        failed += 1

    # Company hierarchy via SaleOrder
    data, err = call_tool('dataset.query_model', {
        'model': 'OdooSaleOrderQueryModel',
        'payload': {
            'columns': ['name', 'company$caption', 'amountTotal'],
            'slice': [{'field': 'company$id', 'op': 'selfAndDescendantsOf', 'value': 1}],
            'limit': 5
        }
    })
    if data:
        count = data['pagination']['returned']
        print(f"  OK  company selfAndDescendantsOf(1): {count} orders")
        passed += 1
    else:
        print(f"  FAIL company hierarchy: {err}")
        failed += 1

    # GroupBy with hierarchy
    data, err = call_tool('dataset.query_model', {
        'model': 'OdooHrEmployeeQueryModel',
        'payload': {
            'columns': ['department$caption'],
            'slice': [{'field': 'department$id', 'op': 'selfAndDescendantsOf', 'value': 2}],
            'groupBy': ['department$caption'],
        }
    })
    if data:
        count = data['pagination']['returned']
        print(f"  OK  groupBy department + hierarchy: {count} groups")
        for item in data['items'][:5]:
            dept = item.get('department$caption', '?')
            print(f"       - {dept}")
        passed += 1
    else:
        print(f"  FAIL groupBy hierarchy: {err}")
        failed += 1

    # Company hierarchy via ResCompanyQueryModel — self-referential parent$caption now supported
    # (Fixed: JOIN dedup uses alias comparison instead of isRootEqual, name2Alias uses IdentityHashMap)
    data, err = call_tool('dataset.query_model', {
        'model': 'OdooResCompanyQueryModel',
        'payload': {
            'columns': ['name', 'parent$caption', 'currency$caption'],
            'slice': [{'field': 'parent$id', 'op': 'selfAndDescendantsOf', 'value': 1}],
        }
    })
    if data:
        count = data['pagination']['returned']
        print(f"  OK  ResCompany hierarchy selfAndDescendantsOf(1): {count} companies")
        for item in data['items'][:3]:
            parent = item.get('parent$caption', '?')
            cur = item.get('currency$caption', '?')
            print(f"       - {item.get('name', '?')} | parent: {parent} | currency: {cur}")
        passed += 1
    else:
        print(f"  FAIL ResCompany hierarchy: {err}")
        failed += 1

    print()
    print("=" * 60)
    total = passed + failed
    if failed == 0:
        print(f"  ALL PASSED: {passed}/{total}")
    else:
        print(f"  RESULT: {passed} passed, {failed} failed (total {total})")
    print("=" * 60)
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
