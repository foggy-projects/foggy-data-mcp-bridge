---
type: bug
bug_source: quality-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-GIT-ENVIRONMENT-OVERRIDE-BYPASS
severity: critical
status: closed
post_gate_confirmed_at: 2026-07-18
post_gate_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
test_strategy: security-negative-test
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 Git 环境覆盖可绕过仓库身份与 frozen replay

## Background

pre-r4 实现质量复核发现，Step 4 的 Git 子进程只删除了少量 ambient `GIT_*`
变量。`GIT_SHALLOW_FILE`、`GIT_GRAFT_FILE`、`GIT_REPLACE_REF_BASE`、
`GIT_NAMESPACE`、`GIT_CONFIG_*` 等仍可进入 source identity 与 frozen diagnostic
replay；`launch-child` 的 Git 目录解析甚至直接继承完整环境。coverage contract 启动
XML frozen validator 时也重新复制了 ambient 环境；successor overlay 的 parent/
drift/ancestry Git 调用和 outer authority lock 解析也没有独立净化。

这些变量可改变 Git 看到的 shallow/graft、对象、引用、index、worktree、repository
或 config。若调用方把真实危险元数据重定向到不存在路径，validator 可能把不同的 Git
视图当成 canonical repository，属于 provenance fail-open。

## Reproduction

自动化负例构造真实 Git 仓库，而不是模拟 Git 输出：

1. 建立两个 commit 的 origin，以 `--depth=1 --no-local` clone 得到真实 shallow
   repository；在子进程设置 `GIT_SHALLOW_FILE=<missing>`；
2. 建立普通 repository，在 canonical `.git/info/grafts` 写入非空 graft，同时设置
   `GIT_GRAFT_FILE=<missing>`；
3. 建立 `refs/replace/<sha>`，同时设置
   `GIT_REPLACE_REF_BASE=refs/v934-hidden-replace`；
4. 对 clean control 同时注入 repository/worktree/index/object/ref/config/shallow/graft
   共 17 个高风险 override；
5. 对 XML frozen Git helper 重复 shallow、graft、replace 与整组 ambient override。

修复前的 blocklist 未删除前三类 override，Git 可以使用攻击者指定的替代 metadata。
修复后的 focused 结果为：source identity `7/7`（前三个既有 identity control，加 shallow、
graft、replace 与 17-key deny-by-default control），XML fast negative `50/50`；真实
shallow/graft/replace 均返回 `rc=2` 或 `E_FROZEN_GIT`，没有被 missing-path override
隐藏。

## Root Cause

安全边界使用 `os.environ.copy()` 再按已知变量逐项删除。Git 的环境控制面不是封闭集合；
仅清除 `GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE/GIT_OBJECT_DIRECTORY` 等少数字段，既遗漏
现有 high-risk override，也会对未来新增变量 fail open。三个独立调用面还存在不一致：

- `coverage_tool.py run_git` 使用不完整 blocklist；
- `launch_child_command` 的 Git 目录解析没有安全环境；
- frozen XML 子进程复制 ambient 环境；
- `coverage_xml_tool.py` 重复了同一不完整 blocklist，且 standalone frozen helper 没有
  独立拒绝 shallow/replace/graft topology。
- `successor/overlay_tool.py` 与 outer shell authority 各自直接继承 ambient Git
  environment，使 overlay 与锁路径仍可能看到另一套 repository view。

## Expected vs Actual

- Expected：所有 security Git 子进程从非 Git 白名单环境构造；只注入固定安全 Git
  开关。真实 shallow、replace 或非空 graft 在任意 ambient override 下都必须失败；
  source validator、child launcher 与 standalone frozen replay 使用同一语义。
- Actual：安全性依赖一份不完整变量黑名单，不同调用面还可能完全绕过该黑名单。

## Test Strategy

`automation_decision=required`：

1. `coverage_tool.py` 与 `coverage_xml_tool.py` 的 Git 环境仅保留
   `PATH/SYSTEMROOT/TMP*`，固定 `GIT_CONFIG_GLOBAL=/dev/null`、
   `GIT_CONFIG_NOSYSTEM=1`、`GIT_NO_REPLACE_OBJECTS=1`、
   `GIT_OPTIONAL_LOCKS=0` 与 C locale；
2. 自动审计 17 个 high-risk `GIT_*` 均不被转发；
3. 真实 shallow + malicious `GIT_SHALLOW_FILE=<missing>` 必须 `rc=2`；
4. 真实 non-empty graft + malicious `GIT_GRAFT_FILE=<missing>` 必须 `rc=2`；
5. 真实 replace ref + alternate replace base 必须 `rc=2`；
6. XML `git_current_head/require_git_ancestor/git_show_blob` 在 hostile environment 下仍
   返回 canonical identity/blob，并独立拒绝 shallow/graft/replace；
7. `launch-child` Git directory 与 coverage-tool-to-XML subprocess 必须复用同一安全环境；
8. successor overlay 必须独立清除 ambient `GIT_*`，outer 在第一次 Git/lock 调用前净化
   全部 Git override 并只恢复固定安全配置；
9. identity manifest 刷新后重跑完整 contract/overlay negative 与 full validator，随后才可关闭。

## Fix Checklist

- [x] 确认 source、child launcher、XML subprocess 与 frozen helper 四个环境边界。
- [x] 用非 Git allowlist 替换 ambient `GIT_*` blocklist。
- [x] child launcher Git directory 改为统一 `run_git` 安全入口。
- [x] coverage tool 启动 frozen XML validator 时复用安全环境并拒绝 Python import override。
- [x] standalone XML frozen helper 增加 canonical root/object-format/shallow/replace/graft 检查。
- [x] successor overlay 的全部 Git 子进程使用 deny-by-default 环境；hostile override focused
  control 通过，完整 suite 待 identity 刷新后确认为 `12/12`。
- [x] outer authority 在任何 Git/lock 调用前清除 ambient `GIT_*`，children 继承相同环境。
- [x] source identity 真实 shallow/graft/replace 与 17-key control：`7/7`。
- [x] XML fast negative：`50/50`。
- [x] 四个 Python 文件 `py_compile` 通过。
- [x] identity manifest 刷新后完整 contract `20/20` + Git identity `7/7`
  与 full validator 通过。
- [x] pre-r4 正式质量闸门复核，open Blocker/High/Medium=`0/0/0`。

## References

- `scripts/v934/step4/coverage_tool.py`
- `scripts/v934/step4/coverage_contract_negative_tool.py`
- `scripts/v934/step4/coverage_xml_tool.py`
- `scripts/v934/step4/coverage_xml_negative_tool.py`
- `docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`
