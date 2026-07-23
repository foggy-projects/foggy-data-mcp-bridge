---
evidence_type: step5-runtime-image-inspect-merge-regression-focused-diagnosis
version: 9.3.4
diagnostic: r13
plan_commit: 82258a396ceea75dfc93ef714f61477eed478901
activation_commit: ab34924cfb5d753967d94e006537651be198af66
tested_commit: 5c57e537603004d47be936f74e92f873de5fe431
execution_status: completed
diagnosis_status: confirmed
diagnosis_category: merge-regression-confirmed
docker_client_version: 28.4.0
docker_server_version: 28.4.0
portable_commit_in_first_parent: true
println_commit_in_second_parent: true
merge_result_equals_first_parent_tool: false
merge_result_equals_second_parent_tool: true
cfreeze_equals_merge_tool: true
post_merge_inspect_tool_changes: 0
same_image: true
current_format_line_count: 4
current_format_empty_record_count: 1
current_format_trailing_lf_count: 2
current_format_id_shape_valid: true
current_format_platform_valid: true
current_format_strict_three_record_valid: false
current_function_status: failed
current_function_error_code: E_IMAGE
portable_format_line_count: 1
portable_format_field_count: 3
portable_format_trailing_lf_count: 1
portable_format_identity_valid: true
historical_portable_function_status: passed
constant_only_current_parser_status: failed
constant_only_current_parser_error_code: E_IMAGE
existing_mock_line_count: 3
existing_mock_trailing_lf_count: 1
existing_mock_models_cli_terminal_newline: false
image_builds: 0
image_pulls: 0
containers_created: 0
package_invocations: 0
canonical_step5_invoked: false
owned_cleanup_verified: true
source_unchanged: true
step4_authority_unchanged: true
original_workspace_unchanged: true
raw_output_retained: false
privacy_review: passed
---

# Step 5 runtime-image inspect merge-regression r13 confirmed diagnosis

## Outcome

The focused same-image matrix confirms
`merge-regression-confirmed`. The r12 `package-image-runtime-inspect /
E_IMAGE` result was caused by the inspect format/parser implementation present
in the tested Cfreeze, not by the Maven/JVM control environment or by an
invalid image identity/platform.

No Maven goal, package command, image build, pull, container, Step 4 runner or
canonical Step 5 operation was executed by this diagnosis.

## Git causality

- `d2565c0a17ccdb09b410def03d6667e276660a26` is in merge
  `f67ed0534074a9182f7df8d15e1eafba69f40364`'s first-parent ancestry. It used
  one delimiter-separated record and split that record into three fields.
- `faf0efed6421b44d899e261c48a7763ec2123a57` is in the merge's second-parent
  ancestry. It used three `println` calls and then required exactly three
  `splitlines()` records.
- The merge result's package-tool blob equals the second parent's blob and
  differs from the first parent's blob.
- No later commit changed the package tool between that merge and Cfreeze
  `5c57e537603004d47be936f74e92f873de5fe431`.

The portable format/parser pair was therefore lost at the merge boundary, and
the tested source retained the second-parent `println` implementation.

## Runtime causality

On one owned alias of the same locally cached image:

- the current `println` format returned four `splitlines()` records: the three
  valid identity/platform records plus one empty record;
- the output had two terminal line feeds: one emitted by the final `println`
  and one emitted by the Docker CLI formatter;
- ID shape, OS and architecture fields were individually valid, but the strict
  exactly-three-record predicate failed;
- the unchanged current `docker_inspect_identity` consequently returned
  `E_IMAGE`;
- the historical delimiter format returned one line and exactly three valid
  fields, and the historical portable parser accepted it.

Changing only the format constant to the delimiter form while retaining the
current three-line parser still failed with `E_IMAGE`. The corrective unit is
therefore the atomic historical format/parser pair, not a constant-only edit
and not relaxed validation.

## Regression coverage gap

The existing Docker-free canonical fixture supplies exactly one terminal line
feed and therefore produces three records. It does not model the additional
terminal line feed appended by the Docker CLI after a template whose final
operation is already `println`.

That fixture allowed the `println` implementation to pass 117/117 package
negative/self-test cases while the real Docker path deterministically produced
the fourth empty record. The regression protection is a mock-fidelity gap, not
a failure of the strict identity/platform policy.

## Minimal remediation boundary

The smallest correct production-tool patch is:

1. restore the delimiter format and matching single-record field split from
   `d2565c0a`;
2. update the Docker-free exact-command fixture to the restored format/parser
   pair;
3. add a fixture that models Docker's terminal newline behavior and proves the
   `println`/double-terminal-line-feed form fails closed;
4. retain exact ID shape, linux/amd64, receipt schema, subphase, cleanup and
   package authority semantics unchanged.

This changes release-tool bytes, so its Step 4/5/6 integrity closure and a fresh
new-source Step 4 authority chain are required before another package proof.
It does not require a public API/SPI, Maven/POM, Dockerfile, production runtime,
coverage threshold or test-order change.

## Cleanup and authority boundary

The owned image tag and private tmux/control material were removed. No owned
tag, container, diagnostic or package process remained. Step 4/5/6 checksum
manifests passed after the live matrix, and the r11 candidate and final
artifacts remained public-valid. The execution clone and protected original
workspace remained unchanged, including the existing v9.3.5 user changes.

Only bounded counts, booleans, versions and commit identities are retained.
No raw Docker output, image identity, tag token, endpoint, credential,
temporary path or package digest is present in this record.
