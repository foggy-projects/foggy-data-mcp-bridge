# -*- coding: utf-8 -*-
"""
Permission Bridge — converts Odoo ir.rule domains to DSL slice conditions.

For a given QM model, this module:
1. Looks up the corresponding Odoo model
2. Reads applicable ir.rule records for the user
3. Evaluates domain_force expressions (resolving user.id, company_ids, etc.)
4. Converts Odoo domain (Polish notation) to DSL slice conditions
5. Outputs a list of slice dicts that can be appended directly to payload.slice

Odoo domain uses Polish (prefix) notation:
    ['&', A, B]        → A AND B
    ['|', A, B]        → A OR B
    ['!', A]           → NOT A
    [A, B]             → A AND B  (implicit AND, after normalize_domain: ['&', A, B])

Output is standard DSL slice format:
    [
      {"field": "company_id", "op": "in", "value": [1, 3]},
      {"$or": [
        {"field": "user_id", "op": "=", "value": 42},
        {"field": "user_id", "op": "is null"}
      ]}
    ]
"""
import logging
from typing import NamedTuple, Optional

from odoo.osv import expression
from odoo.tools.safe_eval import safe_eval
from odoo.tools import safe_eval as _safe_eval_mod

from .tool_registry import QM_TO_ODOO_MODEL


class FieldContext(NamedTuple):
    """Immutable context for AST traversal — built once per model, used in _leaf_to_condition.

    Attributes:
        column_map: {db_column: qm_field} from FieldMappingRegistry (per-model dynamic mapping)
        field_types: {field_name: type_string} from Odoo ORM (for Boolean vs NULL disambiguation)
    """
    column_map: Optional[dict] = None
    field_types: Optional[dict] = None


# Sentinel for ctx=None — avoids allocating an empty NamedTuple on each call
_EMPTY_CTX = FieldContext()

_logger = logging.getLogger(__name__)

# ─── Operator mapping ──────────────────────────────────────────────

# Odoo domain operator → DSL slice operator (most pass through directly)
DOMAIN_OP_MAP = {
    '=': '=',
    '!=': '!=',
    '>': '>',
    '>=': '>=',
    '<': '<',
    '<=': '<=',
    'in': 'in',
    'not in': 'not in',
    'like': 'like',
    'ilike': 'like',
    '=like': 'like',
    '=ilike': 'like',
    'selfAndDescendantsOf': 'selfAndDescendantsOf',
    'selfAndAncestorsOf': 'selfAndAncestorsOf',
}

# Negation mapping for NOT operator (De Morgan's laws)
NEGATE_OP_MAP = {
    '=': '!=',
    '!=': '=',
    '>': '<=',
    '>=': '<',
    '<': '>=',
    '<=': '>',
    'in': 'not in',
    'not in': 'in',
    'is null': 'is not null',
    'is not null': 'is null',
    # 'like' cannot be cleanly negated — keep as-is and log
}

# ─── Field mapping ─────────────────────────────────────────────────

# Odoo ir.rule field names → QM model column names (dimension $id fields).
# Relational fields (e.g., 'user_id.id') are pre-resolved to IDs.
# Values are Foggy dimension fields, e.g., company$id, salesperson$id.
DIRECT_FIELD_MAP = {
    'company_id': 'company$id',
    'company_ids': 'company$id',
    'user_id': 'salesperson$id',
    'invoice_user_id': 'salesperson$id',  # account.move salesperson FK
    'partner_id': 'partner$id',
    'team_id': 'salesTeam$id',
    'department_id': 'department$id',
    'warehouse_id': 'warehouse$id',
    'journal_id': 'journal$id',
    'picking_type_id': 'pickingType$id',
    'move_type': 'moveType',             # account.move property
    'state': 'state',                    # common property (explicit mapping)
}

# Odoo ir.rule field → Foggy dimension field (for hierarchy operators).
# These fields reference hierarchical models with closure tables.
# child_of → selfAndDescendantsOf, parent_of → selfAndAncestorsOf.
HIERARCHY_FIELD_MAP = {
    'company_id': 'company$id',
    'company_ids': 'company$id',
    'department_id': 'department$id',
    'parent_id': 'parent$id',
}

