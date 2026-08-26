#!/usr/bin/env bash

set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
bridge_root=$(cd "$script_dir/../../.." && pwd)
sync_script="$bridge_root/scripts/sync-release-to-obs.sh"
fixture_root=$(mktemp -d "${TMPDIR:-/tmp}/foggy-obs-sync-test.XXXXXX")
trap 'rm -rf -- "$fixture_root"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

expect_failure() {
  local label=$1
  shift
  if "$@" >"$fixture_root/failure.stdout" 2>"$fixture_root/failure.stderr"; then
    fail "$label unexpectedly succeeded"
  fi
}

write_launcher_fixture() {
  local destination=$1
  local version=$2
  mkdir -p "$destination"
  printf 'launcher jar fixture\n' > "$destination/foggy-runtime-launcher-$version.jar"
  printf 'powershell fixture\n' > "$destination/start-foggy-runtime.ps1"
  printf 'bash fixture\n' > "$destination/start-foggy-runtime.sh"
  printf 'readme fixture\n' > "$destination/README-foggy-runtime-launcher.md"
  local jar_sha256
  local jar_bytes
  jar_sha256=$(sha256sum "$destination/foggy-runtime-launcher-$version.jar" | cut -d' ' -f1)
  jar_bytes=$(stat -c '%s' "$destination/foggy-runtime-launcher-$version.jar")
  jq -n --arg version "$version" --arg jar_sha256 "$jar_sha256" --argjson jar_bytes "$jar_bytes" '
    {
      schemaVersion: "foggy-runtime-launcher/v1",
      releaseVersion: $version,
      releaseReady: true,
      source: {dirty: false},
      jar: {
        file: ("foggy-runtime-launcher-" + $version + ".jar"),
        sha256: $jar_sha256,
        bytes: $jar_bytes
      },
      features: {
        analyticsConsole: {
          embedded: true,
          enabledByDefault: false,
          fapEnabledByDefault: false
        },
        analyticsRuntimeApi: {
          embedded: true,
          enabledByDefault: false
        }
      },
      assets: [
        "foggy-runtime-launcher-" + $version + ".jar",
        "start-foggy-runtime.ps1",
        "start-foggy-runtime.sh",
        "README-foggy-runtime-launcher.md",
        "runtime-launcher-manifest.json",
        "SHA256SUMS"
      ]
    }' > "$destination/runtime-launcher-manifest.json"
  (
    cd "$destination"
    sha256sum \
      "foggy-runtime-launcher-$version.jar" \
      start-foggy-runtime.ps1 \
      start-foggy-runtime.sh \
      README-foggy-runtime-launcher.md \
      runtime-launcher-manifest.json > SHA256SUMS
  )
}

write_skill_triplet() {
  local destination=$1
  local version=$2
  local skill_name=$3
  local language=$4
  local suffix=
  [ "$language" = en ] || suffix="-$language"
  local base="$skill_name-skill-$version$suffix"
  mkdir -p "$destination/$skill_name"
  printf '%s fixture\n' "$skill_name" > "$destination/$skill_name/SKILL.md"
  (cd "$destination" && zip -q "$base.zip" "$skill_name/SKILL.md")
  local archive_sha256
  local archive_bytes
  local skill_file_sha256
  local skill_file_bytes
  archive_sha256=$(sha256sum "$destination/$base.zip" | cut -d' ' -f1)
  archive_bytes=$(stat -c '%s' "$destination/$base.zip")
  skill_file_sha256=$(sha256sum "$destination/$skill_name/SKILL.md" | cut -d' ' -f1)
  skill_file_bytes=$(stat -c '%s' "$destination/$skill_name/SKILL.md")
  jq -n \
    --arg name "$skill_name" \
    --arg version "$version" \
    --arg language "$language" \
    --arg archive "$base.zip" \
    --arg checksum "$base-SHA256SUMS" \
    --arg archive_sha256 "$archive_sha256" \
    --arg skill_file_sha256 "$skill_file_sha256" \
    --argjson archive_bytes "$archive_bytes" \
    --argjson skill_file_bytes "$skill_file_bytes" '
      {
        schemaVersion: "foggy-skill-package/v1",
        name: $name,
        version: $version,
        language: $language,
        sourceCommit: "0123456789abcdef0123456789abcdef01234567",
        package: {
          file: $archive,
          root: $name,
          sha256: $archive_sha256,
          bytes: $archive_bytes
        },
        checksums: $checksum,
        files: [{
          path: ($name + "/SKILL.md"),
          sha256: $skill_file_sha256,
          bytes: $skill_file_bytes
        }]
      }' > "$destination/$base-manifest.json"
  (
    cd "$destination"
    sha256sum "$base.zip" "$base-manifest.json" > "$base-SHA256SUMS"
  )
}

launcher_dir="$fixture_root/launcher"
write_launcher_fixture "$launcher_dir" 1.2.3
bash "$sync_script" \
  --component launcher \
  --tag foggy-runtime-launcher-v1.2.3 \
  --repo foggy-projects/foggy-data-mcp-bridge \
  --source-dir "$launcher_dir" \
  --validate-assets-only >/dev/null

printf 'unexpected\n' > "$launcher_dir/unexpected.txt"
expect_failure "launcher extra asset rejection" \
  bash "$sync_script" \
    --component launcher \
    --tag foggy-runtime-launcher-v1.2.3 \
    --repo foggy-projects/foggy-data-mcp-bridge \
    --source-dir "$launcher_dir" \
    --validate-assets-only
grep -q 'Release asset whitelist mismatch' "$fixture_root/failure.stderr" \
  || fail "launcher extra asset failure did not identify the whitelist"

skills_dir="$fixture_root/skills"
mkdir -p "$skills_dir"
write_skill_triplet "$skills_dir" 1.2.3 foggy-ai-analysis en
expect_failure "missing semantic-query Skill rejection" \
  bash "$sync_script" \
    --component skills \
    --tag v1.2.3 \
    --repo foggy-projects/foggy-ai-analysis \
    --source-dir "$skills_dir" \
    --validate-assets-only
grep -q 'Release asset whitelist mismatch' "$fixture_root/failure.stderr" \
  || fail "missing semantic-query Skill failure did not identify the paired whitelist"

write_skill_triplet "$skills_dir" 1.2.3 foggy-semantic-query en
bash "$sync_script" \
  --component skills \
  --tag v1.2.3 \
  --repo foggy-projects/foggy-ai-analysis \
  --source-dir "$skills_dir" \
  --validate-assets-only >/dev/null

semantic_manifest="$skills_dir/foggy-semantic-query-skill-1.2.3-manifest.json"
jq '.files[0].sha256 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"' \
  "$semantic_manifest" > "$fixture_root/semantic-manifest.invalid.json"
mv "$fixture_root/semantic-manifest.invalid.json" "$semantic_manifest"
(
  cd "$skills_dir"
  sha256sum \
    foggy-semantic-query-skill-1.2.3.zip \
    foggy-semantic-query-skill-1.2.3-manifest.json \
    > foggy-semantic-query-skill-1.2.3-SHA256SUMS
)
expect_failure "Skill archive entry digest rejection" \
  bash "$sync_script" \
    --component skills \
    --tag v1.2.3 \
    --repo foggy-projects/foggy-ai-analysis \
    --source-dir "$skills_dir" \
    --validate-assets-only
grep -q 'Skill archive entry digest mismatch' "$fixture_root/failure.stderr" \
  || fail "Skill entry mismatch did not identify the archive member"

echo "sync-release-to-obs focused tests passed"
