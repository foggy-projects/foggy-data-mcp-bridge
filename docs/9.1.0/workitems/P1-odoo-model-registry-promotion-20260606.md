---
doc_role: workitem
doc_purpose: Track promotion of verified Odoo TM/QM artifacts from bridge debug workspace into foggy-model-registry.
version: 9.1.0
status: tracked
created_at: 2026-06-06
updated_at: 2026-06-06
---

# P1 Odoo Model Registry Promotion

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, registry-owner
- purpose: Prevent verified Odoo TM/QM changes from remaining only in the bridge demo workspace.
- source_type: cross-project coordination
- priority: P1
- delivery_mode: multi-owner-delivery

## Background

Odoo TM/QM files are currently debugged and validated in this bridge repo under:

- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/model`
- `foggy-dataset-demo/src/main/resources/foggy/templates/odoo/query`

That location is useful for local MySQL fixtures, direct MCP baselines, and Java regression tests, but it must not become the long-term source of truth for reusable model packages.

The final model package destination is:

- remote: `https://github.com/foggy-projects/foggy-model-registry.git`
- local checkout: `/Users/fengjianguang/foggy-projects/foggy-model-registry`

## Current Scope To Promote After Stabilization

Initial promotion candidates from the current Odoo iteration:

- Odoo accounting TM/QM updates, including `OdooAccountMoveLineQueryModel.qm`
- `OdooAccountPaymentBillMatchModel.tm`
- `OdooAccountPaymentBillMatchQueryModel.qm`
- `OdooPurchaseDocumentFlowModel.tm`
- `OdooPurchaseDocumentFlowQueryModel.qm`
- `OdooSaleDocumentFlowModel.tm`
- `OdooSaleDocumentFlowQueryModel.qm`
- related Odoo dict/helper files if their model contracts changed

Fixture files and bridge-local validation helpers remain in this bridge repo unless registry packaging explicitly needs sample evidence.

## Promotion Gate

Before copying a model artifact into `foggy-model-registry`, the bridge-side version must have:

- direct domain baseline passing for the affected fixture pack
- target Maven regression passing for AI fixture loading and result validation
- `git diff --check` passing
- domain document updated with the semantic contract

Current evidence from this iteration:

- `odoo-sales-purchase-tests.json`: direct baseline `8/8 passed`
- target Maven regression: `38/38 passed`
- `git diff --check`: passed

Registry staging evidence:

- Published `foggy.odoo.community@1.1.10` from the verified bridge Odoo source.
- Published `foggy.odoo.pro@1.1.10` from the verified bridge Odoo source.
- Updated `community/foggy.odoo.community/stable.json` and `pro/foggy.odoo.pro/stable.json` to `1.1.10`.
- Community pull verification succeeded and wrote `/tmp/foggy-odoo-community-1.1.10-pull/models.lock.json`.
- Community bundle verification: `32` files, checksum passed, contains `OdooAccountPaymentBillMatch*`, `OdooPurchaseDocumentFlow*`, and `OdooSaleDocumentFlow*`, excludes `OdooMrpProduction*` and `OdooProjectTask*`.
- Pro bundle verification: `36` files, checksum passed, contains full community set plus `OdooMrpProduction*` and `OdooProjectTask*`.
- Pro authenticated pull verification completed with local ignored `config/keys.json` test key and `foggy.odoo.pro@1.1.10`.
- Downstream Java addon was synced from `foggy.odoo.pro@1.1.10`; `addons/foggy-odoo-bridge-java/models.lock.json` now points to `1.1.10` and the generated model directory drift check passes.
- Java addon `application-odoo.yml` model list now exposes the promoted payment match, purchase document flow, and sale document flow query models.

## Execution Notes

The registry checkout was created on 2026-06-06:

```bash
git clone https://github.com/foggy-projects/foggy-model-registry.git \
  /Users/fengjianguang/foggy-projects/foggy-model-registry
```

Registry-side tracking doc:

- `/Users/fengjianguang/foggy-projects/foggy-model-registry/docs/v1.0/P1-odoo-verified-model-promotion.md`

## Acceptance Criteria

- Verified Odoo TM/QM package is published or staged in `foggy-model-registry`.
- Registry manifest includes the promoted Odoo model files.
- Bridge docs point to registry as the final source of truth.
- Downstream consumers can pull the promoted package through the registry workflow.

## Current Status

- Registry cloned locally: complete.
- Promotion task recorded: complete.
- Model artifact publish: complete for local registry `1.1.10`.
- Registry pull verification after publish: complete for community and pro.
- Downstream consumer verification: complete for `addons/foggy-odoo-bridge-java` pro bundle sync and packaging.

## Downstream Consumer Verification

Commands executed on 2026-06-06:

```bash
bash scripts/pull-odoo-models.sh --edition pro --registry /Users/fengjianguang/foggy-projects/foggy-model-registry/data --channel stable --key fmk_live_xxx
bash scripts/check-model-drift.sh
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl addons/foggy-odoo-bridge-java -am -DskipTests package
```

Results:

- `scripts/pull-odoo-models.sh` resolved `foggy.odoo.pro@1.1.10`, verified bundle checksum `sha256:e821093622e8dbc1006d63648bee5fbf37d0f7763d5b9d492c0eb144d35bf2a6`, and synced `36` model files into the Java addon generated resource directory.
- `scripts/check-model-drift.sh` passed with content checksum `sha256:1a26e46d695a7c46134317e61436c24a4a7fde763b3a8e654b699212f90ec5af`.
- `mvn -pl addons/foggy-odoo-bridge-java -am -DskipTests package` passed; addon jar and sources jar were built.
- A full `mvn -pl addons/foggy-odoo-bridge-java -am test` attempt did not reach the addon because dependency module `foggy-dataset-model` failed existing MySQL-data-sensitive tests in `AdvancedAnalyticsTest` and `AggregateJoinQueryModelTest`. The failures are unrelated to the Odoo registry bundle sync and should be handled as a separate fixture/reset issue before using that command as an addon gate.
