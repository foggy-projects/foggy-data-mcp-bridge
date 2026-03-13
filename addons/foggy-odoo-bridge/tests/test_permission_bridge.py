# -*- coding: utf-8 -*-
"""
Unit tests for permission_bridge domain parsing logic.

These tests cover the pure Python functions (_parse_domain_ast, _flatten_to_dsl_slices,
_leaf_to_condition) without requiring Odoo runtime.

Output format is standard DSL slice:
    [{"field": "x", "op": "=", "value": 1}, {"$or": [...]}]

Run with:  python -m pytest tests/test_permission_bridge.py -v
"""
import json
import sys
import os
import types
import importlib.util
import pytest

# ── Stub out Odoo imports so we can test pure logic ──

# Create proper module hierarchy for Odoo stubs
_odoo = types.ModuleType('odoo')
_odoo_osv = types.ModuleType('odoo.osv')
_odoo_osv_expression = types.ModuleType('odoo.osv.expression')
_odoo_osv_expression.normalize_domain = lambda d: d
_odoo_osv.expression = _odoo_osv_expression
_odoo_tools = types.ModuleType('odoo.tools')
_odoo_tools_safe_eval = types.ModuleType('odoo.tools.safe_eval')
_odoo_tools_safe_eval.safe_eval = lambda expr, ctx=None: eval(expr, ctx or {})
_odoo_tools.safe_eval = _odoo_tools_safe_eval

sys.modules['odoo'] = _odoo
sys.modules['odoo.osv'] = _odoo_osv
sys.modules['odoo.osv.expression'] = _odoo_osv_expression
sys.modules['odoo.tools'] = _odoo_tools
sys.modules['odoo.tools.safe_eval'] = _odoo_tools_safe_eval

# Load permission_bridge directly from file path (avoid Odoo package issues)
_bridge_dir = os.path.join(os.path.dirname(__file__), '..', 'foggy_mcp', 'services')

# Stub tool_registry first (permission_bridge imports from it)
_tr_spec = importlib.util.spec_from_file_location(
    'tool_registry',
    os.path.join(_bridge_dir, 'tool_registry.py'),
    submodule_search_locations=[]
)
_tr_mod = importlib.util.module_from_spec(_tr_spec)
# Manually set QM_TO_ODOO_MODEL before exec to avoid import errors
_tr_mod.QM_TO_ODOO_MODEL = {}
_tr_mod.MODEL_MAPPING = {}
# Don't exec the module — just provide the needed symbols
sys.modules['tool_registry'] = _tr_mod

# Patch the import path so permission_bridge finds tool_registry
_fake_services = types.ModuleType('foggy_mcp.services')
_fake_services.tool_registry = _tr_mod
_fake_foggy_mcp = types.ModuleType('foggy_mcp')
_fake_foggy_mcp.services = _fake_services
sys.modules['foggy_mcp'] = _fake_foggy_mcp
sys.modules['foggy_mcp.services'] = _fake_services
sys.modules['foggy_mcp.services.tool_registry'] = _tr_mod

# Now load permission_bridge
_pb_spec = importlib.util.spec_from_file_location(
    'foggy_mcp.services.permission_bridge',
    os.path.join(_bridge_dir, 'permission_bridge.py'),
)
_pb_mod = importlib.util.module_from_spec(_pb_spec)
sys.modules['foggy_mcp.services.permission_bridge'] = _pb_mod
_pb_spec.loader.exec_module(_pb_mod)

# Import the functions under test
_parse_domain_ast = _pb_mod._parse_domain_ast
_flatten_to_dsl_slices = _pb_mod._flatten_to_dsl_slices
_leaf_to_condition = _pb_mod._leaf_to_condition


# ═══════════════════════════════════════════════════════════════════
# _parse_domain_ast tests
# ═══════════════════════════════════════════════════════════════════

