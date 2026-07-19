# Step 4 diagnostic-r27 ExportWithChart order recovery implementation quality

- reviewed_at: 2026-07-19
- scope: one existing test node's map iteration order
- decision: PASS / ready-for-new-Cdiag
- B/H/M/L: `0/0/0/0`
- mandatory fixes: `0`

`Map.of("category", ..., "amount", ...)` had no specified entry iteration order while the production
method stops on the first number. The change makes only the test fixture ordered with
`LinkedHashMap(category -> amount)`; it does not alter production behavior, test identity or test count.

Static review confirms line 248 must now visit String false then Number true, and the pre-existing
`yField=amount` assertion continues to prove semantic behavior. Five fresh Maven/Surefire/JaCoCo runs each
completed `ExportWithChartToolTest=16/F0E0S0`; XML was `line 248 mb=0/cb=2` and the target class probe
bitmap was identical in all five runs.

r27 remains `diagnostic-observed / non-freezable`; this quality result authorizes only a clean/pushed Cdiag
and one new fresh diagnostic. It does not authorize candidate, Cfreeze, formal, acceptance, Step 5–7,
9.3.5 or 9.4.0.
