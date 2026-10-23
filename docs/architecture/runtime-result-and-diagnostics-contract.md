# Runtime result and diagnostics contract (#158–#161)

## Scope and compatibility

This change extends Runtime transport diagnostics and result serialization, corrects
semantic summaries, and fixes Launcher exclusions. It retains the previous Compose
and date-difference changes. It does not change aggregate values, pagination, JOIN
semantics, permission rules, or global Jackson serialization for consuming apps.

## NULL output

Runtime query and Compose endpoints accept `X-Foggy-Null-Output: explicit`.
The default (or `omit`) retains the application's configured serialization.
Explicit mode retains existing null Map entries in response data; absent keys are
not created, and unauthorized or unselected fields are not inferred from models.
An aggregate row containing only null values remains one row; an empty result
remains empty. Null is never converted to zero. DTO null properties remain omitted.
This is an HTTP response option, not a query expression or permission option.

## Structured diagnostics

Existing error code, message, phase and field remain compatible. `error.path`
locates a specific erroneous member; Compose uses a root-relative plan path such
as `root.source.right.on[0].right` (not a source-code character offset).
`diagnostics.attributes` carries `planPath`, `joinSide`, `onIndex`, and, when safe,
`availableFields`. Candidates are sorted output aliases from successfully compiled,
permission-checked inputs. Pre-binding schema errors do not expose candidates.
No message parsing is used to reconstruct diagnostics. Date function errors retain
their calculation path and may supply supported `allowedValues`; changing a date
unit is not automatically safe. The same engine detail survives validate, preview,
and execute failures. Java constructor compatibility is retained.

## Summary and startup

Semantic summaries use actual bound GROUP BY columns, following the same
`groupByName != null` rule as SQL generation. An aggregate with no grouping is
described as an overall summary. Detailed queries do not acquire grouping claims.
Launcher shell and PowerShell scripts preserve lite Mongo exclusions alongside
the Odoo exclusion. Full-profile and explicit Mongo assembly remain available.

## Validation and impact

Regressions cover aggregate ratios, grouping aliases, NULL serialization in query
and Compose, permission-safe nested JOIN locations, date units, and real Launcher
startup. Regression gates precede the full reactor and MySQL 5.7/8, PostgreSQL,
SQL Server matrix. Evidence is stored outside Maven clean targets; previous source
and evidence hashes are recorded before changes. Release gates include downloaded
artifact identity, startup, real queries and independent Maven consumption.