class TestParseDomainAst:
    """Test the Polish notation parser."""

    def test_empty_domain(self):
        assert _parse_domain_ast([]) is None
        assert _parse_domain_ast(None) is None

    def test_single_leaf(self):
        domain = [('company_id', '=', 1)]
        tree = _parse_domain_ast(domain)
        assert tree == ('LEAF', ('company_id', '=', 1))

    def test_implicit_and_two_leaves(self):
        """Two leaves without operator → implicitly AND'd (after normalize)."""
        domain = ['&', ('a', '=', 1), ('b', '=', 2)]
        tree = _parse_domain_ast(domain)
        assert tree == ('AND', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2)))

    def test_or_two_leaves(self):
        domain = ['|', ('a', '=', 1), ('b', '=', 2)]
        tree = _parse_domain_ast(domain)
        assert tree == ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2)))

    def test_not_leaf(self):
        domain = ['!', ('a', '=', 1)]
        tree = _parse_domain_ast(domain)
        assert tree == ('NOT', ('LEAF', ('a', '=', 1)))

    def test_and_with_or_subtree(self):
        """['&', '|', A, B, C] → AND(OR(A, B), C)"""
        domain = ['&', '|', ('a', '=', 1), ('b', '=', 2), ('c', '=', 3)]
        tree = _parse_domain_ast(domain)
        expected = (
            'AND',
            ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2))),
            ('LEAF', ('c', '=', 3))
        )
        assert tree == expected

    def test_nested_or(self):
        """['|', '|', A, B, C] → OR(OR(A, B), C)"""
        domain = ['|', '|', ('a', '=', 1), ('b', '=', 2), ('c', '=', 3)]
        tree = _parse_domain_ast(domain)
        expected = (
            'OR',
            ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2))),
            ('LEAF', ('c', '=', 3))
        )
        assert tree == expected

    def test_complex_three_and(self):
        """['&', '&', A, B, C] → AND(AND(A, B), C)"""
        domain = ['&', '&', ('a', '=', 1), ('b', '=', 2), ('c', '=', 3)]
        tree = _parse_domain_ast(domain)
        expected = (
            'AND',
            ('AND', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2))),
            ('LEAF', ('c', '=', 3))
        )
        assert tree == expected

    def test_not_or(self):
        """['!', '|', A, B] → NOT(OR(A, B))"""
        domain = ['!', '|', ('a', '=', 1), ('b', '=', 2)]
        tree = _parse_domain_ast(domain)
        expected = (
            'NOT',
            ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2)))
        )
        assert tree == expected


# ═══════════════════════════════════════════════════════════════════
# _leaf_to_condition tests
# ═══════════════════════════════════════════════════════════════════

class TestLeafToCondition:
    """Test leaf (field, op, value) → DSL condition dict conversion."""

    def test_simple_eq(self):
        result = _leaf_to_condition(('company_id', '=', 1))
        assert result == {'field': 'company_id', 'op': '=', 'value': 1}

    def test_in_operator(self):
        result = _leaf_to_condition(('company_id', 'in', [1, 3]))
        assert result == {'field': 'company_id', 'op': 'in', 'value': [1, 3]}

    def test_not_in_operator(self):
        result = _leaf_to_condition(('state', 'not in', ['cancel', 'draft']))
        assert result == {'field': 'state', 'op': 'not in', 'value': ['cancel', 'draft']}

    def test_null_check_eq_false(self):
        """('field', '=', False) → is null"""
        result = _leaf_to_condition(('user_id', '=', False))
        assert result == {'field': 'user_id', 'op': 'is null'}

    def test_null_check_neq_false(self):
        """('field', '!=', False) → is not null"""
        result = _leaf_to_condition(('user_id', '!=', False))
        assert result == {'field': 'user_id', 'op': 'is not null'}

    def test_relational_field_dot_id(self):
        """'company_id.id' → 'company_id'"""
        result = _leaf_to_condition(('company_id.id', '=', 5))
        assert result == {'field': 'company_id', 'op': '=', 'value': 5}

    def test_field_mapping(self):
        """'company_ids' maps to 'company_id' in QM"""
        result = _leaf_to_condition(('company_ids', 'in', [1, 2]))
        assert result == {'field': 'company_id', 'op': 'in', 'value': [1, 2]}

    def test_negate_eq(self):
        result = _leaf_to_condition(('state', '=', 'done'), negate=True)
        assert result == {'field': 'state', 'op': '!=', 'value': 'done'}

    def test_negate_in(self):
        result = _leaf_to_condition(('company_id', 'in', [1, 2]), negate=True)
        assert result == {'field': 'company_id', 'op': 'not in', 'value': [1, 2]}

    def test_negate_is_null(self):
        """NOT (field = False) → is not null"""
        result = _leaf_to_condition(('user_id', '=', False), negate=True)
        assert result == {'field': 'user_id', 'op': 'is not null'}

    def test_ilike_maps_to_like(self):
        result = _leaf_to_condition(('name', 'ilike', '%test%'))
        assert result == {'field': 'name', 'op': 'like', 'value': '%test%'}

    def test_gt_operator(self):
        result = _leaf_to_condition(('amount', '>', 1000))
        assert result == {'field': 'amount', 'op': '>', 'value': 1000}

    def test_lte_operator(self):
        result = _leaf_to_condition(('amount', '<=', 500))
        assert result == {'field': 'amount', 'op': '<=', 'value': 500}

    def test_unsupported_operator(self):
        """child_of and similar are not directly mapped."""
        result = _leaf_to_condition(('department_id', 'child_of', [3]))
        assert result is None

    def test_tuple_value_to_list(self):
        """Tuple values should be converted to list."""
        result = _leaf_to_condition(('company_id', 'in', (1, 2, 3)))
        assert result == {'field': 'company_id', 'op': 'in', 'value': [1, 2, 3]}

    def test_null_no_value_key(self):
        """Null-check conditions should not have a 'value' key."""
        result = _leaf_to_condition(('user_id', '=', False))
        assert 'value' not in result
        result2 = _leaf_to_condition(('user_id', '!=', False))
        assert 'value' not in result2


