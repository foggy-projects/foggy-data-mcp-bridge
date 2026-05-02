# Coordination Prompt: Python Pivot Engine Impact of Java Stage 5A

You are coordinating with the root controller or Python engine team for Foggy Pivot 9.0.0.beta.

Workspace context:

- Java workspace: `D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- Current Java docs:
  - `docs/9.0.0.beta/acceptance/s10_python_parity_plan.md`
  - `docs/9.0.0.beta/detailed_design/09_pivot_stage5_domain_transport_spike_plan.md`
  - `docs/9.0.0.beta/detailed_design/08_pivot_sql_topn_pushdown_refactor_plan.md`
  - `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-acceptance.md`
  - `docs/9.0.0.beta/acceptance/pivot-sql-topn-pushdown-performance-baseline.md`

Important: Java Stage 5A is an internal execution spike, not a Pivot DSL change.

## Background

Java Pivot SQL TopN/Having Pushdown Stage 1-4 has been accepted. The current Java behavior for non-additive subtotal/grandTotal with oversized surviving domain is:

- If surviving domain size is `> 500`, Java fails closed through `NonAdditiveRollupDomainTooLargeException`.
- This is an intentional safety boundary, not a silent fallback.
- The public Pivot DSL does not expose domain transport settings.

The Java team is considering a Stage 5A spike for large-domain transport:

- Replace huge `IN` / `OR-of-AND` predicates with an internal joinable domain relation.
- Candidate shapes: `VALUES` CTE, `UNION ALL` CTE, temp/session table.
- Keep any prototype disabled by default unless separately accepted.
- Do not change DSL, JSON schema, request shape, or result shape.

## Question for Python / Root Controller

Please decide how Python Pivot planning should account for Java Stage 5A:

1. Should Python 9.0.0 mirror the current Java accepted behavior exactly, including `domain > 500` fail-closed?
2. Should Python add an internal `DomainTransport` abstraction now, even if its only implementation is fail-closed?
3. If Java later enables Stage 5A by default, should Python treat that as:
   - a required parity blocker;
   - a later 9.0.x parity enhancement;
   - or a Java-only optimization temporarily outside Python mirror scope?
4. Should Python parity snapshots include an explicit oversized-domain error case?

## Recommended Position

Recommended default for Python 9.0.0:

- Do not change external DSL.
- Implement current Java accepted semantics first.
- Match `domain > 500` fail-closed behavior.
- Add a small internal abstraction boundary only if it does not delay the mirror work:
  - `DomainTransport`
  - default implementation: fail closed at the same threshold
  - future implementation: SQLAlchemy/PyPika domain relation renderer
- Do not block Python mirror on Java Stage 5A unless release ownership decides Java Stage 5A will be enabled by default before Python signoff.

## Expected Output

Please update the Python execution plan or reply with:

```markdown
## Python Pivot Stage 5A Impact Decision

- External DSL change required: yes/no
- Python 9.0.0 must implement Stage 5A transport: yes/no
- Python 9.0.0 must match `domain > 500` fail-closed: yes/no
- Add internal DomainTransport abstraction now: yes/no
- Oversized-domain parity snapshot required: yes/no
- Affected files/docs:
- Schedule impact:
- Open questions:
```
