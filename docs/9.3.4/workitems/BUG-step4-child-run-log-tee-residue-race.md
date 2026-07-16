---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-STEP4-CHILD-RUN-LOG-TEE-RESIDUE-RACE
severity: major
status: in-progress
reproduction_status: confirmed
test_strategy: runner-lifecycle-test
automation_decision: required
owner: step4-coverage-runner
---

# Step 4 child 日志 tee 未 join 导致进程组残留误报

## Background

Step 4 r3 从 clean committed/pushed HEAD
`e16693297239f2a861f3b93b3de60c1bb783bda0` 启动 all-lane diagnostic。Unit
authority 已完整签出 `4,941/F0E0S0`，但 outer 在 child leader 返回后立即检查整个
process group，发现仍有成员并 fail closed：

```text
[v934-unit] PASS run=step4-coverage-20260716-diagnostic-r3 ...
[v934-step4-coverage] ERROR: child returned with live process-group residue: unit
```

## Reproduction

r3 immutable failure：

- run：`step4-coverage-20260716-diagnostic-r3`；
- outer：`failed / exit_code=1 / last_phase=child-unit`；
- Unit：`681 positive execution + 55 structural / 4,941 testcase / F0E0S0`；
- database、external、Addon、aggregate、model gate 与 coverage observation 均未执行；
- outer summary absent，cleanup container/volume/network=`0/0/0`；
- worktree、`HEAD` 与 `origin/main` 保持一致且 clean。

无仓库写入的最小复现使用与 Unit 相同的 Bash process substitution：leader
`exec > >(tee ...)` 后退出，父进程只 `wait leader` 再立即探测 `-PGID`。快速循环在
`100/100` 次中都先观察到 group alive，10ms 后 `0/100` 仍存活；受控慢 sink
`20/20` 可捕获到 leader 已返回而同 PGID 的 `tee` 仍在排空。显式保存 logger PID、恢复
原始 FD 并 `wait logger` 后，立即残留为 `0/100`。

r3 runner 在 TERM 前没有持久化进程组成员快照，且 finalizer 已完成回收，因此不能诚实地
恢复 r3 当时的 PID。可由代码和受控复现确定的残留命令是
`tee -a .../target/v934-step2-unit/runs/step4-coverage-20260716-diagnostic-r3/run.log`；
受控复现 PID 不得冒充 r3 PID。

## Root Cause

`verify-v934-unit.sh` 与 `verify-v934-integration.sh` 都使用：

```bash
exec > >(tee -a "$RUN_ROOT/run.log") 2>&1
```

Bash process substitution 是异步子进程。runner 没保存 `$!`，成功与失败收口均没有关闭
写端并 `wait` logger。`coverage_tool.py launch-child` 在新 session/process group 中 exec
child，因此该 `tee` 继承 child PGID。outer 的 `wait "$ACTIVE_CHILD_PID"` 只回收组长
shell；紧接着的 group probe 可以在 logger flush/退出窗口看到真实成员，从而把完整绿色
Unit 判为 child residue。

r2 使用相同 Unit、outer 与 launcher 字节并成功进入 Integration，证明这是既有时序缺陷；
r1 的 Unit 自身失败，不经过同一成功收口路径。Integration 具有同样缺陷，必须一并修复。

## Expected vs Actual

- Expected：Unit/Integration runner 显式拥有 logger；关闭写端、完整 flush 并成功 wait 后，
  才能发布 durable green 并让 child leader 返回。真实长驻 descendant 仍由 outer
  fail closed，并在清理前留下可审计成员快照。
- Actual：child leader 可以先于未托管 logger 返回；outer 无法区分 logger 收尾与真实泄漏。

## Test Strategy

`automation_decision=required`：

1. Unit/Integration 共用 managed FIFO logger，保存原始 FD 与 logger PID；
2. success、ordinary failure、INT/TERM/HUP 都必须 close pipe、bounded wait，超时执行
   TERM/KILL 并返回失败；
3. logger flush 成功后才允许 durable `completed` status/summary；
4. 慢 logger 证明 leader 不会先返回，非零 logger 与持有 pipe 的 descendant 必须失败且
   summary absent；
5. outer 继续拒绝真实 sleeper/TERM-resistant descendant，并在杀组前记录
   PID/PPID/PGID/SID/stat/starttime/comm/cmdline；
6. Unit、Integration、Step 3 required child 与 fresh all-lane diagnostic 全部零进程残留；
7. 不通过固定 sleep、降低检查强度、跳过 child 或复用 r3 Unit 绿色绕过重跑。

## Fix Checklist

- [x] 封存 r3 outer RED、Unit GREEN、summary absent 与 cleanup 证据。
- [x] 确认 Unit/Integration 的未托管 process-substitution logger。
- [x] 用快/慢 sink 稳定复现 leader 与 logger 的退出顺序。
- [x] 实现共用 managed logger 与失败路径收口。
- [x] 补 logger lifecycle 和真实 process-group residue 自动化负例（9 类 / 14 case）。
- [x] 增加 child process-group ready/成员快照证据，关闭 fast-leader fail-open。
- [x] 完成 54/54 identity、全量静态与 pre-r4 正式质量闸门。
- [ ] 从新的 clean/pushed HEAD 完成 fresh all-lane diagnostic。

## References

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r3-fail-closed-20260716.md`
- `scripts/verify-v934-step4-coverage.sh`
- `scripts/verify-v934-unit.sh`
- `scripts/verify-v934-integration.sh`
- `scripts/verify-v934-step3-required-matrix.sh`
