"""
Verify TM model column definitions against actual Odoo PostgreSQL schema.

Connects to the Odoo database and checks:
1. All referenced tables exist
2. All referenced columns exist with compatible types
3. All foreign key columns exist
4. Dimension tables and their caption columns exist

Usage:
    python verify_schema.py                              # default localhost:5432/odoo
    python verify_schema.py --host localhost --port 5432 --db odoo --user odoo --password odoo
    python verify_schema.py --docker foggy-odoo-postgres # via docker exec
"""
import argparse
import json
import subprocess
import sys


# ── TM Model Definitions ─────────────────────────────────────
# Model definitions extracted from foggy-odoo-bridge-java module
# (built into Docker image foggysource/foggy-odoo-mcp)
MODELS = [
    {
        'name': 'OdooSaleOrderModel',
        'table': 'sale_order',
        'columns': {
            'id': 'integer', 'name': 'varchar', 'state': 'varchar',
            'date_order': 'timestamp', 'commitment_date': 'timestamp',
            'create_date': 'timestamp', 'write_date': 'timestamp',
            'amount_untaxed': 'numeric', 'amount_tax': 'numeric',
            'amount_total': 'numeric', 'currency_rate': 'numeric',
        },
        'dimensions': [
            {'fk': 'partner_id', 'table': 'res_partner', 'caption_col': 'complete_name'},
            {'fk': 'user_id', 'table': 'res_users', 'caption_col': 'login'},
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
            {'fk': 'team_id', 'table': 'crm_team', 'caption_col': 'name'},
            {'fk': 'pricelist_id', 'table': 'product_pricelist', 'caption_col': 'name'},
            {'fk': 'warehouse_id', 'table': 'stock_warehouse', 'caption_col': 'name'},
        ]
    },
    {
        'name': 'OdooSaleOrderLineModel',
        'table': 'sale_order_line',
        'columns': {
            'id': 'integer', 'name': 'text',
            'product_uom_qty': 'numeric', 'qty_delivered': 'numeric',
            'qty_invoiced': 'numeric', 'price_unit': 'numeric',
            'price_subtotal': 'numeric', 'price_tax': 'numeric',
            'price_total': 'numeric', 'discount': 'numeric',
            'create_date': 'timestamp',
        },
        'dimensions': [
            {'fk': 'order_id', 'table': 'sale_order', 'caption_col': 'name'},
            {'fk': 'product_id', 'table': 'product_product', 'caption_col': 'default_code'},
            {'fk': 'product_uom', 'table': 'uom_uom', 'caption_col': 'name'},
            {'fk': 'salesman_id', 'table': 'res_users', 'caption_col': 'login'},
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
        ]
    },
    {
        'name': 'OdooPurchaseOrderModel',
        'table': 'purchase_order',
        'columns': {
            'id': 'integer', 'name': 'varchar', 'state': 'varchar',
            'priority': 'varchar', 'date_order': 'timestamp',
            'date_approve': 'timestamp', 'date_planned': 'timestamp',
            'create_date': 'timestamp',
            'amount_untaxed': 'numeric', 'amount_tax': 'numeric',
            'amount_total': 'numeric',
        },
        'dimensions': [
            {'fk': 'partner_id', 'table': 'res_partner', 'caption_col': 'complete_name'},
            {'fk': 'user_id', 'table': 'res_users', 'caption_col': 'login'},
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
            {'fk': 'currency_id', 'table': 'res_currency', 'caption_col': 'name'},
            {'fk': 'picking_type_id', 'table': 'stock_picking_type', 'caption_col': 'name'},
        ]
    },
    {
        'name': 'OdooAccountMoveModel',
        'table': 'account_move',
        'columns': {
            'id': 'integer', 'name': 'varchar', 'state': 'varchar',
            'move_type': 'varchar', 'payment_state': 'varchar',
            'date': 'date', 'invoice_date': 'date', 'invoice_date_due': 'date',
            'create_date': 'timestamp',
            'amount_untaxed': 'numeric', 'amount_tax': 'numeric',
            'amount_total': 'numeric', 'amount_residual': 'numeric',
            'amount_untaxed_signed': 'numeric', 'amount_tax_signed': 'numeric',
            'amount_total_signed': 'numeric', 'amount_residual_signed': 'numeric',
        },
        'dimensions': [
            {'fk': 'partner_id', 'table': 'res_partner', 'caption_col': 'complete_name'},
            {'fk': 'journal_id', 'table': 'account_journal', 'caption_col': 'name'},
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
            {'fk': 'currency_id', 'table': 'res_currency', 'caption_col': 'name'},
            {'fk': 'invoice_user_id', 'table': 'res_users', 'caption_col': 'login'},
            {'fk': 'team_id', 'table': 'crm_team', 'caption_col': 'name'},
        ]
    },
    {
        'name': 'OdooStockPickingModel',
        'table': 'stock_picking',
        'columns': {
            'id': 'integer', 'name': 'varchar', 'state': 'varchar',
            'priority': 'varchar', 'origin': 'varchar',
            'scheduled_date': 'timestamp', 'date_done': 'timestamp',
            'create_date': 'timestamp',
        },
        'dimensions': [
            {'fk': 'partner_id', 'table': 'res_partner', 'caption_col': 'complete_name'},
            {'fk': 'picking_type_id', 'table': 'stock_picking_type', 'caption_col': 'name'},
            {'fk': 'location_id', 'table': 'stock_location', 'caption_col': 'complete_name'},
            {'fk': 'location_dest_id', 'table': 'stock_location', 'caption_col': 'complete_name'},
            {'fk': 'user_id', 'table': 'res_users', 'caption_col': 'login'},
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
        ]
    },
    {
        'name': 'OdooHrEmployeeModel',
        'table': 'hr_employee',
        'columns': {
            'id': 'integer', 'name': 'varchar',
            'job_title': 'varchar', 'work_email': 'varchar',
            'work_phone': 'varchar', 'mobile_phone': 'varchar',
            'active': 'bool', 'gender': 'varchar', 'marital': 'varchar',
            'employee_type': 'varchar',
            'create_date': 'timestamp', 'write_date': 'timestamp',
        },
        'dimensions': [
            {'fk': 'department_id', 'table': 'hr_department', 'caption_col': 'complete_name'},
            {'fk': 'job_id', 'table': 'hr_job', 'caption_col': 'name'},
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
            {'fk': 'parent_id', 'table': 'hr_employee', 'caption_col': 'name'},
            {'fk': 'work_location_id', 'table': 'hr_work_location', 'caption_col': 'name'},
            {'fk': 'user_id', 'table': 'res_users', 'caption_col': 'login'},
        ]
    },
    {
        'name': 'OdooResPartnerModel',
        'table': 'res_partner',
        'columns': {
            'id': 'integer', 'complete_name': 'varchar', 'name': 'varchar',
            'type': 'varchar', 'email': 'varchar', 'phone': 'varchar',
            'mobile': 'varchar', 'website': 'varchar', 'vat': 'varchar',
            'ref': 'varchar', 'lang': 'varchar',
            'street': 'varchar', 'city': 'varchar', 'zip': 'varchar',
            'is_company': 'bool', 'active': 'bool',
            'customer_rank': 'integer', 'supplier_rank': 'integer',
            'create_date': 'timestamp', 'write_date': 'timestamp',
        },
        'dimensions': [
            {'fk': 'company_id', 'table': 'res_company', 'caption_col': 'name'},
            {'fk': 'country_id', 'table': 'res_country', 'caption_col': 'name'},
            {'fk': 'state_id', 'table': 'res_country_state', 'caption_col': 'name'},
            {'fk': 'parent_id', 'table': 'res_partner', 'caption_col': 'complete_name'},
            {'fk': 'user_id', 'table': 'res_users', 'caption_col': 'login'},
            {'fk': 'team_id', 'table': 'crm_team', 'caption_col': 'name'},
        ]
    },
    {
        'name': 'OdooResCompanyModel',
        'table': 'res_company',
        'columns': {
            'id': 'integer', 'name': 'varchar',
            'email': 'varchar', 'phone': 'varchar',
            'active': 'bool', 'create_date': 'timestamp',
        },
        'dimensions': [
            {'fk': 'currency_id', 'table': 'res_currency', 'caption_col': 'name'},
            {'fk': 'parent_id', 'table': 'res_company', 'caption_col': 'name'},
        ]
    },
]