# ─── Field type introspection ─────────────────────────────────────

def _build_field_types(env, odoo_model):
    """Build a field_name → field_type dict from Odoo ORM metadata.

    This enables dynamic Boolean vs NULL disambiguation without
    maintaining a static whitelist. For example:
        env['res.partner']._fields['partner_share'].type == 'boolean'
        env['res.partner']._fields['company_id'].type == 'many2one'

    Returns:
        dict: {field_name: type_string} e.g. {'active': 'boolean', 'company_id': 'many2one'}
    """
    try:
        Model = env[odoo_model].sudo()
        return {fname: f.type for fname, f in Model._fields.items()}
    except Exception as e:
        _logger.warning("Failed to introspect field types for %s: %s", odoo_model, e)
        return {}


# ─── Public API ────────────────────────────────────────────────────

def compute_permission_slices(env, uid, qm_model_name, field_mapping_registry=None):
    """
    Compute permission slice conditions for a single QM model.

    Reads the user's applicable ir.rule records for the corresponding Odoo model,
    converts domain expressions to DSL slice format.

    Args:
        env: Odoo environment
        uid: User ID
        qm_model_name: QM model name (e.g., 'OdooSaleOrderQueryModel')
        field_mapping_registry: Optional FieldMappingRegistry for dynamic
            DB column → QM field resolution. When provided, per-model metadata
            from Foggy is used instead of the static DIRECT_FIELD_MAP.

    Returns:
        list[dict]: DSL slice conditions, can be appended to payload['slice'].
                    Empty list if no permission filters needed.
    """
    odoo_model = QM_TO_ODOO_MODEL.get(qm_model_name)
    if not odoo_model:
        _logger.debug("No Odoo model mapping for QM: %s", qm_model_name)
        return []

    if odoo_model not in env:
        _logger.debug("Odoo model not installed: %s", odoo_model)
        return []

    # Load per-model column mapping from Foggy metadata (if registry available)
    column_map = None
    if field_mapping_registry:
        try:
            column_map = field_mapping_registry.get_column_map(qm_model_name)
        except Exception as e:
            _logger.warning("Failed to get column map for %s: %s", qm_model_name, e)

    slices = _compute_model_slices(env, uid, odoo_model, column_map)
    if slices:
        _logger.debug("Permission slices for %s (uid=%s): %d conditions",
                      qm_model_name, uid, len(slices))
    return slices


# ─── Model-level slice computation ────────────────────────────────

