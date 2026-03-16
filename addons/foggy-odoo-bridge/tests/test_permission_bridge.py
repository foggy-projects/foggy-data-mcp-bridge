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
# Pre-wrapped modules (Odoo 17 compatibility)
_odoo_tools_safe_eval.time = __import__('time')
_odoo_tools_safe_eval.datetime = __import__('datetime')
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
_expand_hierarchy_operators = _pb_mod._expand_hierarchy_operators
_normalize_hierarchy_value = _pb_mod._normalize_hierarchy_value
HIERARCHY_FIELD_MAP = _pb_mod.HIERARCHY_FIELD_MAP
DIRECT_FIELD_MAP = _pb_mod.DIRECT_FIELD_MAP
_build_field_types = _pb_mod._build_field_types
FieldContext = _pb_mod.FieldContext


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
        """Two leaves without operator -> implicitly AND'd (after normalize)."""
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
        """['&', '|', A, B, C] -> AND(OR(A, B), C)"""
        domain = ['&', '|', ('a', '=', 1), ('b', '=', 2), ('c', '=', 3)]
        tree = _parse_domain_ast(domain)
        expected = (
            'AND',
            ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2))),
            ('LEAF', ('c', '=', 3))
        )
        assert tree == expected

    def test_nested_or(self):
        """['|', '|', A, B, C] -> OR(OR(A, B), C)"""
        domain = ['|', '|', ('a', '=', 1), ('b', '=', 2), ('c', '=', 3)]
        tree = _parse_domain_ast(domain)
        expected = (
            'OR',
            ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2))),
            ('LEAF', ('c', '=', 3))
        )
        assert tree == expected

    def test_complex_three_and(self):
        """['&', '&', A, B, C] -> AND(AND(A, B), C)"""
        domain = ['&', '&', ('a', '=', 1), ('b', '=', 2), ('c', '=', 3)]
        tree = _parse_domain_ast(domain)
        expected = (
            'AND',
            ('AND', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2))),
            ('LEAF', ('c', '=', 3))
        )
        assert tree == expected

    def test_not_or(self):
        """['!', '|', A, B] -> NOT(OR(A, B))"""
        domain = ['!', '|', ('a', '=', 1), ('b', '=', 2)]
        tree = _parse_domain_ast(domain)
        expected = (
            'NOT',
            ('OR', ('LEAF', ('a', '=', 1)), ('LEAF', ('b', '=', 2)))
        )
        assert tree == expected

    def test_tautology_literal(self):
        """(1, '=', 1) is parsed as a valid LEAF."""
        domain = [(1, '=', 1)]
        tree = _parse_domain_ast(domain)
        assert tree == ('LEAF', (1, '=', 1))


# ═══════════════════════════════════════════════════════════════════
# _leaf_to_condition tests
# ═══════════════════════════════════════════════════════════════════

class TestLeafToCondition:
    """Test leaf (field, op, value) -> DSL condition dict conversion."""

    def test_simple_eq(self):
        result = _leaf_to_condition(('company_id', '=', 1))
        assert result == {'field': 'company$id', 'op': '=', 'value': 1}

    def test_in_operator(self):
        result = _leaf_to_condition(('company_id', 'in', [1, 3]))
        assert result == {'field': 'company$id', 'op': 'in', 'value': [1, 3]}

    def test_not_in_operator(self):
        result = _leaf_to_condition(('state', 'not in', ['cancel', 'draft']))
        assert result == {'field': 'state', 'op': 'not in', 'value': ['cancel', 'draft']}

    def test_null_check_eq_false(self):
        """('field', '=', False) -> is null"""
        result = _leaf_to_condition(('user_id', '=', False))
        assert result == {'field': 'salesperson$id', 'op': 'is null'}

    def test_null_check_neq_false(self):
        """('field', '!=', False) -> is not null"""
        result = _leaf_to_condition(('user_id', '!=', False))
        assert result == {'field': 'salesperson$id', 'op': 'is not null'}

    def test_relational_field_dot_id(self):
        """'company_id.id' -> 'company$id'"""
        result = _leaf_to_condition(('company_id.id', '=', 5))
        assert result == {'field': 'company$id', 'op': '=', 'value': 5}

    def test_field_mapping(self):
        """'company_ids' maps to 'company$id' in QM"""
        result = _leaf_to_condition(('company_ids', 'in', [1, 2]))
        assert result == {'field': 'company$id', 'op': 'in', 'value': [1, 2]}

    def test_user_id_maps_to_salesperson(self):
        """'user_id' maps to 'salesperson$id' in QM"""
        result = _leaf_to_condition(('user_id', '=', 42))
        assert result == {'field': 'salesperson$id', 'op': '=', 'value': 42}

    def test_team_id_maps_to_salesTeam(self):
        """'team_id' maps to 'salesTeam$id' in QM"""
        result = _leaf_to_condition(('team_id', 'in', [5, 8]))
        assert result == {'field': 'salesTeam$id', 'op': 'in', 'value': [5, 8]}

    def test_negate_eq(self):
        result = _leaf_to_condition(('state', '=', 'done'), negate=True)
        assert result == {'field': 'state', 'op': '!=', 'value': 'done'}

    def test_negate_in(self):
        result = _leaf_to_condition(('company_id', 'in', [1, 2]), negate=True)
        assert result == {'field': 'company$id', 'op': 'not in', 'value': [1, 2]}

    def test_negate_is_null(self):
        """NOT (field = False) -> is not null"""
        result = _leaf_to_condition(('user_id', '=', False), negate=True)
        assert result == {'field': 'salesperson$id', 'op': 'is not null'}

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
        """Operators not in DOMAIN_OP_MAP return None."""
        result = _leaf_to_condition(('department_id', 'child_of', [3]))
        assert result is None

    def test_selfAndDescendantsOf_passes_through(self):
        """selfAndDescendantsOf is a valid operator in DOMAIN_OP_MAP."""
        result = _leaf_to_condition(('company$id', 'selfAndDescendantsOf', 1))
        assert result == {'field': 'company$id', 'op': 'selfAndDescendantsOf', 'value': 1}

    def test_selfAndAncestorsOf_passes_through(self):
        """selfAndAncestorsOf is a valid operator in DOMAIN_OP_MAP."""
        result = _leaf_to_condition(('company$id', 'selfAndAncestorsOf', 2))
        assert result == {'field': 'company$id', 'op': 'selfAndAncestorsOf', 'value': 2}

    def test_tuple_value_to_list(self):
        """Tuple values should be converted to list."""
        result = _leaf_to_condition(('company_id', 'in', (1, 2, 3)))
        assert result == {'field': 'company$id', 'op': 'in', 'value': [1, 2, 3]}

    def test_null_no_value_key(self):
        """Null-check conditions should not have a 'value' key."""
        result = _leaf_to_condition(('user_id', '=', False))
        assert 'value' not in result
        result2 = _leaf_to_condition(('user_id', '!=', False))
        assert 'value' not in result2

    def test_tautology_returns_none(self):
        """(1, '=', 1) is a tautology (always true) -> returns None."""
        result = _leaf_to_condition((1, '=', 1))
        assert result is None

    def test_contradiction_returns_impossible(self):
        """(0, '=', 1) is a contradiction (always false) -> returns impossible condition."""
        result = _leaf_to_condition((0, '=', 1))
        assert result == {'field': 'id', 'op': '=', 'value': -1}

    def test_unmapped_field_passes_through(self):
        """Fields not in DIRECT_FIELD_MAP are passed through as-is."""
        result = _leaf_to_condition(('custom_field', '=', 'test'))
        assert result == {'field': 'custom_field', 'op': '=', 'value': 'test'}