# ═══════════════════════════════════════════════════════════════════
# _flatten_to_dsl_slices tests
# ═══════════════════════════════════════════════════════════════════

class TestFlattenToDslSlices:
    """Test AST → DSL slice list flattening."""

    def test_single_leaf(self):
        tree = ('LEAF', ('company_id', '=', 1))
        slices = _flatten_to_dsl_slices(tree)
        assert slices == [{'field': 'company_id', 'op': '=', 'value': 1}]

    def test_two_and_leaves(self):
        tree = ('AND',
                ('LEAF', ('company_id', 'in', [1, 3])),
                ('LEAF', ('user_id', '=', 42)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company_id', 'op': 'in', 'value': [1, 3]}
        assert slices[1] == {'field': 'user_id', 'op': '=', 'value': 42}

    def test_or_becomes_dsl_or(self):
        """OR at top level → {"$or": [...]}"""
        tree = ('OR',
                ('LEAF', ('user_id', '=', 42)),
                ('LEAF', ('user_id', '=', False)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'user_id', 'op': '=', 'value': 42}
        assert or_children[1] == {'field': 'user_id', 'op': 'is null'}

    def test_and_with_or_subtree(self):
        """
        company_id IN [1,3] AND (user_id = 42 OR user_id IS NULL)
        → [company_id IN ..., {"$or": [user_id = 42, user_id IS NULL]}]
        """
        tree = ('AND',
                ('LEAF', ('company_id', 'in', [1, 3])),
                ('OR',
                    ('LEAF', ('user_id', '=', 42)),
                    ('LEAF', ('user_id', '=', False))))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company_id', 'op': 'in', 'value': [1, 3]}
        assert '$or' in slices[1]
        assert len(slices[1]['$or']) == 2

    def test_nested_or_flattens(self):
        """OR(OR(A, B), C) → {"$or": [A, B, C]}"""
        tree = ('OR',
                ('OR',
                    ('LEAF', ('a', '=', 1)),
                    ('LEAF', ('b', '=', 2))),
                ('LEAF', ('c', '=', 3)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert '$or' in slices[0]
        assert len(slices[0]['$or']) == 3

    def test_not_leaf(self):
        """NOT(a = 1) → a != 1"""
        tree = ('NOT', ('LEAF', ('state', '=', 'cancel')))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert slices[0] == {'field': 'state', 'op': '!=', 'value': 'cancel'}

    def test_not_or_de_morgan(self):
        """NOT(A OR B) = NOT(A) AND NOT(B) → two conditions (AND'd)"""
        tree = ('NOT',
                ('OR',
                    ('LEAF', ('state', '=', 'cancel')),
                    ('LEAF', ('state', '=', 'draft'))))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 2
        assert slices[0] == {'field': 'state', 'op': '!=', 'value': 'cancel'}
        assert slices[1] == {'field': 'state', 'op': '!=', 'value': 'draft'}

    def test_not_and_de_morgan(self):
        """NOT(A AND B) = NOT(A) OR NOT(B) → {"$or": [NOT(A), NOT(B)]}"""
        tree = ('NOT',
                ('AND',
                    ('LEAF', ('company_id', '=', 1)),
                    ('LEAF', ('user_id', '=', 42))))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'company_id', 'op': '!=', 'value': 1}
        assert or_children[1] == {'field': 'user_id', 'op': '!=', 'value': 42}

    def test_three_and_conditions(self):
        """AND(AND(A, B), C) → three conditions (flat list)"""
        tree = ('AND',
                ('AND',
                    ('LEAF', ('a', '=', 1)),
                    ('LEAF', ('b', '=', 2))),
                ('LEAF', ('c', '=', 3)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 3

    def test_and_inside_or(self):
        """(A AND B) OR C → {"$or": [{"$and": [A, B]}, C]}"""
        tree = ('OR',
                ('AND',
                    ('LEAF', ('a', '=', 1)),
                    ('LEAF', ('b', '=', 2))),
                ('LEAF', ('c', '=', 3)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        # First child is {"$and": [A, B]}
        assert '$and' in or_children[0]
        and_children = or_children[0]['$and']
        assert len(and_children) == 2
        assert and_children[0] == {'field': 'a', 'op': '=', 'value': 1}
        assert and_children[1] == {'field': 'b', 'op': '=', 'value': 2}
        # Second child is C
        assert or_children[1] == {'field': 'c', 'op': '=', 'value': 3}

    def test_single_and_inside_or_unwrapped(self):
        """(single_cond) OR C → {"$or": [single_cond, C]} (no $and wrapper)"""
        tree = ('OR',
                ('LEAF', ('a', '=', 1)),
                ('LEAF', ('c', '=', 3)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        # No $and wrapping for single conditions
        assert 'field' in or_children[0]
        assert 'field' in or_children[1]


# ═══════════════════════════════════════════════════════════════════
# Integration: domain → parse → flatten (end-to-end)
# ═══════════════════════════════════════════════════════════════════

class TestDomainEndToEnd:
    """Test the full pipeline: Odoo domain → AST → DSL slice list."""

    def _domain_to_slices(self, domain):
        """Helper: full pipeline."""
        tree = _parse_domain_ast(domain)
        if tree is None:
            return []
        return _flatten_to_dsl_slices(tree)

    def test_odoo_multi_company(self):
        """Standard Odoo multi-company rule: [('company_id', 'in', company_ids)]"""
        domain = [('company_id', 'in', [1, 3])]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'company_id', 'op': 'in', 'value': [1, 3]}]

    def test_odoo_own_records(self):
        """Standard Odoo 'own records' rule: [('user_id', '=', user.id)]"""
        domain = [('user_id', '=', 42)]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'user_id', 'op': '=', 'value': 42}]

    def test_odoo_own_or_unassigned(self):
        """
        Common Odoo pattern: ['|', ('user_id', '=', user.id), ('user_id', '=', False)]
        User sees own records OR unassigned records.
        """
        domain = ['|', ('user_id', '=', 42), ('user_id', '=', False)]
        slices = self._domain_to_slices(domain)
        assert len(slices) == 1
        assert '$or' in slices[0]
        assert slices[0]['$or'][0] == {'field': 'user_id', 'op': '=', 'value': 42}
        assert slices[0]['$or'][1] == {'field': 'user_id', 'op': 'is null'}

    def test_odoo_company_plus_own_or_unassigned(self):
        """
        Composite rule:
            company_id IN [1,3] AND (user_id = 42 OR user_id IS NULL)

        Normalized domain:
            ['&', ('company_id', 'in', [1, 3]), '|', ('user_id', '=', 42), ('user_id', '=', False)]

        Expected DSL slice:
            [
              {"field": "company_id", "op": "in", "value": [1, 3]},
              {"$or": [
                {"field": "user_id", "op": "=", "value": 42},
                {"field": "user_id", "op": "is null"}
              ]}
            ]
        """
        domain = ['&', ('company_id', 'in', [1, 3]),
                  '|', ('user_id', '=', 42), ('user_id', '=', False)]
        slices = self._domain_to_slices(domain)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company_id', 'op': 'in', 'value': [1, 3]}
        assert '$or' in slices[1]
        assert len(slices[1]['$or']) == 2

    def test_odoo_team_based(self):
        """Team-based access: [('team_id', 'in', user.sale_team_ids.ids)]"""
        domain = [('team_id', 'in', [5, 8, 12])]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'team_id', 'op': 'in', 'value': [5, 8, 12]}]

    def test_odoo_not_cancelled(self):
        """Exclude cancelled: ['!', ('state', '=', 'cancel')]"""
        domain = ['!', ('state', '=', 'cancel')]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'state', 'op': '!=', 'value': 'cancel'}]

    def test_output_matches_dsl_slice_format(self):
        """
        Verify the output JSON structure matches DSL slice format
        that can be directly appended to payload.slice.

        DSL expects: [{"field": "...", "op": "...", "value": ...}, {"$or": [...]}]
        """
        domain = ['&', ('company_id', 'in', [1, 3]),
                  '|', ('user_id', '=', 42), ('user_id', '=', False)]
        slices = self._domain_to_slices(domain)

        # Serialize to JSON and verify structure
        json_str = json.dumps(slices, separators=(',', ':'))
        parsed = json.loads(json_str)

        assert isinstance(parsed, list)
        assert len(parsed) == 2
        # First element: plain condition
        assert parsed[0]['field'] == 'company_id'
        assert parsed[0]['op'] == 'in'
        # Second element: $or group
        assert '$or' in parsed[1]
        assert parsed[1]['$or'][0]['field'] == 'user_id'
        assert parsed[1]['$or'][0]['op'] == '='
        assert parsed[1]['$or'][1]['op'] == 'is null'

    def test_complex_real_world(self):
        """
        Real-world composite:
            Company isolation AND (own team OR manager override) AND active only

        ['&', '&',
            ('company_id', 'in', [1, 3]),
            '|', ('team_id', '=', 5), ('user_id', '=', 42),
            ('active', '=', True)]

        Expected DSL slice:
            [
              {"field": "company_id", "op": "in", "value": [1, 3]},
              {"$or": [
                {"field": "team_id", "op": "=", "value": 5},
                {"field": "user_id", "op": "=", "value": 42}
              ]},
              {"field": "active", "op": "=", "value": true}
            ]
        """
        domain = ['&', '&',
                  ('company_id', 'in', [1, 3]),
                  '|', ('team_id', '=', 5), ('user_id', '=', 42),
                  ('active', '=', True)]
        slices = self._domain_to_slices(domain)

        # 3 items: company_id condition, $or group, active condition
        assert len(slices) == 3
        field_names = [s.get('field') for s in slices if 'field' in s]
        assert 'company_id' in field_names
        assert 'active' in field_names
        # One $or group
        or_items = [s for s in slices if '$or' in s]
        assert len(or_items) == 1
        assert len(or_items[0]['$or']) == 2

    def test_payload_slice_injection(self):
        """
        Simulate the actual use case: inject permission slices into payload.slice.
        Existing user filters should be preserved.
        """
        # Simulate existing payload from AI client
        payload = {
            'columns': ['order_date', 'amount_total'],
            'slice': [
                {'field': 'order_date', 'op': '>=', 'value': '2024-01-01'},
            ]
        }

        # Compute permission slices
        domain = ['&', ('company_id', 'in', [1, 3]),
                  '|', ('user_id', '=', 42), ('user_id', '=', False)]
        perm_slices = self._domain_to_slices(domain)

        # Inject (same logic as mcp_controller.py)
        payload['slice'].extend(perm_slices)

        # Verify combined result
        assert len(payload['slice']) == 3
        # Original filter preserved
        assert payload['slice'][0] == {'field': 'order_date', 'op': '>=', 'value': '2024-01-01'}
        # Permission conditions appended
        assert payload['slice'][1] == {'field': 'company_id', 'op': 'in', 'value': [1, 3]}
        assert '$or' in payload['slice'][2]