def _compute_model_slices(env, uid, odoo_model, column_map=None):
    """
    Compute permission slices for a single Odoo model based on ir.rule.

    Odoo rule semantics:
        - Global rules (groups=False): applied to ALL users, AND'd together
        - Group rules (groups=specific groups): OR'd across rules, then AND'd with globals
        - Final: global1 AND global2 AND ... AND (group_rule1 OR group_rule2 OR ...)

    Args:
        column_map: Optional dict of {db_column: qm_field} for dynamic field
                    resolution. Takes priority over static DIRECT_FIELD_MAP.

    Returns:
        list[dict]: DSL slice conditions (AND'd together at top level)
    """
    user = env['res.users'].sudo().browse(uid)

    rules = _get_applicable_rules(env, uid, odoo_model)
    if not rules:
        return []

    slices = []
    field_types = _build_field_types(env, odoo_model)
    ctx = FieldContext(column_map=column_map, field_types=field_types)

    global_rules = rules.filtered(lambda r: not r.groups)
    group_rules = rules.filtered(lambda r: r.groups)
    eval_context = _build_eval_context(env, user)

    # ── Global rules: each rule's domain is AND'd ──
    for rule in global_rules:
        try:
            domain = _eval_rule_domain(rule, eval_context)
            if not domain:
                continue
            domain = _expand_hierarchy_operators(env, domain, odoo_model)
            tree = _parse_domain_ast(domain)
            if tree is None:
                continue
            rule_slices = _flatten_to_dsl_slices(tree, ctx)
            slices.extend(rule_slices)
        except Exception as e:
            _logger.warning("Failed to evaluate global rule '%s' on %s: %s",
                            rule.name, odoo_model, e)

    # ── Group rules: OR'd across rules ──
    if group_rules:
        rule_branches = []
        eval_failures = 0
        for rule in group_rules:
            try:
                domain = _eval_rule_domain(rule, eval_context)
                if not domain:
                    # Empty domain or [(1,'=',1)] after normalize → no filter (allow all)
                    rule_branches.append([])
                    continue
                domain = _expand_hierarchy_operators(env, domain, odoo_model)
                tree = _parse_domain_ast(domain)
                if tree is None:
                    rule_branches.append([])
                    continue
                branch = _flatten_to_dsl_slices(tree, ctx)
                if branch:
                    rule_branches.append(branch)
                else:
                    rule_branches.append([])
            except Exception as e:
                eval_failures += 1
                _logger.warning("Failed to evaluate group rule '%s' on %s: %s",
                                rule.name, odoo_model, e)

        # Fail-closed: if ALL group rules failed to evaluate, deny access
        if eval_failures > 0 and eval_failures == len(group_rules):
            _logger.error(
                "All %d group rules failed for %s (uid=%s) — fail-closed: denying access",
                eval_failures, odoo_model, uid,
            )
            # Inject an impossible condition to block all rows
            slices.append({'field': 'id', 'op': '=', 'value': -1})
            return slices

        # Odoo semantics: group rules are OR'd.
        # If ANY branch is a tautology (empty = "allow all", e.g., [(1,'=',1)]),
        # then tautology OR anything = tautology → no group-level filter needed.
        has_tautology = any(b == [] for b in rule_branches)
        if has_tautology:
            _logger.debug("Group rules for %s include tautology — no group filter",
                          odoo_model)
        else:
            non_empty = [b for b in rule_branches if b]

            if not non_empty:
                # No branches at all → no group filter
                pass
            elif len(non_empty) == 1:
                # Single group rule with conditions → AND'd directly
                slices.extend(non_empty[0])
            elif len(non_empty) > 1:
                # Multiple group rules → OR'd:
                # {"$or": [branch1, branch2, ...]}
                # Each branch with multiple conditions is wrapped in {"$and": [...]}
                or_children = []
                for branch in non_empty:
                    if len(branch) == 1:
                        or_children.append(branch[0])
                    else:
                        # Multi-condition branch → {"$and": [cond1, cond2, ...]}
                        or_children.append({'$and': branch})
                slices.append({'$or': or_children})

    return slices


# ─── Rule retrieval ────────────────────────────────────────────────

def _get_applicable_rules(env, uid, odoo_model):
    """
    Get ir.rule records applicable to the user for the given model.

    Returns:
        ir.rule recordset
    """
    IrRule = env['ir.rule'].sudo()
    model_id = env['ir.model'].sudo().search([('model', '=', odoo_model)], limit=1)
    if not model_id:
        return IrRule.browse()

    user = env['res.users'].sudo().browse(uid)
    user_groups = user.groups_id

    rules = IrRule.search([
        ('model_id', '=', model_id.id),
        ('perm_read', '=', True),
        ('active', '=', True),
        '|',
        ('groups', '=', False),
        ('groups', 'in', user_groups.ids),
    ])

    return rules


def _build_eval_context(env, user):
    """Build evaluation context for domain_force.

    Uses Odoo 17's pre-wrapped safe modules (wrap_module) instead of raw
    Python builtins so that ``safe_eval`` does not reject the context.
    """
    return {
        'user': user,
        'uid': user.id,
        'company_id': user.company_id.id,
        'company_ids': user.company_ids.ids,
        'time': _safe_eval_mod.time,
        'datetime': _safe_eval_mod.datetime,
    }