# Known JSONB caption columns (need ->> extraction via captionDef dialectFormulaDef)
# These are Odoo 17 translatable fields stored as JSONB (e.g. {"en_US": "value"})
JSONB_CAPTION_COLS = {
    ('hr_job', 'name'),
    ('account_journal', 'name'),
    ('crm_team', 'name'),
    ('product_pricelist', 'name'),
    ('res_country', 'name'),
    ('stock_picking_type', 'name'),
    ('uom_uom', 'name'),
}

# Type compatibility map: TM type → acceptable PostgreSQL udt_names
TYPE_COMPAT = {
    'integer': {'int4', 'int2', 'int8'},
    'numeric': {'numeric', 'float4', 'float8'},
    'varchar': {'varchar', 'text', 'bpchar', 'name', 'jsonb'},  # jsonb allowed (JSONB translatable fields)
    'text':    {'text', 'varchar', 'jsonb'},
    'bool':    {'bool'},
    'date':    {'date'},
    'timestamp': {'timestamp', 'timestamptz'},
}


def run_sql(query, args):
    """Execute SQL and return rows as list of dicts."""
    if args.docker:
        cmd = ['docker', 'exec', args.docker, 'psql', '-U', args.user, '-d', args.db,
               '-t', '-A', '-F', '\t', '-c', query]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        if result.returncode != 0:
            print(f"  ERROR: psql failed: {result.stderr.strip()}", file=sys.stderr)
            return []
        lines = [l for l in result.stdout.strip().split('\n') if l.strip()]
    else:
        try:
            import psycopg2
        except ImportError:
            print("ERROR: psycopg2 not installed. Use --docker or: pip install psycopg2-binary", file=sys.stderr)
            sys.exit(1)
        conn = psycopg2.connect(host=args.host, port=args.port, dbname=args.db,
                                user=args.user, password=args.password)
        cur = conn.cursor()
        cur.execute(query)
        cols = [desc[0] for desc in cur.description]
        rows = cur.fetchall()
        cur.close()
        conn.close()
        return [dict(zip(cols, row)) for row in rows]

    # Parse tab-separated docker output
    if not lines:
        return []
    rows = []
    for line in lines:
        parts = line.split('\t')
        rows.append(parts)
    return rows


