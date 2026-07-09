---
type: bug
bug_source: user-report
version: runtime-api-launcher-v0.1.13
ticket: GH-121
severity: minor
status: closed
reproduction_status: confirmed
test_strategy: manual-evidence-only
automation_decision: optional
owner: foggy-data-mcp-bridge
created: 2026-07-09
updated: 2026-07-09
---

# BUG: v0.1.13 SHA256SUMS references missing RELEASE_NOTES.md asset

## Background

GitHub Issue GH-121 reports that release `foggy-runtime-launcher-v0.1.13` publishes `SHA256SUMS` with an entry for `RELEASE_NOTES.md`, but the file was not uploaded as a GitHub release asset.

## Reproduction

1. Download all GitHub release assets for `foggy-runtime-launcher-v0.1.13`.
2. Run `sha256sum -c SHA256SUMS`.
3. Observe that all published assets validate except `RELEASE_NOTES.md`, which is listed but missing from the release asset set.

## Expected vs Actual

Expected: every file listed in `SHA256SUMS` is available as a release asset and validates against the listed hash.

Actual: `RELEASE_NOTES.md` returns `404` as a release asset, so full checksum validation is partial even though the launcher jar validates and starts successfully.

## Impact Scope

The runtime launcher jar is not corrupted. The impact is release validation and installer/operator confidence for users who verify the complete checksum manifest.

## Test Strategy

Manual evidence is sufficient for this hotfix because the defect is in the published GitHub release asset set, not runtime code. The stable reproduction is remote asset enumeration plus checksum verification.

## Code Inventory

- GitHub release: `https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.13`
- Preserved local release directory: `/home/sa/workspace/foggy-data-mcp/.codex-tmp/foggy-runtime-launcher-v0.1.13-release/launcher`
- Expected checksum: `c04ffe5441ed4a52ebc19821fd1c52ecdd40e5a6c39bba835aee392fe81e00e7  RELEASE_NOTES.md`

## Fix Checklist

- [x] Confirm the missing release asset from GitHub release metadata.
- [x] Locate the preserved local `RELEASE_NOTES.md`.
- [x] Confirm its SHA-256 matches the published `SHA256SUMS` entry.
- [x] Upload the matching `RELEASE_NOTES.md` to release `foggy-runtime-launcher-v0.1.13`.
- [x] Re-download release assets and run `sha256sum -c SHA256SUMS`.
- [x] Add a GH-121 resolution comment and close the issue.

## Verification

Completed commands:

```bash
gh release view foggy-runtime-launcher-v0.1.13 --repo foggy-projects/foggy-data-mcp-bridge --json assets
sha256sum /home/sa/workspace/foggy-data-mcp/.codex-tmp/foggy-runtime-launcher-v0.1.13-release/launcher/RELEASE_NOTES.md
gh release download foggy-runtime-launcher-v0.1.13 --repo foggy-projects/foggy-data-mcp-bridge --dir .tmp/issue-121-verify
(cd .tmp/issue-121-verify && sha256sum -c SHA256SUMS)
```

Result:

```text
foggy-runtime-launcher-0.1.13.jar: OK
start-foggy-runtime.sh: OK
start-foggy-runtime.ps1: OK
README-foggy-runtime-launcher.md: OK
runtime-launcher-manifest.json: OK
RELEASE_NOTES.md: OK
```

## References

- GH-121: `https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/121`