def _eval_rule_domain(rule, eval_context):
    """Evaluate a rule's domain_force string to a domain list."""
    domain_str = rule.domain_force
    if not domain_str:
        return []

    domain = safe_eval(domain_str, eval_context)
    # Normalize: make implicit AND operators explicit
    return expression.normalize_domain(domain)


# ─── Hierarchy expansion (child_of / parent_of) ──────────────────

def _expand_hierarchy_operators(env, domain, odoo_model):
    """
    Expand child_of/parent_of to Foggy hierarchy operators or flat ID lists.

    For fields with closure table mapping (HIERARCHY_FIELD_MAP):
        child_of  → selfAndDescendantsOf (closure table JOIN, no Odoo ORM call)
        parent_of → selfAndAncestorsOf   (closure table JOIN, no Odoo ORM call)

    For unmapped fields: fallback to flat ID resolution via Odoo ORM.

    Example (mapped):
        ('company_id', 'child_of', [1])
        → ('company$id', 'selfAndDescendantsOf', 1)

    Example (unmapped, fallback):
        ('categ_id', 'child_of', [5])
        → ('categ_id', 'in', [5, 8, 12])

    Args:
        env: Odoo environment
        domain: Normalized domain list
        odoo_model: Odoo model name (e.g., 'sale.order')

    Returns:
        list: Domain with child_of/parent_of replaced
    """
    expanded = []
    for element in domain:
        if isinstance(element, (list, tuple)) and len(element) == 3:
            field, op, value = element
            if op in ('child_of', 'parent_of'):
                dim_field = HIERARCHY_FIELD_MAP.get(field)
                if dim_field:
                    # Map to Foggy closure table operator (no Odoo ORM call needed)
                    foggy_op = 'selfAndDescendantsOf' if op == 'child_of' else 'selfAndAncestorsOf'
                    norm_value = _normalize_hierarchy_value(value)
                    expanded.append((dim_field, foggy_op, norm_value))
                    _logger.debug("Hierarchy mapping: %s %s %s → %s %s %s",
                                  field, op, value, dim_field, foggy_op, norm_value)
                else:
                    # No closure table mapping → fallback to flat ID resolution
                    resolved_ids = _resolve_hierarchy(env, odoo_model, field, op, value)
                    if resolved_ids:
                        expanded.append((field, 'in', resolved_ids))
                    else:
                        _logger.debug("Hierarchy expansion returned empty for %s %s %s",
                                      field, op, value)
            else:
                expanded.append(element)
        else:
            expanded.append(element)
    return expanded


def _normalize_hierarchy_value(value):
    """
    Normalize hierarchy operator value.

    Foggy hierarchy operators (selfAndDescendantsOf, etc.) accept:
    - Single int: selfAndDescendantsOf(1) → closure.parent_id = 1
    - List: selfAndDescendantsOf([1, 2]) → closure.parent_id IN (1, 2)

    Single-element lists are unwrapped to a plain int for cleaner queries.
    """
    if hasattr(value, 'ids'):
        value = value.ids
    elif isinstance(value, int):
        return value

    if isinstance(value, (list, tuple)):
        if len(value) == 1:
            return value[0]
        return list(value)

    return value


def _resolve_hierarchy(env, odoo_model, field, op, value):
    """
    Resolve child_of/parent_of to a flat list of IDs.

    Uses Odoo's native search() with child_of/parent_of which leverages
    parent_path for efficient hierarchy traversal.
    """
    # Normalize value to list of ints
    if hasattr(value, 'ids'):
        value = value.ids
    elif isinstance(value, int):
        value = [value]
    elif isinstance(value, (list, tuple)):
        value = list(value)
    else:
        _logger.warning("Unexpected value type for %s: %s", op, type(value).__name__)
        return value if isinstance(value, list) else [value]

    if not value:
        return []

    field_base = field.split('.')[0]

    try:
        Model = env[odoo_model].sudo()
        field_def = Model._fields.get(field_base)
        if not field_def:
            _logger.debug("Field '%s' not found on model '%s'", field_base, odoo_model)
            return value

        comodel_name = getattr(field_def, 'comodel_name', None)
        if not comodel_name:
            _logger.debug("Field '%s' is not relational, cannot expand hierarchy", field_base)
            return value

        CoModel = env[comodel_name].sudo()
        result = CoModel.search([('id', op, value)])
        resolved = result.ids

        _logger.debug("Hierarchy expansion: %s %s %s → %d IDs",
                      field, op, value, len(resolved))
        return resolved

    except Exception as e:
        _logger.warning("Failed to resolve hierarchy for %s %s %s: %s",
                        field, op, value, e)
        return value