# ═══════════════════════════════════════════════════════════════════
# _flatten_to_dsl_slices tests
# ═══════════════════════════════════════════════════════════════════

class TestFlattenToDslSlices:
    """Test AST -> DSL slice list flattening."""

    def test_single_leaf(self):
        tree = ('LEAF', ('company_id', '=', 1))
        slices = _flatten_to_dsl_slices(tree)
        assert slices == [{'field': 'company$id', 'op': '=', 'value': 1}]

    def test_two_and_leaves(self):
        tree = ('AND',
                ('LEAF', ('company_id', 'in', [1, 3])),
                ('LEAF', ('user_id', '=', 42)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company$id', 'op': 'in', 'value': [1, 3]}
        assert slices[1] == {'field': 'salesperson$id', 'op': '=', 'value': 42}

    def test_or_becomes_dsl_or(self):
        """OR at top level -> {"$or": [...]}"""
        tree = ('OR',
                ('LEAF', ('user_id', '=', 42)),
                ('LEAF', ('user_id', '=', False)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'salesperson$id', 'op': '=', 'value': 42}
        assert or_children[1] == {'field': 'salesperson$id', 'op': 'is null'}

    def test_and_with_or_subtree(self):
        """
        company_id IN [1,3] AND (user_id = 42 OR user_id IS NULL)
        -> [company$id IN ..., {"$or": [salesperson$id = 42, salesperson$id IS NULL]}]
        """
        tree = ('AND',
                ('LEAF', ('company_id', 'in', [1, 3])),
                ('OR',
                    ('LEAF', ('user_id', '=', 42)),
                    ('LEAF', ('user_id', '=', False))))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company$id', 'op': 'in', 'value': [1, 3]}
        assert '$or' in slices[1]
        assert len(slices[1]['$or']) == 2

    def test_nested_or_flattens(self):
        """OR(OR(A, B), C) -> {"$or": [A, B, C]}"""
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
        """NOT(a = 1) -> a != 1"""
        tree = ('NOT', ('LEAF', ('state', '=', 'cancel')))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert slices[0] == {'field': 'state', 'op': '!=', 'value': 'cancel'}

    def test_not_or_de_morgan(self):
        """NOT(A OR B) = NOT(A) AND NOT(B) -> two conditions (AND'd)"""
        tree = ('NOT',
                ('OR',
                    ('LEAF', ('state', '=', 'cancel')),
                    ('LEAF', ('state', '=', 'draft'))))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 2
        assert slices[0] == {'field': 'state', 'op': '!=', 'value': 'cancel'}
        assert slices[1] == {'field': 'state', 'op': '!=', 'value': 'draft'}

    def test_not_and_de_morgan(self):
        """NOT(A AND B) = NOT(A) OR NOT(B) -> {"$or": [NOT(A), NOT(B)]}"""
        tree = ('NOT',
                ('AND',
                    ('LEAF', ('company_id', '=', 1)),
                    ('LEAF', ('user_id', '=', 42))))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'company$id', 'op': '!=', 'value': 1}
        assert or_children[1] == {'field': 'salesperson$id', 'op': '!=', 'value': 42}

    def test_three_and_conditions(self):
        """AND(AND(A, B), C) -> three conditions (flat list)"""
        tree = ('AND',
                ('AND',
                    ('LEAF', ('a', '=', 1)),
                    ('LEAF', ('b', '=', 2))),
                ('LEAF', ('c', '=', 3)))
        slices = _flatten_to_dsl_slices(tree)
        assert len(slices) == 3

    def test_and_inside_or(self):
        """(A AND B) OR C -> {"$or": [{"$and": [A, B]}, C]}"""
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
        """(single_cond) OR C -> {"$or": [single_cond, C]} (no $and wrapper)"""
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

    def test_tautology_leaf_produces_no_slice(self):
        """(1, '=', 1) tautology produces empty slice list."""
        tree = ('LEAF', (1, '=', 1))
        slices = _flatten_to_dsl_slices(tree)
        assert slices == []


# ═══════════════════════════════════════════════════════════════════
# Integration: domain -> parse -> flatten (end-to-end)
# ═══════════════════════════════════════════════════════════════════

class TestDomainEndToEnd:
    """Test the full pipeline: Odoo domain -> AST -> DSL slice list."""

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
        assert slices == [{'field': 'company$id', 'op': 'in', 'value': [1, 3]}]

    def test_odoo_own_records(self):
        """Standard Odoo 'own records' rule: [('user_id', '=', user.id)]"""
        domain = [('user_id', '=', 42)]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'salesperson$id', 'op': '=', 'value': 42}]

    def test_odoo_own_or_unassigned(self):
        """
        Common Odoo pattern: ['|', ('user_id', '=', user.id), ('user_id', '=', False)]
        User sees own records OR unassigned records.
        """
        domain = ['|', ('user_id', '=', 42), ('user_id', '=', False)]
        slices = self._domain_to_slices(domain)
        assert len(slices) == 1
        assert '$or' in slices[0]
        assert slices[0]['$or'][0] == {'field': 'salesperson$id', 'op': '=', 'value': 42}
        assert slices[0]['$or'][1] == {'field': 'salesperson$id', 'op': 'is null'}

    def test_odoo_company_plus_own_or_unassigned(self):
        """
        Composite rule:
            company_id IN [1,3] AND (user_id = 42 OR user_id IS NULL)

        Normalized domain:
            ['&', ('company_id', 'in', [1, 3]), '|', ('user_id', '=', 42), ('user_id', '=', False)]

        Expected DSL slice:
            [
              {"field": "company$id", "op": "in", "value": [1, 3]},
              {"$or": [
                {"field": "salesperson$id", "op": "=", "value": 42},
                {"field": "salesperson$id", "op": "is null"}
              ]}
            ]
        """
        domain = ['&', ('company_id', 'in', [1, 3]),
                  '|', ('user_id', '=', 42), ('user_id', '=', False)]
        slices = self._domain_to_slices(domain)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company$id', 'op': 'in', 'value': [1, 3]}
        assert '$or' in slices[1]
        assert len(slices[1]['$or']) == 2

    def test_odoo_team_based(self):
        """Team-based access: [('team_id', 'in', user.sale_team_ids.ids)]"""
        domain = [('team_id', 'in', [5, 8, 12])]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'salesTeam$id', 'op': 'in', 'value': [5, 8, 12]}]

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
        assert parsed[0]['field'] == 'company$id'
        assert parsed[0]['op'] == 'in'
        # Second element: $or group
        assert '$or' in parsed[1]
        assert parsed[1]['$or'][0]['field'] == 'salesperson$id'
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
              {"field": "company$id", "op": "in", "value": [1, 3]},
              {"$or": [
                {"field": "salesTeam$id", "op": "=", "value": 5},
                {"field": "salesperson$id", "op": "=", "value": 42}
              ]},
              {"field": "active", "op": "=", "value": true}
            ]
        """
        domain = ['&', '&',
                  ('company_id', 'in', [1, 3]),
                  '|', ('team_id', '=', 5), ('user_id', '=', 42),
                  ('active', '=', True)]
        slices = self._domain_to_slices(domain)

        # 3 items: company$id condition, $or group, active condition
        assert len(slices) == 3
        field_names = [s.get('field') for s in slices if 'field' in s]
        assert 'company$id' in field_names
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
        assert payload['slice'][1] == {'field': 'company$id', 'op': 'in', 'value': [1, 3]}
        assert '$or' in payload['slice'][2]

    def test_hierarchy_child_of_end_to_end(self):
        """
        End-to-end: child_of with mapped field -> selfAndDescendantsOf in DSL slice.

        Simulates Odoo ir.rule: ('company_id', 'child_of', [1])
        After hierarchy expansion: ('company$id', 'selfAndDescendantsOf', 1)
        Final DSL slice: {"field": "company$id", "op": "selfAndDescendantsOf", "value": 1}
        """
        # After _expand_hierarchy_operators, child_of becomes selfAndDescendantsOf
        domain = [('company$id', 'selfAndDescendantsOf', 1)]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'company$id', 'op': 'selfAndDescendantsOf', 'value': 1}]

    def test_hierarchy_with_company_filter(self):
        """
        Real-world scenario: company hierarchy + user filter.

        ir.rule: ['&', ('company_id', 'child_of', [user.company_id]),
                       ('user_id', '=', user.id)]

        After expansion:
            ['&', ('company$id', 'selfAndDescendantsOf', 1), ('user_id', '=', 42)]
        """
        domain = ['&', ('company$id', 'selfAndDescendantsOf', 1), ('user_id', '=', 42)]
        slices = self._domain_to_slices(domain)
        assert len(slices) == 2
        assert slices[0] == {'field': 'company$id', 'op': 'selfAndDescendantsOf', 'value': 1}
        assert slices[1] == {'field': 'salesperson$id', 'op': '=', 'value': 42}

    def test_tautology_domain_produces_no_slices(self):
        """[(1, '=', 1)] (allow all) -> empty slice list."""
        domain = [(1, '=', 1)]
        slices = self._domain_to_slices(domain)
        assert slices == []


# ═══════════════════════════════════════════════════════════════════
# Hierarchy expansion tests (child_of/parent_of -> closure table operators)
# ═══════════════════════════════════════════════════════════════════

class TestExpandHierarchyOperators:
    """Test _expand_hierarchy_operators: child_of/parent_of -> selfAndDescendantsOf/selfAndAncestorsOf."""

    def test_child_of_mapped_field(self):
        """child_of on mapped field -> selfAndDescendantsOf with dimension field."""
        domain = [('company_id', 'child_of', [1])]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert len(result) == 1
        assert result[0] == ('company$id', 'selfAndDescendantsOf', 1)

    def test_parent_of_mapped_field(self):
        """parent_of on mapped field -> selfAndAncestorsOf with dimension field."""
        domain = [('company_id', 'parent_of', [2])]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert len(result) == 1
        assert result[0] == ('company$id', 'selfAndAncestorsOf', 2)

    def test_child_of_department(self):
        """department_id child_of -> department$id selfAndDescendantsOf."""
        domain = [('department_id', 'child_of', [3])]
        result = _expand_hierarchy_operators(None, domain, 'hr.employee')
        assert len(result) == 1
        assert result[0] == ('department$id', 'selfAndDescendantsOf', 3)

    def test_child_of_parent_id(self):
        """parent_id child_of (employee manager hierarchy) -> parent$id selfAndDescendantsOf."""
        domain = [('parent_id', 'child_of', [1])]
        result = _expand_hierarchy_operators(None, domain, 'hr.employee')
        assert len(result) == 1
        assert result[0] == ('parent$id', 'selfAndDescendantsOf', 1)

    def test_child_of_company_ids(self):
        """company_ids also maps to company$id."""
        domain = [('company_ids', 'child_of', [1])]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert len(result) == 1
        assert result[0] == ('company$id', 'selfAndDescendantsOf', 1)

    def test_child_of_scalar_value(self):
        """Scalar value (not list) is preserved as-is."""
        domain = [('company_id', 'child_of', 1)]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert len(result) == 1
        assert result[0] == ('company$id', 'selfAndDescendantsOf', 1)

    def test_child_of_multi_value(self):
        """Multi-element list is preserved."""
        domain = [('company_id', 'child_of', [1, 2])]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert len(result) == 1
        assert result[0] == ('company$id', 'selfAndDescendantsOf', [1, 2])

    def test_non_hierarchy_operators_pass_through(self):
        """Non-hierarchy operators are not modified."""
        domain = ['&', ('company_id', 'in', [1, 3]), ('user_id', '=', 42)]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert result == ['&', ('company_id', 'in', [1, 3]), ('user_id', '=', 42)]

    def test_mixed_hierarchy_and_normal(self):
        """Mix of hierarchy and normal operators in same domain."""
        domain = ['&', ('company_id', 'child_of', [1]), ('user_id', '=', 42)]
        result = _expand_hierarchy_operators(None, domain, 'sale.order')
        assert len(result) == 3
        assert result[0] == '&'
        assert result[1] == ('company$id', 'selfAndDescendantsOf', 1)
        assert result[2] == ('user_id', '=', 42)

    def test_hierarchy_field_map_coverage(self):
        """All entries in HIERARCHY_FIELD_MAP are correctly defined."""
        assert 'company_id' in HIERARCHY_FIELD_MAP
        assert 'company_ids' in HIERARCHY_FIELD_MAP
        assert 'department_id' in HIERARCHY_FIELD_MAP
        assert 'parent_id' in HIERARCHY_FIELD_MAP
        # All map to dimension$id format
        for field, dim_field in HIERARCHY_FIELD_MAP.items():
            assert '$id' in dim_field, f"{field} should map to a dimension$id field"


class TestNormalizeHierarchyValue:
    """Test _normalize_hierarchy_value helper."""

    def test_single_int(self):
        assert _normalize_hierarchy_value(1) == 1

    def test_single_element_list(self):
        """Single-element list unwrapped to plain int."""
        assert _normalize_hierarchy_value([5]) == 5

    def test_multi_element_list(self):
        assert _normalize_hierarchy_value([1, 2, 3]) == [1, 2, 3]

    def test_tuple_to_list(self):
        assert _normalize_hierarchy_value((1, 2)) == [1, 2]

    def test_single_tuple(self):
        assert _normalize_hierarchy_value((5,)) == 5

    def test_empty_list(self):
        assert _normalize_hierarchy_value([]) == []


# ═══════════════════════════════════════════════════════════════════
# DIRECT_FIELD_MAP coverage tests
# ═══════════════════════════════════════════════════════════════════

class TestDirectFieldMapCoverage:
    """Verify DIRECT_FIELD_MAP covers all fields used by ir.rule domains."""

    def test_company_fields(self):
        """company_id / company_ids → company$id."""
        assert DIRECT_FIELD_MAP['company_id'] == 'company$id'
        assert DIRECT_FIELD_MAP['company_ids'] == 'company$id'

    def test_user_id_field(self):
        """user_id → salesperson$id (sale.order FK)."""
        assert DIRECT_FIELD_MAP['user_id'] == 'salesperson$id'

    def test_invoice_user_id_field(self):
        """invoice_user_id → salesperson$id (account.move FK)."""
        assert DIRECT_FIELD_MAP['invoice_user_id'] == 'salesperson$id'

    def test_partner_id_field(self):
        assert DIRECT_FIELD_MAP['partner_id'] == 'partner$id'

    def test_team_id_field(self):
        assert DIRECT_FIELD_MAP['team_id'] == 'salesTeam$id'

    def test_department_id_field(self):
        assert DIRECT_FIELD_MAP['department_id'] == 'department$id'

    def test_journal_id_field(self):
        assert DIRECT_FIELD_MAP['journal_id'] == 'journal$id'

    def test_picking_type_id_field(self):
        assert DIRECT_FIELD_MAP['picking_type_id'] == 'pickingType$id'

    def test_move_type_field(self):
        """move_type → moveType (account.move property)."""
        assert DIRECT_FIELD_MAP['move_type'] == 'moveType'

    def test_state_field(self):
        """state → state (common property, explicit mapping)."""
        assert DIRECT_FIELD_MAP['state'] == 'state'


# ═══════════════════════════════════════════════════════════════════
# False in IN/NOT IN value lists
# ═══════════════════════════════════════════════════════════════════

class TestFalseInValueLists:
    """Test handling of False/None in 'in'/'not in' value lists.

    Odoo pattern: ('company_id', 'in', company_ids + [False])
    False in a Many2one IN list means NULL → should produce $or with is null.
    """

    def test_in_with_false_produces_or(self):
        """('company_id', 'in', [1, 2, False]) → $or: [IN [1,2], IS NULL]."""
        result = _leaf_to_condition(('company_id', 'in', [1, 2, False]))
        assert '$or' in result
        or_children = result['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'company$id', 'op': 'in', 'value': [1, 2]}
        assert or_children[1] == {'field': 'company$id', 'op': 'is null'}

    def test_in_with_none_produces_or(self):
        """None in value list is treated same as False."""
        result = _leaf_to_condition(('company_id', 'in', [1, None]))
        assert '$or' in result
        assert result['$or'][0] == {'field': 'company$id', 'op': 'in', 'value': [1]}
        assert result['$or'][1] == {'field': 'company$id', 'op': 'is null'}

    def test_in_with_only_false(self):
        """('company_id', 'in', [False]) → is null."""
        result = _leaf_to_condition(('company_id', 'in', [False]))
        assert result == {'field': 'company$id', 'op': 'is null'}

    def test_not_in_with_false_produces_and(self):
        """('company_id', 'not in', [1, False]) → $and: [NOT IN [1], IS NOT NULL]."""
        result = _leaf_to_condition(('company_id', 'not in', [1, False]))
        assert '$and' in result
        and_children = result['$and']
        assert len(and_children) == 2
        assert and_children[0] == {'field': 'company$id', 'op': 'not in', 'value': [1]}
        assert and_children[1] == {'field': 'company$id', 'op': 'is not null'}

    def test_in_without_false_unchanged(self):
        """Normal IN list (no False) is unchanged."""
        result = _leaf_to_condition(('company_id', 'in', [1, 2]))
        assert result == {'field': 'company$id', 'op': 'in', 'value': [1, 2]}

    def test_in_with_false_single_value(self):
        """('company_id', 'in', [3, False]) → $or with single-element list."""
        result = _leaf_to_condition(('company_id', 'in', [3, False]))
        assert '$or' in result
        assert result['$or'][0] == {'field': 'company$id', 'op': 'in', 'value': [3]}
        assert result['$or'][1] == {'field': 'company$id', 'op': 'is null'}


# ═══════════════════════════════════════════════════════════════════
# Model-specific ir.rule domain tests
# ═══════════════════════════════════════════════════════════════════

class TestPurchaseOrderDomains:
    """Test purchase.order ir.rule domain patterns.

    Actual Odoo ir.rule for purchase.order:
      Global: [('company_id', 'in', company_ids)]
    """

    def _domain_to_slices(self, domain):
        tree = _parse_domain_ast(domain)
        return _flatten_to_dsl_slices(tree) if tree else []

    def test_company_isolation(self):
        """Global rule: [('company_id', 'in', [1])]."""
        slices = self._domain_to_slices([('company_id', 'in', [1])])
        assert slices == [{'field': 'company$id', 'op': 'in', 'value': [1]}]

    def test_multi_company(self):
        """User in multiple companies: [('company_id', 'in', [1, 2])]."""
        slices = self._domain_to_slices([('company_id', 'in', [1, 2])])
        assert slices == [{'field': 'company$id', 'op': 'in', 'value': [1, 2]}]


class TestAccountMoveDomains:
    """Test account.move ir.rule domain patterns.

    Actual Odoo ir.rules for account.move:
      Global: [('company_id', 'in', company_ids)]
      Group (Billing): [(1, '=', 1)]  — tautology
      Group (Own Docs): [('move_type', 'in', ('out_invoice', 'out_refund')),
                          '|', ('invoice_user_id', '=', user.id),
                               ('invoice_user_id', '=', False)]
      Group (All Docs): [('move_type', 'in', ('out_invoice', 'out_refund'))]
      Group (Purchase): [('move_type', 'in', ('in_invoice', 'in_refund', 'in_receipt'))]
    """

    def _domain_to_slices(self, domain):
        tree = _parse_domain_ast(domain)
        return _flatten_to_dsl_slices(tree) if tree else []

    def test_company_isolation(self):
        """Global rule: company_id IN company_ids."""
        slices = self._domain_to_slices([('company_id', 'in', [1, 2])])
        assert slices == [{'field': 'company$id', 'op': 'in', 'value': [1, 2]}]

    def test_billing_tautology(self):
        """Billing group rule: [(1, '=', 1)] → empty (allow all)."""
        slices = self._domain_to_slices([(1, '=', 1)])
        assert slices == []

    def test_all_invoices(self):
        """All Documents group: move_type IN customer invoice types."""
        domain = [('move_type', 'in', ('out_invoice', 'out_refund'))]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'moveType', 'op': 'in',
                           'value': ['out_invoice', 'out_refund']}]

    def test_personal_invoices(self):
        """Own Documents group: move_type filter AND (own OR unassigned).

        Domain: ['&', ('move_type', 'in', ('out_invoice', 'out_refund')),
                      '|', ('invoice_user_id', '=', 6), ('invoice_user_id', '=', False)]
        """
        domain = ['&', ('move_type', 'in', ('out_invoice', 'out_refund')),
                  '|', ('invoice_user_id', '=', 6), ('invoice_user_id', '=', False)]
        slices = self._domain_to_slices(domain)

        assert len(slices) == 2
        # First: move_type filter
        assert slices[0] == {'field': 'moveType', 'op': 'in',
                             'value': ['out_invoice', 'out_refund']}
        # Second: user OR null
        assert '$or' in slices[1]
        or_children = slices[1]['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'salesperson$id', 'op': '=', 'value': 6}
        assert or_children[1] == {'field': 'salesperson$id', 'op': 'is null'}

    def test_purchase_invoices(self):
        """Purchase User group: move_type IN vendor bill types."""
        domain = [('move_type', 'in', ('in_invoice', 'in_refund', 'in_receipt'))]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'moveType', 'op': 'in',
                           'value': ['in_invoice', 'in_refund', 'in_receipt']}]


class TestStockPickingDomains:
    """Test stock.picking ir.rule domain patterns.

    Actual Odoo ir.rule for stock.picking:
      Global: [('company_id', 'in', company_ids)]
    """

    def _domain_to_slices(self, domain):
        tree = _parse_domain_ast(domain)
        return _flatten_to_dsl_slices(tree) if tree else []

    def test_company_isolation(self):
        """Global rule: company_id IN company_ids."""
        slices = self._domain_to_slices([('company_id', 'in', [1])])
        assert slices == [{'field': 'company$id', 'op': 'in', 'value': [1]}]

    def test_multi_company(self):
        """Multi-company user sees pickings from all companies."""
        slices = self._domain_to_slices([('company_id', 'in', [1, 2])])
        assert slices == [{'field': 'company$id', 'op': 'in', 'value': [1, 2]}]


class TestHrEmployeeDomains:
    """Test hr.employee ir.rule domain patterns.

    Actual Odoo ir.rule for hr.employee:
      Global: [('company_id', 'in', company_ids + [False])]
    """

    def _domain_to_slices(self, domain):
        tree = _parse_domain_ast(domain)
        return _flatten_to_dsl_slices(tree) if tree else []

    def test_company_isolation_with_false(self):
        """Global rule: company_id IN company_ids + [False].

        The + [False] allows employees without a company (company_id IS NULL).
        Should produce: company$id IN [1] OR company$id IS NULL.
        """
        domain = [('company_id', 'in', [1, False])]
        slices = self._domain_to_slices(domain)

        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 2
        assert or_children[0] == {'field': 'company$id', 'op': 'in', 'value': [1]}
        assert or_children[1] == {'field': 'company$id', 'op': 'is null'}

    def test_multi_company_with_false(self):
        """Multi-company user: company_id IN [1, 2, False]."""
        domain = [('company_id', 'in', [1, 2, False])]
        slices = self._domain_to_slices(domain)

        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert or_children[0] == {'field': 'company$id', 'op': 'in', 'value': [1, 2]}
        assert or_children[1] == {'field': 'company$id', 'op': 'is null'}


class TestResPartnerDomains:
    """Test res.partner ir.rule domain patterns.

    Actual Odoo ir.rule for res.partner:
      Global: ['|', '|', ('partner_share', '=', False),
                         ('company_id', 'parent_of', company_ids),
                         ('company_id', '=', False)]

    NOTE: partner_share is a boolean field. With field_types from Odoo ORM,
    ('partner_share', '=', False) correctly produces op='=' value=false,
    not op='is null' (which is the default for Many2one fields).
    """

    # Simulated field types for res.partner (from Odoo ORM introspection)
    PARTNER_FIELD_TYPES = {
        'partner_share': 'boolean',
        'is_company': 'boolean',
        'active': 'boolean',
        'company_id': 'many2one',
    }

    def _domain_to_slices(self, domain, field_types=None):
        tree = _parse_domain_ast(domain)
        ctx = FieldContext(field_types=field_types) if field_types else None
        return _flatten_to_dsl_slices(tree, ctx=ctx) if tree else []

    def test_company_null_check(self):
        """company_id = False → company$id IS NULL."""
        slices = self._domain_to_slices([('company_id', '=', False)])
        assert slices == [{'field': 'company$id', 'op': 'is null'}]

    def test_partner_share_false(self):
        """partner_share = False → boolean field, treated as = false (not IS NULL).

        With field_types={'partner_share': 'boolean'}, ('partner_share', '=', False)
        correctly produces op='=' value=false, not op='is null'.
        """
        slices = self._domain_to_slices(
            [('partner_share', '=', False)],
            field_types=self.PARTNER_FIELD_TYPES)
        assert slices == [{'field': 'partner_share', 'op': '=', 'value': False}]

    def test_partner_hierarchy_parent_of(self):
        """company_id parent_of → selfAndAncestorsOf (after hierarchy expansion)."""
        # After _expand_hierarchy_operators, parent_of becomes selfAndAncestorsOf
        domain = [('company$id', 'selfAndAncestorsOf', [1, 2])]
        slices = self._domain_to_slices(domain)
        assert slices == [{'field': 'company$id', 'op': 'selfAndAncestorsOf',
                           'value': [1, 2]}]

    def test_full_partner_global_rule(self):
        """Full global rule after hierarchy expansion:

        ['|', '|', ('partner_share', '=', False),
                   ('company$id', 'selfAndAncestorsOf', [1, 2]),
                   ('company_id', '=', False)]

        Expected: {"$or": [partner_share = false, company$id selfAndAncestorsOf, company$id IS NULL]}
        """
        # Simulating after _expand_hierarchy_operators has run:
        domain = ['|', '|', ('partner_share', '=', False),
                  ('company$id', 'selfAndAncestorsOf', [1, 2]),
                  ('company_id', '=', False)]
        slices = self._domain_to_slices(domain, field_types=self.PARTNER_FIELD_TYPES)

        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert len(or_children) == 3
        # partner_share is boolean (from field_types) → = false, not IS NULL
        assert or_children[0] == {'field': 'partner_share', 'op': '=', 'value': False}
        assert or_children[1] == {'field': 'company$id', 'op': 'selfAndAncestorsOf',
                                  'value': [1, 2]}
        assert or_children[2] == {'field': 'company$id', 'op': 'is null'}

    def test_hierarchy_expansion_parent_of(self):
        """Verify _expand_hierarchy_operators converts parent_of correctly."""
        domain = [('company_id', 'parent_of', [1, 2])]
        result = _expand_hierarchy_operators(None, domain, 'res.partner')
        assert len(result) == 1
        assert result[0] == ('company$id', 'selfAndAncestorsOf', [1, 2])


# ═══════════════════════════════════════════════════════════════════
# Cross-model field mapping integration tests
# ═══════════════════════════════════════════════════════════════════

class TestCrossModelFieldMapping:
    """Verify field mappings work correctly across different model contexts.

    Different models use the same Odoo field names but map to different
    QM dimensions. The DIRECT_FIELD_MAP is global, so we test that the
    most common mappings work for the fields used in ir.rule domains.
    """

    def test_invoice_user_id_maps_to_salesperson(self):
        """account.move: invoice_user_id → salesperson$id."""
        result = _leaf_to_condition(('invoice_user_id', '=', 6))
        assert result == {'field': 'salesperson$id', 'op': '=', 'value': 6}

    def test_invoice_user_id_null_check(self):
        """account.move: invoice_user_id = False → salesperson$id IS NULL."""
        result = _leaf_to_condition(('invoice_user_id', '=', False))
        assert result == {'field': 'salesperson$id', 'op': 'is null'}

    def test_move_type_maps_to_moveType(self):
        """account.move: move_type → moveType."""
        result = _leaf_to_condition(('move_type', 'in', ('out_invoice', 'out_refund')))
        assert result == {'field': 'moveType', 'op': 'in',
                          'value': ['out_invoice', 'out_refund']}

    def test_state_passes_through(self):
        """state → state (same name, explicit mapping)."""
        result = _leaf_to_condition(('state', 'not in', ('cancel', 'draft')))
        assert result == {'field': 'state', 'op': 'not in',
                          'value': ['cancel', 'draft']}

    def test_journal_id_maps_to_journal(self):
        """account.move: journal_id → journal$id."""
        result = _leaf_to_condition(('journal_id', '=', 3))
        assert result == {'field': 'journal$id', 'op': '=', 'value': 3}

    def test_picking_type_id_maps_to_pickingType(self):
        """stock.picking/purchase.order: picking_type_id → pickingType$id."""
        result = _leaf_to_condition(('picking_type_id', '=', 1))
        assert result == {'field': 'pickingType$id', 'op': '=', 'value': 1}


# ═══════════════════════════════════════════════════════════════════
# Boolean vs NULL disambiguation
# ═══════════════════════════════════════════════════════════════════

class TestBooleanVsNull:
    """Test dynamic field type introspection for Boolean vs NULL disambiguation.

    Odoo uses ('field', '=', False) for two entirely different semantics:
      - Many2one: field IS NULL  (no related record)
      - Boolean:  field = false  (boolean value is false)

    The field_types dict (built from Odoo ORM metadata via _build_field_types)
    tells _leaf_to_condition which interpretation to use.
    """

    # Simulated field types (what _build_field_types would return from Odoo ORM)
    FIELD_TYPES = {
        'partner_share': 'boolean',
        'active': 'boolean',
        'is_company': 'boolean',
        'company_id': 'many2one',
        'user_id': 'many2one',
    }

    def test_many2one_eq_false_is_null(self):
        """Many2one field: ('company_id', '=', False) → IS NULL."""
        result = _leaf_to_condition(('company_id', '=', False),
                                    ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'company$id', 'op': 'is null'}

    def test_many2one_neq_false_is_not_null(self):
        """Many2one field: ('user_id', '!=', False) → IS NOT NULL."""
        result = _leaf_to_condition(('user_id', '!=', False),
                                    ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'salesperson$id', 'op': 'is not null'}

    def test_boolean_eq_false_equals_false(self):
        """Boolean field: ('partner_share', '=', False) → = false."""
        result = _leaf_to_condition(('partner_share', '=', False),
                                    ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'partner_share', 'op': '=', 'value': False}

    def test_boolean_neq_false_neq_false(self):
        """Boolean field: ('active', '!=', False) → != false (i.e., active is true)."""
        result = _leaf_to_condition(('active', '!=', False),
                                    ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'active', 'op': '!=', 'value': False}

    def test_boolean_eq_false_negated(self):
        """NOT ('partner_share', '=', False) → partner_share != false."""
        result = _leaf_to_condition(('partner_share', '=', False),
                                    negate=True, ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'partner_share', 'op': '!=', 'value': False}

    def test_boolean_neq_false_negated(self):
        """NOT ('active', '!=', False) → active = false."""
        result = _leaf_to_condition(('active', '!=', False),
                                    negate=True, ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'active', 'op': '=', 'value': False}

    def test_is_company_boolean(self):
        """is_company is boolean (from field_types) → correct boolean handling."""
        result = _leaf_to_condition(('is_company', '=', False),
                                    ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'is_company', 'op': '=', 'value': False}

    def test_unknown_field_eq_false_is_null(self):
        """Unknown field (not in field_types) defaults to IS NULL (Many2one assumption)."""
        result = _leaf_to_condition(('some_relation_id', '=', False),
                                    ctx=FieldContext(field_types=self.FIELD_TYPES))
        assert result == {'field': 'some_relation_id', 'op': 'is null'}

    def test_no_field_types_defaults_to_null(self):
        """Without ctx (None), all '= False' treated as IS NULL."""
        result = _leaf_to_condition(('partner_share', '=', False))
        assert result == {'field': 'partner_share', 'op': 'is null'}

    def test_empty_field_types_defaults_to_null(self):
        """With empty field_types dict, all '= False' treated as IS NULL."""
        result = _leaf_to_condition(('active', '=', False),
                                    ctx=FieldContext(field_types={}))
        assert result == {'field': 'active', 'op': 'is null'}


# ═══════════════════════════════════════════════════════════════════
# Dynamic column_map (per-model field resolution)
# ═══════════════════════════════════════════════════════════════════

class TestDynamicColumnMap:
    """Test dynamic per-model column_map field resolution via FieldContext.

    FieldContext.column_map is a dict of {db_column: qm_field} loaded from Foggy
    metadata (FieldMappingRegistry). When column_map is present and non-empty,
    DIRECT_FIELD_MAP is bypassed entirely (no cross-model contamination).

    Priority: ctx.column_map (exclusive when non-empty) | DIRECT_FIELD_MAP (fallback when no column_map)
    """

    def test_column_map_overrides_direct_field_map(self):
        """column_map entry takes priority over DIRECT_FIELD_MAP.

        DIRECT_FIELD_MAP: user_id → salesperson$id
        column_map: user_id → user$id (e.g., hr.employee model)
        """
        ctx = FieldContext(column_map={'user_id': 'user$id'})
        result = _leaf_to_condition(('user_id', '=', 42), ctx=ctx)
        assert result == {'field': 'user$id', 'op': '=', 'value': 42}

    def test_column_map_none_falls_back_to_direct(self):
        """Without ctx, falls back to DIRECT_FIELD_MAP."""
        result = _leaf_to_condition(('user_id', '=', 42))
        assert result == {'field': 'salesperson$id', 'op': '=', 'value': 42}

    def test_column_map_empty_falls_back_to_direct(self):
        """Empty column_map falls back to DIRECT_FIELD_MAP."""
        result = _leaf_to_condition(('user_id', '=', 42),
                                    ctx=FieldContext(column_map={}))
        assert result == {'field': 'salesperson$id', 'op': '=', 'value': 42}

    def test_column_map_field_not_in_map_passthrough(self):
        """Field not in column_map passes through (DIRECT_FIELD_MAP bypassed).

        With non-empty column_map, DIRECT_FIELD_MAP is NOT consulted.
        This prevents cross-model contamination (e.g., user_id mapping
        from sale.order leaking into hr.employee).
        """
        ctx = FieldContext(column_map={'company_id': 'company$id'})
        result = _leaf_to_condition(('user_id', '=', 42), ctx=ctx)
        # user_id passes through as-is (not mapped to salesperson$id)
        assert result == {'field': 'user_id', 'op': '=', 'value': 42}

    def test_column_map_unknown_field_passthrough(self):
        """Field not in any map passes through unchanged."""
        ctx = FieldContext(column_map={'company_id': 'company$id'})
        result = _leaf_to_condition(('unknown_field', '=', 'abc'), ctx=ctx)
        assert result == {'field': 'unknown_field', 'op': '=', 'value': 'abc'}

    def test_per_model_user_id_sale_order(self):
        """sale.order: user_id → salesperson$id (from column_map)."""
        ctx = FieldContext(column_map={'user_id': 'salesperson$id', 'partner_id': 'partner$id'})
        result = _leaf_to_condition(('user_id', '=', 5), ctx=ctx)
        assert result == {'field': 'salesperson$id', 'op': '=', 'value': 5}

    def test_per_model_user_id_hr_employee(self):
        """hr.employee: user_id → user$id (different from sale.order)."""
        ctx = FieldContext(column_map={'user_id': 'user$id', 'department_id': 'department$id'})
        result = _leaf_to_condition(('user_id', '=', 5), ctx=ctx)
        assert result == {'field': 'user$id', 'op': '=', 'value': 5}

    def test_column_map_with_null_handling(self):
        """column_map works correctly with NULL/IS NULL semantics."""
        ctx = FieldContext(column_map={'user_id': 'user$id'})
        result = _leaf_to_condition(('user_id', '=', False), ctx=ctx)
        assert result == {'field': 'user$id', 'op': 'is null'}

    def test_column_map_with_boolean_field(self):
        """column_map works with boolean field_types disambiguation."""
        ctx = FieldContext(
            column_map={'partner_share': 'partnerShare'},
            field_types={'partner_share': 'boolean'},
        )
        result = _leaf_to_condition(('partner_share', '=', False), ctx=ctx)
        assert result == {'field': 'partnerShare', 'op': '=', 'value': False}

    def test_column_map_in_flatten(self):
        """column_map propagates through _flatten_to_dsl_slices via FieldContext."""
        domain = ['&', ('user_id', '=', 5), ('company_id', 'in', [1, 2])]
        tree = _parse_domain_ast(domain)
        ctx = FieldContext(column_map={'user_id': 'user$id', 'company_id': 'company$id'})
        slices = _flatten_to_dsl_slices(tree, ctx=ctx)
        assert slices == [
            {'field': 'user$id', 'op': '=', 'value': 5},
            {'field': 'company$id', 'op': 'in', 'value': [1, 2]},
        ]

    def test_column_map_in_or_branch(self):
        """column_map propagates into OR branches."""
        domain = ['|', ('user_id', '=', 5), ('user_id', '=', False)]
        tree = _parse_domain_ast(domain)
        ctx = FieldContext(column_map={'user_id': 'user$id'})
        slices = _flatten_to_dsl_slices(tree, ctx=ctx)
        assert len(slices) == 1
        assert '$or' in slices[0]
        or_children = slices[0]['$or']
        assert or_children[0] == {'field': 'user$id', 'op': '=', 'value': 5}
        assert or_children[1] == {'field': 'user$id', 'op': 'is null'}

    def test_column_map_in_not_branch(self):
        """column_map propagates into NOT (negated) branches."""
        domain = ['!', ('user_id', '=', 5)]
        tree = _parse_domain_ast(domain)
        ctx = FieldContext(column_map={'user_id': 'user$id'})
        slices = _flatten_to_dsl_slices(tree, ctx=ctx)
        assert slices == [{'field': 'user$id', 'op': '!=', 'value': 5}]

    def test_column_map_with_negated_or(self):
        """column_map in NOT(OR(A, B)) = AND(NOT(A), NOT(B))."""
        domain = ['!', '|', ('user_id', '=', 5), ('company_id', '=', 1)]
        tree = _parse_domain_ast(domain)
        ctx = FieldContext(column_map={'user_id': 'user$id', 'company_id': 'company$id'})
        slices = _flatten_to_dsl_slices(tree, ctx=ctx)
        # NOT(OR(A, B)) = NOT(A) AND NOT(B) → two AND'd slices
        assert len(slices) == 2
        assert slices[0] == {'field': 'user$id', 'op': '!=', 'value': 5}
        assert slices[1] == {'field': 'company$id', 'op': '!=', 'value': 1}

    def test_column_map_measure_field(self):
        """column_map works for measure fields (e.g., amount_total → amountTotal)."""
        ctx = FieldContext(column_map={'amount_total': 'amountTotal'})
        result = _leaf_to_condition(('amount_total', '>', 100), ctx=ctx)
        assert result == {'field': 'amountTotal', 'op': '>', 'value': 100}
