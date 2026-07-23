# Step 4 diagnostic-r23 MapBeanInfo recovery implementation quality

- reviewed_at: 2026-07-19
- scope: r23 non-freezable high-water analysis and deterministic existing-node regression
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-replacement-Cdiag-and-fresh-r24
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: one commit/push/clean Cdiag and one fresh diagnostic-r24 only

## Review basis

r23 完成所有 dynamic lanes 并通过 public validation，但 branch/complexity 各比 r22 多一个 covered
outcome。XML 与 raw exec 独立审计把唯一差异定位到
`MapBeanInfoHelper#getBeanProperty` inner double-check。19 个 retained diagnostic 的 probe 分布证明
该 outcome 依赖 incidental scheduling；而 freeze/formal 工具分别 exact 复制和强制 observed branch。
因此 r23 本身有效但不可冻结，candidate 保持 absent。

实现只扩展既有 `BeanInfoHelperTest#getClassHelper`。main thread 持有 helper monitor 后启动 lookup；
lookup 完成 first miss 后稳定阻塞在唯一 monitor-enter；main 在 monitor 内安装 expected property，
unlock→lock happens-before 使 inner read 确定观察 non-null。测试还覆盖 create/cache paths，在一个
test node 内重复 100 次，使用 5 秒 deadline/join 与 termination assertion。

## Verification

| Check | Result |
|---|---|
| r23 diagnostic | `PASS / public VALID / source exact / cleanup 0/0/0` |
| freeze decision | `threshold-candidate-not-authorized / candidate absent` |
| exact delta | only BeanInfoHelper.java:245; branch `3/4 -> 4/4`; complexity `2/3 -> 3/3` |
| targeted test | `5 fresh JVM x 1/F0E0S0`; each test has 100 controlled interleavings |
| targeted JaCoCo | class id=`a6629aa379049ec7`; probes=`10/11`; bitmap=`_wU`; `5/5 exact identical`; method branch=`4/4`; complexity=`3/3` |
| five exec SHA-256 | `89a5b3bf…71538`, `08f5e17d…8bff`, `0561521a…0b4d`, `8d3bb38a…47b9`, `799678ac…24f6` |
| full owning module | `foggy-core=97/F0E0S0`; BeanInfoHelperTest=`4/F0E0S0` |
| cardinality | existing `@Test` extended; no new test/report node |
| scope | production/POM/runner/threshold/critical/exclusion diff=`0` |
| final test source | LF normalized; SHA-256=`52bf8b885f6cd0e6e65fafe4f4afa753699edf5b5e902b93c778ef36f020966c` |
| Step 4 machine closure | manifest=`61/61` / `51ff1d26…f76`; full contract=`diagnostic-ready / diagnostic-pending / passed` |
| Step 4 negatives | contract=`27 + 22 + 12 / passed`; successor overlay=`20/20 / 6587458c…da81` |
| Step 6 machine closure | manifest=`16/16` / `d0f5393a…d62`; four workflows=`passed`; CI negatives=`86/86 / 9ba9c1d4…4638` |
| independent reviews | concurrency、final delta、machine preflight all `APPROVE`; combined `B0 H0 M0 L0` |
| diff hygiene | `git diff --check` PASS |

## Findings and decision

Blocker/High/Medium/Low 均为 0。监视器顺序、JMM 可见性、AtomicReference publication、join 和线程
收口成立；5 秒等待有界，正常 100 次交错约 0.3 秒。测试计数不变，生产行为未改。

该质量门只授权一次 replacement Cdiag commit/push/clean 和唯一 fresh diagnostic-r24。r24 必须全
lane PASS，并确认目标方法 4/4 branch 不再依赖其他并发 workload。它不授权 r23 candidate、Cfreeze、
formal、Step 5、9.3.5 或 9.4.0；若 r24 失败，继续 fail closed。