# ─── Domain AST parser (Polish notation) ──────────────────────────

def _parse_domain_ast(domain):
    """
    Parse Odoo's Polish-notation domain into an AST (Abstract Syntax Tree).

    AST node types:
        ('AND', left, right)
        ('OR', left, right)
        ('NOT', operand)
        ('LEAF', (field, op, value))

    Returns:
        tuple: AST node, or None if domain is empty
    """
    if not domain:
        return None

    pos = [0]  # mutable index for recursive descent

    def _parse_one():
        if pos[0] >= len(domain):
            return None

        token = domain[pos[0]]
        pos[0] += 1

        if token == '&':
            left = _parse_one()
            right = _parse_one()
            return ('AND', left, right)
        elif token == '|':
            left = _parse_one()
            right = _parse_one()
            return ('OR', left, right)
        elif token == '!':
            operand = _parse_one()
            return ('NOT', operand)
        elif isinstance(token, (list, tuple)) and len(token) == 3:
            return ('LEAF', tuple(token))
        else:
            _logger.warning("Unexpected domain token: %s (type=%s)", token, type(token).__name__)
            return None

    root = _parse_one()

    # Remaining elements are implicitly AND'd (shouldn't happen after normalize_domain)
    while pos[0] < len(domain):
        next_node = _parse_one()
        if next_node:
            root = ('AND', root, next_node)

    return root


# ─── AST → DSL slice conditions ──────────────────────────────────

def _flatten_to_dsl_slices(tree, ctx=None):
    """
    Flatten an AST into a list of DSL slice conditions.

    Top-level items are AND'd together (slice array semantics).
    OR subtrees become {"$or": [...]} objects.

    Args:
        tree: AST node from _parse_domain_ast
        ctx: FieldContext with column_map and field_types for field resolution

    Returns:
        list[dict]: DSL slice conditions
    """
    slices = []
    _collect_and_node(tree, slices, ctx)
    return slices


def _collect_and_node(node, slices, ctx=None):
    """
    Collect DSL slice conditions from an AND-rooted (or top-level) AST node.

    AND children are recursively expanded (top-level list = AND).
    OR children become {"$or": [...]} objects.
    LEAF children become condition dicts.
    NOT children are negated via De Morgan's laws.
    """
    if node is None:
        return

    node_type = node[0]

    if node_type == 'AND':
        _collect_and_node(node[1], slices, ctx)
        _collect_and_node(node[2], slices, ctx)

    elif node_type == 'OR':
        or_children = []
        _collect_or_children(node, or_children, ctx)
        if or_children:
            slices.append({'$or': or_children})

    elif node_type == 'LEAF':
        cond = _leaf_to_condition(node[1], ctx=ctx)
        if cond:
            slices.append(cond)

    elif node_type == 'NOT':
        _handle_not_node(node[1], slices, ctx)