def load_schema(args):
    """Load full schema from information_schema.columns."""
    query = """
    SELECT table_name, column_name, udt_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
    ORDER BY table_name, ordinal_position
    """
    raw = run_sql(query, args)
    schema = {}  # table -> {col_name: udt_name}
    if not raw:
        return schema

    for row in raw:
        if isinstance(row, dict):
            t, c, u = row['table_name'], row['column_name'], row['udt_name']
        else:
            t, c, u = row[0], row[1], row[2]
        schema.setdefault(t, {})[c] = u
    return schema


def check_model(model, schema):
    """Check one TM model against the schema. Returns (warnings, errors)."""
    warnings = []
    errors = []
    table = model['table']

    # Check main table exists
    if table not in schema:
        errors.append(f"Table '{table}' does not exist")
        return warnings, errors

    table_cols = schema[table]

    # Check columns
    for col, expected_type in model['columns'].items():
        if col not in table_cols:
            errors.append(f"Column '{table}.{col}' does not exist")
            continue

        actual_udt = table_cols[col]
        acceptable = TYPE_COMPAT.get(expected_type, set())
        if acceptable and actual_udt not in acceptable:
            if actual_udt == 'jsonb' and expected_type == 'varchar':
                warnings.append(f"'{table}.{col}' is JSONB (TM expects VARCHAR) — needs captionDef dialectFormulaDef")
            else:
                errors.append(f"'{table}.{col}' type mismatch: TM={expected_type}, DB={actual_udt}")

    # Check dimension foreign keys and target tables
    for dim in model.get('dimensions', []):
        fk = dim['fk']
        dim_table = dim['table']
        caption_col = dim['caption_col']

        # FK column must exist in main table
        if fk not in table_cols:
            errors.append(f"FK column '{table}.{fk}' does not exist")

        # Dimension table must exist
        if dim_table not in schema:
            errors.append(f"Dimension table '{dim_table}' does not exist (FK: {fk})")
            continue

        # Caption column must exist in dimension table
        dim_cols = schema[dim_table]
        if caption_col not in dim_cols:
            errors.append(f"Caption column '{dim_table}.{caption_col}' does not exist")
        else:
            actual_udt = dim_cols[caption_col]
            if actual_udt == 'jsonb' and (dim_table, caption_col) not in JSONB_CAPTION_COLS:
                warnings.append(
                    f"'{dim_table}.{caption_col}' is JSONB but not in JSONB_CAPTION_COLS — "
                    f"may need captionDef dialectFormulaDef for ->> extraction"
                )

    return warnings, errors


def main():
    parser = argparse.ArgumentParser(description='Verify TM columns against Odoo PostgreSQL schema')
    parser.add_argument('--host', default='localhost')
    parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--db', default='odoo')
    parser.add_argument('--user', default='odoo')
    parser.add_argument('--password', default='odoo')
    parser.add_argument('--docker', default=None, help='Docker container name (uses docker exec psql)')
    args = parser.parse_args()

    print("=" * 60)
    print("  TM Schema Verification — Odoo PostgreSQL")
    print("=" * 60)
    print()

    schema = load_schema(args)
    if not schema:
        print("  ERROR: Could not load schema. Check connection.")
        sys.exit(1)

    print(f"  Loaded schema: {len(schema)} tables")
    print()

    total_errors = 0
    total_warnings = 0

    for model in MODELS:
        warnings, errors = check_model(model, schema)
        total_errors += len(errors)
        total_warnings += len(warnings)

        status = "OK" if not errors else "FAIL"
        warn_tag = f" ({len(warnings)} warnings)" if warnings else ""
        print(f"  {status:4s}  {model['name']}{warn_tag}")

        for e in errors:
            print(f"         ERROR: {e}")
        for w in warnings:
            print(f"         WARN:  {w}")

    print()
    print("=" * 60)
    if total_errors == 0:
        msg = f"ALL PASSED: {len(MODELS)} models verified"
        if total_warnings:
            msg += f" ({total_warnings} warnings)"
        print(f"  {msg}")
    else:
        print(f"  RESULT: {total_errors} errors, {total_warnings} warnings")
    print("=" * 60)
    sys.exit(1 if total_errors else 0)


if __name__ == '__main__':
    main()
