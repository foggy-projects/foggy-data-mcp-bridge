# Frozen 9.5.0 release authority

This directory validates the **historical 9.5.0 contract** signed off at
`03af64da7330d70d3364c9e92992710a2ed3a111`. It is not the current-main release gate.
The contract fixes 31 modules / 32 projects, the 9.1.0.beta launcher boundary,
63 semantic cases and 370 database cases. Do not change these to match a newer
reactor or treat a newer build's passing tests as 9.5.0 authority evidence.

Run the tool's unit suite on Windows or Linux:

```sh
python -m unittest discover -s scripts/v950/tests -v
```

The positive contract test uses a frozen module-list fixture from the accepted
candidate. Negative tests reject added, removed or reordered modules, missing
module POMs and changed provisioner bytes. Candidate-tamper tests use ordinary
temporary directories and check the exact rejection, independently of Linux
`/dev/shm`. The production cross-filesystem requirement remains enforced.

Explicit candidate validation still inspects the actual source tree:

```sh
python scripts/v950/release_authority_tool.py validate-contract --repo-root /path/to/candidate
```

An incompatible main checkout must fail this command. This failure does not mean
the unit suite failed; conversely, passing tool unit tests is not a release signoff.

The full `verify-release-authority.sh` is a Linux-only historical authority runner
with clean-worktree, remote ancestry, database, root-clean and cross-filesystem
requirements. Do not run it in a development checkout containing retained test
evidence. Its existing safety and evidence rules are unchanged. A future release
authority needs its own approved version scope and evidence contract.

Current standalone Launcher publication uses `scripts/release/`, with its own
package, startup, field-binding and artifact digest checks. See the current
[v950 test-scope workitem](../../docs/9.5/9.5.5/workitems/BUG-v950-contract-test-scope.md)
for the independent maintenance change and verification results.