def _collect_or_children(node, or_children, ctx=None):
    """
    Collect children from an OR subtree into a flat list for {"$or": [...]}.

    Nested OR trees are flattened: (A OR (B OR C)) → [A, B, C]
    AND inside OR is properly nested: (A AND B) OR C → [{"$and": [A, B]}, C]
    """
    if node is None:
        return

    node_type = node[0]

    if node_type == 'OR':
        _collect_or_children(node[1], or_children, ctx)
        _collect_or_children(node[2], or_children, ctx)

    elif node_type == 'LEAF':
        cond = _leaf_to_condition(node[1], ctx=ctx)
        if cond:
            or_children.append(cond)

    elif node_type == 'AND':
        # AND inside OR → wrap in {"$and": [...]}
        and_slices = []
        _collect_and_node(node, and_slices, ctx)
        if len(and_slices) == 1:
            or_children.append(and_slices[0])
        elif and_slices:
            or_children.append({'$and': and_slices})

    elif node_type == 'NOT':
        inner = node[1]
        if inner and inner[0] == 'LEAF':
            cond = _leaf_to_condition(inner[1], negate=True, ctx=ctx)
            if cond:
                or_children.append(cond)
        else:
            _logger.debug("NOT with complex operand inside OR — skipping")


def _handle_not_node(inner_node, slices, ctx=None):
    """
    Handle NOT(inner_node) using De Morgan's laws.

    NOT(LEAF) → negate operator, add to slices
    NOT(OR(A, B)) → AND(NOT(A), NOT(B)) → negate each, add individually
    NOT(AND(A, B)) → OR(NOT(A), NOT(B)) → {"$or": [NOT(A), NOT(B)]}
    """
    if inner_node is None:
        return

    node_type = inner_node[0]

    if node_type == 'LEAF':
        cond = _leaf_to_condition(inner_node[1], negate=True, ctx=ctx)
        if cond:
            slices.append(cond)

    elif node_type == 'OR':
        # NOT(A OR B) = NOT(A) AND NOT(B) → each added individually (AND'd)
        negated = []
        _collect_negated_leaves(inner_node, negated, ctx)
        slices.extend(negated)

    elif node_type == 'AND':
        # NOT(A AND B) = NOT(A) OR NOT(B) → {"$or": [...]}
        negated = []
        _collect_negated_leaves(inner_node, negated, ctx)
        if negated:
            slices.append({'$or': negated})

    else:
        _logger.warning("NOT with unsupported inner node type: %s", node_type)


def _collect_negated_leaves(node, negated_list, ctx=None):
    """Collect negated leaf conditions from a subtree."""
    if node is None:
        return

    node_type = node[0]

    if node_type == 'LEAF':
        cond = _leaf_to_condition(node[1], negate=True, ctx=ctx)
        if cond:
            negated_list.append(cond)
    elif node_type in ('AND', 'OR'):
        _collect_negated_leaves(node[1], negated_list, ctx)
        _collect_negated_leaves(node[2], negated_list, ctx)
    else:
        _logger.debug("Cannot negate complex node: %s", node_type)


# ─── Leaf conversion ──────────────────────────────────────────────

