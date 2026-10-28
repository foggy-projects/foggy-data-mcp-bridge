---
doc_type: bugfix
status: READY_FOR_HOST_VALIDATION
date: 2026-10-29
intended_for: engine maintainer / downstream host owner
purpose: Preserve declared QM formula viewer metadata through the existing semantic/frontend metadata path.
---

# QM formula viewer metadata

Baseline `4644d6ba`: a QM item with `formula: 'SUM(totalVolume)'` and
`extData.viewer: { format: 'number', precision: 4 }` loses its viewer before
frontend-meta. Regular ref fields retain the same extension. The loader converts
formula items to `CalculatedFieldDef` without carrying their presentation data;
the semantic formula branch consequently cannot expose it.

The correction carries the model's extension in `CalculatedFieldDef.modelExtData`,
which is transient and ignored for request JSON/serialization. Existing Java
constructors remain available. Semantic metadata reuses the existing viewer
whitelist (`format`, `rawUnit`, `displayUnit`, `scaleFactor`, `precision`), and the
frontend converter retains its own scalar whitelist. Private keys and nested
values remain hidden. Expressions, aggregation, SQL, units, permissions and the
public Model SPI are unchanged.

Targeted checks cover a real QM's ordinary, default-SUM, explicit-SUM and window
formulas; an unconfigured formula; ignored request JSON injection; frontend
conversion and private-key removal; existing row-formula and viewer regressions.
The six classes passed 24/24 tests (zero failures, errors or skips) on 2026-10-29:
`FormulaViewerMetadataTest`, `RowFormulaMetadataTest`, `ViewerMetadataExtensionTest`,
`RowFormulaAggregationTest`, `RowFormulaHavingTest`, and `FrontendMetaConverterTest`.
The normal reactor ran `-pl foggy-dataset-model-engine,addons/foggy-data-viewer -am
install` with that explicit test list, under the shared workspace Maven lock.
Normal local-repository thin jars match the reactor targets:

- engine SHA256: `ddd374591531aadcdecf18fc65a6232d06c0361e0b1cfa7b5cac744ed7c97305`
- viewer SHA256: `afef8c45da4e5139cf9db51a6bb2b35ec680af3193b6b766b5c0b9dadb0a6e19`

The development Maven coordinate remains `9.3.0-SNAPSHOT`; no release or version
change is implied. Downstream immutable-host frontend-meta verification remains
separate from local tests; old missing-viewer evidence is retained.
