---
type: bug
bug_source: integration-test
version: 9.3.4
ticket: BUG-934-STEP3-EXTERNAL-MATRIX-CHILD-INT-INHERITANCE
severity: critical
status: in-progress
reproduction_status: confirmed-by-signal-probe
test_strategy: runner-contract-and-real-signal-probe
automation_decision: required
owner: scripts/v934/step3
---

# Shared external matrix child 继承 ignored SIGINT

## Symptom

Shared outer candidate `external-matrix-candidate-18498e4d-r1` 本身完成精确
`7 variants / 16 reports / 76 testcase / F0/E0/S0`。随后真实信号组
`external-matrix-signal-18498e4d-r1-int` 在 Redis child 已写
`signal-probe-ready.env` 后向 outer runner 发送 `INT`，但 45 秒内未完成。

Outer 最终写出 `exit_code=130/status=failed` 和 `0/0/0` aggregate cleanup；Redis child
没有写 `run-status.env` 或 `cleanup.env`。成功 summary、candidate、final 与 FIFO 均不存在，
Docker 标签残留为 `0/0/0`。因此清理最终 fail closed，但 parent/child durable signal
contract 未满足，该信号组和当前 shared authority 不可签收。

## Root Cause

Outer runner 使用后台作业 `setsid env ... runner --shared-child ... &` 启动 lane。
非 job-control Bash 会把异步 child 的 `SIGINT` disposition 设为 ignored；该状态穿过
`setsid` 和 `env` 进入新的 Bash runner。Bash 启动时继承的 ignored `SIGINT` 不能由脚本
内的 `trap ... INT` 重新启用，所以 parent 向 child process group 转发 `INT` 后，Redis
runner 仍停在 signal-probe loop。parent 等待 45 秒后发送 `KILL`，child finalizer 因而
没有机会写 durable status 和 cleanup。

无 Docker 最小回归稳定证明：普通后台 `setsid bash` 的 `/proc/<pid>/status` 包含 ignored
`SIGINT`，发送 INT 后仍存活且 trap 未执行；在新 session 中先显式设置
`SIGINT/SIGTERM/SIGHUP=SIG_DFL` 再 exec Bash 后，INT trap 执行并以 130 退出。

## Expected

- lane child 必须拥有独立 session/process group，且在 exec runner 前显式恢复
  `INT/TERM/HUP` 的 default disposition；
- inherited authority lock FD 必须继续跨 launcher/exec 可见并保持已加锁；
- parent 转发 `INT/TERM/HUP` 后，parent 与 child 分别以 `130/143/129` 写 failed status；
- child resource cleanup 与 parent aggregate cleanup 均为零残留；
- summary、candidate、final 与 FIFO 不得保留；
- signal helper 的总等待预算必须大于 parent 的 child graceful-shutdown budget，避免同秒竞态；
- 受约束 runner/helper 改动后，必须在新 commit 重跑完整 shared `7/16/76` candidate 和
  三类真实信号，旧候选只保留为诊断证据。

## Fix Checklist

- [x] 真实 matrix INT probe 稳定复现 child durable evidence 缺失。
- [x] 无 Docker 最小回归证明后台 Bash 的 ignored SIGINT 继承。
- [ ] 增加显式 signal reset + new session + exec 的 lane launcher。
- [ ] 固定 launcher 的 lock FD 继承与 process-group 契约。
- [ ] 错开 parent graceful wait 与 signal helper timeout。
- [ ] 更新 external contract bindings 与负向/静态检查。
- [ ] 新 commit 完整重跑 `7/16/76/F0/E0/S0`。
- [ ] 新 commit 真实 INT/TERM/HUP 全部得到 parent/child durable evidence 与零残留。
- [ ] 独立质量复核通过并关闭本 BUG。

## References

- `scripts/verify-v934-external-matrix.sh`
- `scripts/v934/step3/external_redis_signal_probe.py`
- `scripts/v934/step3/external_shared_context.sh`
- `docs/9.3.4/workitems/BUG-step3-external-matrix-gaps.md`