def _leaf_to_condition(leaf, negate=False, ctx=None):
    """
    Convert a single Odoo domain leaf (field, op, value) to a DSL slice condition.

    Args:
        leaf: Tuple of (field_name, operator, value)
        negate: If True, negate the operator (for NOT handling)
        ctx: FieldContext with column_map and field_types for field resolution

    Returns:
        dict: {'field': ..., 'op': ..., 'value': ...} or None if unsupported
    """
    field, op, value = leaf
    _ctx = ctx or _EMPTY_CTX

    # ── Handle tautology / contradiction literals ──
    # (1, '=', 1) → always true → no filter (return None)
    # (0, '=', 1) → always false → should never match
    if isinstance(field, int) and isinstance(value, int):
        if (op == '=' and field == value) or (op == '!=' and field != value):
            return None  # tautology — no condition needed
        # contradiction — return impossible condition
        return {'field': 'id', 'op': '=', 'value': -1}

    # Handle relational field traversal (e.g., 'company_id.id', 'user_id.company_id')
    if '.' in field:
        parts = field.split('.')
        if len(parts) == 2 and parts[1] == 'id':
            field = parts[0]
        else:
            _logger.debug("Multi-level field traversal not supported: %s (using first part)", field)
            field = parts[0]

    # Map Odoo field name to QM column name
    # When column_map is available (from FieldMappingRegistry), use it exclusively
    # to avoid cross-model contamination from the global DIRECT_FIELD_MAP.
    # Fall back to DIRECT_FIELD_MAP only when registry is unavailable.
    if _ctx.column_map:
        qm_field = _ctx.column_map.get(field, field)
    else:
        qm_field = DIRECT_FIELD_MAP.get(field, field)

    # ── Handle False value: Boolean vs NULL ──
    # Odoo uses False for both "IS NULL" (Many2one) and "equals false" (Boolean).
    # We use field_types from Odoo ORM metadata to distinguish the two cases.
    is_boolean = (_ctx.field_types or {}).get(field) == 'boolean'

    if op == '=' and value is False:
        if is_boolean:
            # Boolean field: ('active', '=', False) → active = false
            dsl_op = '!=' if negate else '='
            return {'field': qm_field, 'op': dsl_op, 'value': False}
        # Many2one / other: ('company_id', '=', False) → IS NULL
        dsl_op = 'is null'
        if negate:
            dsl_op = NEGATE_OP_MAP.get(dsl_op, dsl_op)
        return {'field': qm_field, 'op': dsl_op}

    if op == '!=' and value is False:
        if is_boolean:
            # Boolean field: ('active', '!=', False) → active != false (i.e., true)
            dsl_op = '=' if negate else '!='
            return {'field': qm_field, 'op': dsl_op, 'value': False}
        # Many2one / other: ('company_id', '!=', False) → IS NOT NULL
        dsl_op = 'is not null'
        if negate:
            dsl_op = NEGATE_OP_MAP.get(dsl_op, dsl_op)
        return {'field': qm_field, 'op': dsl_op}

    # ── Map operator ──
    dsl_op = DOMAIN_OP_MAP.get(op)
    if not dsl_op:
        _logger.warning("Unsupported domain operator '%s' for field '%s' — skipping", op, field)
        return None

    # ── Apply negation ──
    if negate:
        negated = NEGATE_OP_MAP.get(dsl_op)
        if negated:
            dsl_op = negated
        else:
            _logger.debug("Cannot negate operator '%s' for field '%s' — keeping original", dsl_op, field)

    # ── Normalize value ──
    if isinstance(value, (list, tuple)):
        value = list(value)

    # Handle Odoo recordset objects
    if hasattr(value, 'ids'):
        value = value.ids

    # Handle Odoo record singletons
    if hasattr(value, 'id') and not isinstance(value, (int, float, str, bool)):
        value = value.id

    # ── Handle False/None in 'in'/'not in' value lists ──
    # Odoo pattern: ('company_id', 'in', company_ids + [False])
    # False in a list means NULL for Many2one fields.
    # Convert to: field IN [non-null-values] OR field IS NULL (for 'in')
    #         or: field NOT IN [non-null-values] AND field IS NOT NULL (for 'not in')
    if isinstance(value, list) and dsl_op in ('in', 'not in'):
        has_false = any(v is False or v is None for v in value)
        if has_false:
            clean_values = [v for v in value if v is not False and v is not None]
            if not clean_values:
                # Only False/None values → simplify to is null / is not null
                null_op = 'is null' if dsl_op == 'in' else 'is not null'
                if negate:
                    null_op = NEGATE_OP_MAP.get(null_op, null_op)
                return {'field': qm_field, 'op': null_op}

            in_cond = {'field': qm_field, 'op': dsl_op, 'value': clean_values}
            null_cond = {'field': qm_field,
                         'op': 'is null' if dsl_op == 'in' else 'is not null'}

            if dsl_op == 'in':
                # field IN [...] OR field IS NULL → {"$or": [...]}
                return {'$or': [in_cond, null_cond]}
            else:
                # field NOT IN [...] AND field IS NOT NULL → {"$and": [...]}
                return {'$and': [in_cond, null_cond]}

    condition = {
        'field': qm_field,
        'op': dsl_op,
        'value': value,
    }

    # Remove value for null operators
    if dsl_op in ('is null', 'is not null'):
        condition.pop('value', None)

    return condition
