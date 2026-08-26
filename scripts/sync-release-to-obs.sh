#!/usr/bin/env bash

set -euo pipefail

component=
tag=
repo=$(printenv GITHUB_REPOSITORY || true)
version=
source_dir=
validate_assets_only=false
obsutil_bin=$(printenv OBSUTIL_BIN || true)
bucket=$(printenv OBS_BUCKET || true)
prefix=$(printenv OBS_PREFIX || true)
endpoint=$(printenv OBS_ENDPOINT || true)
public_base_url=$(printenv OBS_PUBLIC_BASE_URL || true)
[ -n "$bucket" ] || bucket=obs-fe55
[ -n "$prefix" ] || prefix=foggy-runtime
[ -n "$endpoint" ] || endpoint=obs.cn-north-4.myhuaweicloud.com

usage() {
  echo "Usage: $0 --component cli|skills|launcher --tag TAG [--repo OWNER/REPO] [--version VERSION] [--source-dir DIR] [--obsutil PATH] [--public-base-url URL] [--validate-assets-only]"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --component) component=$2; shift 2 ;;
    --tag) tag=$2; shift 2 ;;
    --repo) repo=$2; shift 2 ;;
    --version) version=$2; shift 2 ;;
    --source-dir) source_dir=$2; shift 2 ;;
    --obsutil) obsutil_bin=$2; shift 2 ;;
    --bucket) bucket=$2; shift 2 ;;
    --prefix) prefix=$2; shift 2 ;;
    --endpoint) endpoint=$2; shift 2 ;;
    --public-base-url) public_base_url=$2; shift 2 ;;
    --validate-assets-only) validate_assets_only=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$component" in
  cli|skills)
    [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([._-][A-Za-z0-9._-]+)?$ ]] || {
      echo "Unexpected release tag for $component: $tag" >&2
      exit 2
    }
    [ -n "$version" ] || version=$(printf '%s' "$tag" | sed 's/^v//')
    ;;
  launcher)
    [[ "$tag" =~ ^foggy-runtime-launcher-v[0-9]+\.[0-9]+\.[0-9]+([._-][A-Za-z0-9._-]+)?$ ]] || {
      echo "Unexpected launcher release tag: $tag" >&2
      exit 2
    }
    [ -n "$version" ] || version=$(printf '%s' "$tag" | sed 's/^foggy-runtime-launcher-v//')
    ;;
  *)
    echo "--component must be cli, skills, or launcher" >&2
    exit 2
    ;;
esac
[ -n "$repo" ] || { echo "--repo or GITHUB_REPOSITORY is required" >&2; exit 2; }
[ -n "$public_base_url" ] || public_base_url="https://$bucket.$endpoint"
public_base_url=${public_base_url%/}
case "$public_base_url" in
  https://*) public_curl_proto='=https' ;;
  http://127.0.0.1:*|http://localhost:*) public_curl_proto='=http' ;;
  *)
    echo "--public-base-url must use HTTPS (HTTP is allowed only for local focused tests)" >&2
    exit 2
    ;;
esac

tmp_base=$(printenv RUNNER_TEMP || true)
[ -n "$tmp_base" ] || tmp_base=/tmp
work_dir=$(mktemp -d "$tmp_base/foggy-release-obs.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT

if [ -z "$source_dir" ]; then
  source_dir="$work_dir/assets"
  mkdir -p "$source_dir"
  token=$(printenv GITHUB_TOKEN || true)
  release_json="$work_dir/release.json"
  if [ -n "$token" ]; then
    curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
      --proto '=https' --proto-redir '=https' \
      --header 'Accept: application/vnd.github+json' \
      --header 'X-GitHub-Api-Version: 2022-11-28' \
      --header "Authorization: Bearer $token" \
      "https://api.github.com/repos/$repo/releases/tags/$tag" -o "$release_json"
  else
    curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
      --proto '=https' --proto-redir '=https' \
      --header 'Accept: application/vnd.github+json' \
      --header 'X-GitHub-Api-Version: 2022-11-28' \
      "https://api.github.com/repos/$repo/releases/tags/$tag" -o "$release_json"
  fi
  jq -e '(.draft == false) and ((.assets | length) > 0)' \
    "$release_json" >/dev/null
  while IFS=$'\t' read -r download_url asset_name; do
    [[ "$asset_name" =~ ^[A-Za-z0-9._+-]+$ ]] || {
      echo "Unsafe release asset name: $asset_name" >&2
      exit 1
    }
    if [ -n "$token" ]; then
      curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
        --proto '=https' --proto-redir '=https' \
        --header "Authorization: Bearer $token" \
        "$download_url" -o "$source_dir/$asset_name"
    else
      curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
        --proto '=https' --proto-redir '=https' \
        "$download_url" -o "$source_dir/$asset_name"
    fi
  done < <(jq -r '.assets[] | [.browser_download_url, .name] | @tsv' "$release_json")
fi

[ -d "$source_dir" ] || { echo "Asset directory not found: $source_dir" >&2; exit 1; }

assert_same_lines() {
  local label=$1
  local expected_file=$2
  local actual_file=$3
  if ! cmp -s "$expected_file" "$actual_file"; then
    echo "$label mismatch." >&2
    echo "Expected:" >&2
    sed 's/^/  /' "$expected_file" >&2
    echo "Actual:" >&2
    sed 's/^/  /' "$actual_file" >&2
    exit 1
  fi
}

expected_assets="$work_dir/expected-assets.txt"
case "$component" in
  launcher)
    printf '%s\n' \
      "foggy-runtime-launcher-$version.jar" \
      "start-foggy-runtime.ps1" \
      "start-foggy-runtime.sh" \
      "README-foggy-runtime-launcher.md" \
      "runtime-launcher-manifest.json" \
      "SHA256SUMS" | sort > "$expected_assets"
    ;;
  cli)
    printf '%s\n' \
      "foggy_runtime_cli-$version-py3-none-any.whl" \
      "foggy_runtime_cli-$version.tar.gz" \
      "install-foggy-runtime-cli.ps1" \
      "install-foggy-runtime-cli.sh" \
      "release-manifest.json" \
      "SHA256SUMS" | sort > "$expected_assets"
    ;;
  skills)
    printf '%s\n' \
      "foggy-ai-analysis-skill-$version.zip" \
      "foggy-ai-analysis-skill-$version-manifest.json" \
      "foggy-ai-analysis-skill-$version-SHA256SUMS" \
      "foggy-semantic-query-skill-$version.zip" \
      "foggy-semantic-query-skill-$version-manifest.json" \
      "foggy-semantic-query-skill-$version-SHA256SUMS" > "$expected_assets"
    if find "$source_dir" -mindepth 1 -maxdepth 1 -type f -name '*-zh-CN*' -print -quit | grep -q .; then
      printf '%s\n' \
        "foggy-ai-analysis-skill-$version-zh-CN.zip" \
        "foggy-ai-analysis-skill-$version-zh-CN-manifest.json" \
        "foggy-ai-analysis-skill-$version-zh-CN-SHA256SUMS" >> "$expected_assets"
    fi
    sort -o "$expected_assets" "$expected_assets"
    ;;
esac

actual_assets="$work_dir/actual-assets.txt"
find "$source_dir" -mindepth 1 -maxdepth 1 -type f -not -name '.*' \
  -printf '%f\n' | sort > "$actual_assets"
assert_same_lines "Release asset whitelist" "$expected_assets" "$actual_assets"

checksum_count=0
checksum_targets="$work_dir/checksum-targets.txt"
: > "$checksum_targets"
while IFS= read -r -d '' checksum_file; do
  while IFS= read -r checksum_line || [ -n "$checksum_line" ]; do
    checksum_line=${checksum_line%$'\r'}
    [[ "$checksum_line" =~ ^([a-f0-9]{64})[[:space:]]+([A-Za-z0-9._+-]+)$ ]] || {
      echo "Invalid checksum line in $(basename "$checksum_file"): $checksum_line" >&2
      exit 1
    }
    printf '%s\n' "${BASH_REMATCH[2]}" >> "$checksum_targets"
  done < "$checksum_file"
  (cd "$source_dir" && sha256sum -c "$(basename "$checksum_file")")
  checksum_count=$((checksum_count + 1))
done < <(find "$source_dir" -mindepth 1 -maxdepth 1 -type f -name '*SHA256SUMS' -print0)
[ "$checksum_count" -gt 0 ] || {
  echo "Release assets do not contain a SHA256SUMS file: $source_dir" >&2
  exit 1
}
duplicate_checksum_targets=$(sort "$checksum_targets" | uniq -d)
[ -z "$duplicate_checksum_targets" ] || {
  echo "Release checksum files contain duplicate targets:" >&2
  printf '%s\n' "$duplicate_checksum_targets" | sed 's/^/  /' >&2
  exit 1
}
sort -o "$checksum_targets" "$checksum_targets"
checksummed_assets="$work_dir/checksummed-assets.txt"
cli_legacy_checksum_contract=false
if [ "$component" = cli ]; then
  cli_checksum_coverage=$(jq -r '.checksumCoverage // ""' \
    "$source_dir/release-manifest.json")
  case "$cli_checksum_coverage" in
    all-release-assets) ;;
    "")
      if [ "$version" = 0.1.22 ]; then
        cli_legacy_checksum_contract=true
      else
        echo "Only the known CLI v0.1.22 release may omit checksumCoverage; all other releases must declare checksumCoverage=all-release-assets." >&2
        exit 1
      fi
      ;;
    *)
      echo "Unsupported CLI checksumCoverage: $cli_checksum_coverage" >&2
      exit 1
      ;;
  esac
fi
if [ "$cli_legacy_checksum_contract" = true ]; then
  jq -r '.artifacts[].file' "$source_dir/release-manifest.json" \
    | sort > "$checksummed_assets"
  echo "Known CLI v0.1.22 checksum contract detected; only declared wheel/sdist artifacts are covered." >&2
else
  find "$source_dir" -mindepth 1 -maxdepth 1 -type f -not -name '*SHA256SUMS' \
    -printf '%f\n' | sort > "$checksummed_assets"
fi
assert_same_lines "Checksum coverage" "$checksummed_assets" "$checksum_targets"

case "$component" in
  launcher)
    launcher_jar="$source_dir/foggy-runtime-launcher-$version.jar"
    launcher_jar_sha256=$(sha256sum "$launcher_jar" | cut -d' ' -f1)
    launcher_jar_bytes=$(stat -c '%s' "$launcher_jar")
    jq -e \
      --arg version "$version" \
      --arg jar_sha256 "$launcher_jar_sha256" \
      --argjson jar_bytes "$launcher_jar_bytes" \
      --slurpfile expected <(
      jq -R -s 'split("\n") | map(select(length > 0))' "$expected_assets"
    ) '
      .schemaVersion == "foggy-runtime-launcher/v1"
      and .releaseVersion == $version
      and .releaseReady == true
      and .source.dirty == false
      and .jar.file == ("foggy-runtime-launcher-" + $version + ".jar")
      and .jar.sha256 == $jar_sha256
      and .jar.bytes == $jar_bytes
      and .features.analyticsConsole.embedded == true
      and .features.analyticsConsole.enabledByDefault == false
      and .features.analyticsConsole.fapEnabledByDefault == false
      and .features.analyticsRuntimeApi.embedded == true
      and .features.analyticsRuntimeApi.enabledByDefault == false
      and ((.assets | sort) == ($expected[0] | sort))
    ' "$source_dir/runtime-launcher-manifest.json" >/dev/null || {
      echo "Launcher manifest does not match the publishable release contract." >&2
      exit 1
    }
    ;;
  cli)
    jq -e --arg version "$version" '
      .schemaVersion == "foggy-runtime-cli-release/v1"
      and .version == $version
      and .checksums == "SHA256SUMS"
      and ((.checksumCoverage == null) or (.checksumCoverage == "all-release-assets"))
      and all(.artifacts[]; (keys | sort) == ["bytes", "file", "sha256"])
      and ([.artifacts[].file] | sort) == ([
        "foggy_runtime_cli-" + $version + "-py3-none-any.whl",
        "foggy_runtime_cli-" + $version + ".tar.gz"
      ] | sort)
    ' "$source_dir/release-manifest.json" >/dev/null || {
      echo "CLI manifest does not match the publishable release contract." >&2
      exit 1
    }
    while IFS= read -r cli_artifact; do
      cli_artifact_sha256=$(sha256sum "$source_dir/$cli_artifact" | cut -d' ' -f1)
      cli_artifact_bytes=$(stat -c '%s' "$source_dir/$cli_artifact")
      jq -e \
        --arg file "$cli_artifact" \
        --arg sha256 "$cli_artifact_sha256" \
        --argjson bytes "$cli_artifact_bytes" '
          any(.artifacts[];
            .file == $file and .sha256 == $sha256 and .bytes == $bytes)
        ' "$source_dir/release-manifest.json" >/dev/null || {
          echo "CLI artifact digest does not match release-manifest.json: $cli_artifact" >&2
          exit 1
        }
    done < <(jq -r '.artifacts[].file' "$source_dir/release-manifest.json")
    ;;
  skills)
    skill_source_commits="$work_dir/skill-source-commits.txt"
    : > "$skill_source_commits"
    while IFS= read -r manifest_file; do
      manifest_name=$(basename "$manifest_file")
      expected_name=foggy-ai-analysis
      expected_language=en
      case "$manifest_name" in
        foggy-semantic-query-*) expected_name=foggy-semantic-query ;;
        *-zh-CN-manifest.json) expected_language=zh-CN ;;
      esac
      expected_archive=${manifest_name%-manifest.json}.zip
      expected_checksum=${manifest_name%-manifest.json}-SHA256SUMS
      archive_sha256=$(sha256sum "$source_dir/$expected_archive" | cut -d' ' -f1)
      archive_bytes=$(stat -c '%s' "$source_dir/$expected_archive")
      unzip -tqq "$source_dir/$expected_archive"
      jq -e \
        --arg version "$version" \
        --arg name "$expected_name" \
        --arg language "$expected_language" \
        --arg archive "$expected_archive" \
        --arg checksum "$expected_checksum" \
        --arg archive_sha256 "$archive_sha256" \
        --argjson archive_bytes "$archive_bytes" '
        .schemaVersion == "foggy-skill-package/v1"
        and .name == $name
        and .version == $version
        and .language == $language
        and (.sourceCommit | type == "string" and length > 0)
        and .package.file == $archive
        and .package.root == $name
        and .package.sha256 == $archive_sha256
        and .package.bytes == $archive_bytes
        and .checksums == $checksum
        and (.files | type == "array" and length > 0)
        and all(.files[];
          (keys | sort) == ["bytes", "path", "sha256"]
          and (.path | type == "string")
          and (.path | test("^[A-Za-z0-9._+/-]+$"))
          and (.path | startswith($name + "/"))
          and ((.path | contains("//")) | not)
          and (.path | split("/") | all(. != "." and . != ".." and length > 0))
          and (.sha256 | type == "string" and test("^[a-f0-9]{64}$"))
          and (.bytes | type == "number" and . >= 0 and . == floor)
        )
      ' "$manifest_file" >/dev/null || {
        echo "Skill manifest does not match the paired release contract: $manifest_name" >&2
        exit 1
      }

      manifest_entries="$work_dir/$expected_archive.manifest-entries.txt"
      archive_entries="$work_dir/$expected_archive.archive-entries.txt"
      jq -r '.files[].path' "$manifest_file" | sort > "$manifest_entries"
      duplicate_manifest_entries=$(uniq -d "$manifest_entries")
      [ -z "$duplicate_manifest_entries" ] || {
        echo "Skill manifest contains duplicate file paths: $manifest_name" >&2
        printf '%s\n' "$duplicate_manifest_entries" | sed 's/^/  /' >&2
        exit 1
      }
      unzip -Z1 "$source_dir/$expected_archive" \
        | sed '/\/$/d' | sort > "$archive_entries"
      assert_same_lines \
        "Skill archive file list for $expected_archive" \
        "$manifest_entries" "$archive_entries"

      while IFS=$'\t' read -r entry_path entry_sha256 entry_bytes; do
        actual_entry_sha256=$(unzip -p "$source_dir/$expected_archive" "$entry_path" \
          | sha256sum | cut -d' ' -f1)
        actual_entry_bytes=$(unzip -p "$source_dir/$expected_archive" "$entry_path" \
          | wc -c | tr -d '[:space:]')
        if [ "$actual_entry_sha256" != "$entry_sha256" ] \
          || [ "$actual_entry_bytes" != "$entry_bytes" ]; then
          echo "Skill archive entry digest mismatch: $expected_archive!/$entry_path" >&2
          exit 1
        fi
      done < <(jq -r '.files[] | [.path, .sha256, (.bytes | tostring)] | @tsv' \
        "$manifest_file")
      jq -r '.sourceCommit' "$manifest_file" >> "$skill_source_commits"
    done < <(find "$source_dir" -mindepth 1 -maxdepth 1 -type f -name '*-manifest.json' | sort)
    if [ "$(sort -u "$skill_source_commits" | wc -l)" -ne 1 ]; then
      if [ "$version" = 0.1.17 ] \
        && [ "$(sort -u "$skill_source_commits" | wc -l)" -eq 2 ] \
        && jq -e '.sourceCommit == "621032997d257513b4d0070f34acfd0cc7e56d40"' \
          "$source_dir/foggy-ai-analysis-skill-0.1.17-manifest.json" >/dev/null \
        && jq -e '.sourceCommit == "0598028857d954b1588abe9f31030237cc140aa0"' \
          "$source_dir/foggy-semantic-query-skill-0.1.17-manifest.json" >/dev/null \
        && { [ ! -f "$source_dir/foggy-ai-analysis-skill-0.1.17-zh-CN-manifest.json" ] \
          || jq -e '.sourceCommit == "621032997d257513b4d0070f34acfd0cc7e56d40"' \
            "$source_dir/foggy-ai-analysis-skill-0.1.17-zh-CN-manifest.json" >/dev/null; }; then
        echo "Known Skill v0.1.17 split-source release detected; later releases must be atomic." >&2
      else
        echo "Paired Skill manifests do not share one committed source." >&2
        exit 1
      fi
    fi
    ;;
esac

if [ "$validate_assets_only" = true ]; then
  printf 'Release asset validation complete: component=%s version=%s assets=%s\n' \
    "$component" "$version" "$(wc -l < "$actual_assets")"
  exit 0
fi

if [ -z "$obsutil_bin" ]; then
  obsutil_bin=$(command -v obsutil || true)
fi
[ -n "$obsutil_bin" ] && [ -x "$obsutil_bin" ] || {
  echo "obsutil is required; pass --obsutil or install it on PATH" >&2
  exit 1
}

obs_access_key=$(printenv OBS_ACCESS_KEY_ID || true)
obs_secret_key=$(printenv OBS_SECRET_ACCESS_KEY || true)
obsutil_config=$(printenv OBSUTIL_CONFIG || true)
obsutil_config_arg=
if [ -n "$obs_access_key" ] || [ -n "$obs_secret_key" ]; then
  [ -n "$obs_access_key" ] && [ -n "$obs_secret_key" ] || {
    echo "OBS_ACCESS_KEY_ID and OBS_SECRET_ACCESS_KEY must be supplied together" >&2
    exit 1
  }
  [ -n "$obsutil_config" ] || obsutil_config="$work_dir/obsutilconfig"
  "$obsutil_bin" config -config="$obsutil_config" -i="$obs_access_key" -k="$obs_secret_key" -e="$endpoint" >/dev/null
  obsutil_config_arg="-config=$obsutil_config"
fi

obs_cp() {
  if [ -n "$obsutil_config_arg" ]; then
    "$obsutil_bin" cp "$1" "$2" "$obsutil_config_arg" -acl=public-read -vmd5 -f >/dev/null
  else
    "$obsutil_bin" cp "$1" "$2" -acl=public-read -vmd5 -f >/dev/null
  fi
}

case "$component" in
  cli) component_prefix="$prefix/cli/$version" ;;
  skills) component_prefix="$prefix/skills/$version" ;;
  launcher) component_prefix="$prefix/launcher/$tag" ;;
esac

asset_count=0
while IFS= read -r -d '' asset_file; do
  asset_name=$(basename "$asset_file")
  obs_cp "$asset_file" "obs://$bucket/$component_prefix/$asset_name"
  asset_count=$((asset_count + 1))
done < <(find "$source_dir" -mindepth 1 -maxdepth 1 -type f -not -name '.*' -print0 | sort -z)
[ "$asset_count" -gt 0 ] || { echo "No release assets found in $source_dir" >&2; exit 1; }

asset_json="$work_dir/assets.json"
printf '%s\n' '{}' > "$asset_json"
while IFS= read -r -d '' asset_file; do
  asset_name=$(basename "$asset_file")
  sha256=$(sha256sum "$asset_file" | cut -d' ' -f1)
  size=$(stat -c '%s' "$asset_file")
  public_url="$public_base_url/$component_prefix/$asset_name"
  jq --arg name "$asset_name" --arg sha256 "$sha256" --argjson size "$size" \
    --arg url "$public_url" \
    '. + {($name): {sha256: $sha256, size: $size, url: $url}}' \
    "$asset_json" > "$work_dir/assets.next.json"
  mv "$work_dir/assets.next.json" "$asset_json"
done < <(find "$source_dir" -mindepth 1 -maxdepth 1 -type f -not -name '.*' -print0 | sort -z)

release_url="https://github.com/$repo/releases/tag/$tag"
now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
component_index_json="$work_dir/component-index.json"
jq -n \
  --arg schema 'foggy-runtime-component-release/v1' \
  --arg now "$now" \
  --arg bucket "$bucket" \
  --arg prefix "$prefix" \
  --arg component "$component" \
  --arg version "$version" \
  --arg tag "$tag" \
  --arg repo "$repo" \
  --arg release_url "$release_url" \
  --arg obs_prefix "$component_prefix" \
  --slurpfile assets "$asset_json" \
  '{
     schemaVersion: $schema,
     component: $component,
     updatedAt: $now,
     bucket: $bucket,
     prefix: $prefix,
     release: {
       version: $version,
       tag: $tag,
       repository: $repo,
       releaseUrl: $release_url,
       obsPrefix: $obs_prefix,
       assets: $assets[0]
     }
   }' > "$component_index_json"

component_index_prefix="$prefix/latest"
component_index_url="$public_base_url/$component_index_prefix/$component.json"
obs_cp "$component_index_json" "obs://$bucket/$component_index_prefix/$component.json"

index_url="$public_base_url/$prefix/latest.json"
legacy_index_json="$work_dir/latest.legacy.json"

fetch_public_json() {
  local url=$1
  local output_file=$2
  local cache_bust
  local http_code
  cache_bust="$(date +%s)-$$-$RANDOM"
  http_code=$(curl --silent --show-error --location --retry 2 --retry-all-errors \
    --proto "$public_curl_proto" --proto-redir "$public_curl_proto" \
    --header 'Cache-Control: no-cache' \
    --output "$output_file" --write-out '%{http_code}' \
    "$url?foggy_no_cache=$cache_bust") || return 2
  printf '%s' "$http_code"
}

legacy_http_code=$(fetch_public_json "$index_url" "$legacy_index_json") || {
  echo "Unable to read the current OBS release index: $index_url" >&2
  exit 1
}

component_index_verified=false
for component_verify_attempt in 1 2 3 4; do
  published_component_json="$work_dir/component-index.published.json"
  published_component_http_code=$(
    fetch_public_json "$component_index_url" "$published_component_json"
  ) || published_component_http_code=000
  if [ "$published_component_http_code" = 200 ] \
    && cmp -s \
      <(jq -S . "$component_index_json") \
      <(jq -S . "$published_component_json"); then
    component_index_verified=true
    break
  fi
  echo "OBS component index is not publicly consistent yet (attempt $component_verify_attempt/4)." >&2
  sleep 2
done
[ "$component_index_verified" = true ] || {
  echo "Unable to verify the public OBS component index: $component_index_url" >&2
  exit 1
}

case "$legacy_http_code" in
  200)
    jq -e \
      '.schemaVersion == "foggy-runtime-release-index/v1"
       and (.components | type == "object")' \
      "$legacy_index_json" >/dev/null || {
      echo "OBS release index has an unsupported schema: $index_url" >&2
      exit 1
    }
    ;;
  404)
    jq -n '{schemaVersion: "foggy-runtime-release-index/v1", components: {}}' > "$legacy_index_json"
    ;;
  *)
    echo "Unexpected HTTP $legacy_http_code while reading OBS release index: $index_url" >&2
    exit 1
    ;;
esac

read_component_release() {
  local requested_component=$1
  local output_file=$2
  local remote_json="$work_dir/component-$requested_component.remote.json"
  local remote_url="$public_base_url/$component_index_prefix/$requested_component.json"
  local remote_http_code

  if [ "$requested_component" = "$component" ]; then
    jq '.release' "$component_index_json" > "$output_file"
    return
  fi

  remote_http_code=$(fetch_public_json "$remote_url" "$remote_json") || {
    echo "Unable to read the $requested_component OBS component index: $remote_url" >&2
    exit 1
  }
  case "$remote_http_code" in
    200)
      jq -e --arg component "$requested_component" '
        .schemaVersion == "foggy-runtime-component-release/v1"
        and .component == $component
        and (.release.version | type == "string" and length > 0)
        and (.release.tag | type == "string" and length > 0)
        and (.release.repository | type == "string" and length > 0)
        and (.release.releaseUrl | type == "string" and length > 0)
        and (.release.obsPrefix | type == "string" and length > 0)
        and (.release.assets | type == "object" and length > 0)
      ' "$remote_json" >/dev/null || {
        echo "OBS component index has an unsupported schema: $remote_url" >&2
        exit 1
      }
      jq '.release' "$remote_json" > "$output_file"
      ;;
    404)
      if jq -e --arg component "$requested_component" \
        '.components[$component] | type == "object"' \
        "$legacy_index_json" >/dev/null; then
        jq --arg component "$requested_component" \
          '.components[$component]' "$legacy_index_json" > "$output_file"
      else
        : > "$output_file"
      fi
      ;;
    *)
      echo "Unexpected HTTP $remote_http_code while reading OBS component index: $remote_url" >&2
      exit 1
      ;;
  esac
}

assemble_release_index() {
  local output_file=$1
  local components_json="$work_dir/components.json"
  local release_json
  local requested_component
  printf '%s\n' '{}' > "$components_json"
  for requested_component in cli skills launcher; do
    release_json="$work_dir/component-$requested_component.release.json"
    read_component_release "$requested_component" "$release_json"
    if [ -s "$release_json" ]; then
      jq --arg component "$requested_component" --slurpfile release "$release_json" \
        '. + {($component): $release[0]}' "$components_json" > "$work_dir/components.next.json"
      mv "$work_dir/components.next.json" "$components_json"
    fi
  done
  jq -n \
    --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg bucket "$bucket" \
    --arg prefix "$prefix" \
    --slurpfile components "$components_json" \
    '{
       schemaVersion: "foggy-runtime-release-index/v1",
       components: $components[0],
       updatedAt: $now,
       bucket: $bucket,
       prefix: $prefix
     }' > "$output_file"
}

index_verified=false
for reconcile_attempt in 1 2 3 4; do
  index_json="$work_dir/latest.candidate.json"
  assemble_release_index "$index_json"
  obs_cp "$index_json" "obs://$bucket/$prefix/latest.json"

  published_index_json="$work_dir/latest.published.json"
  published_http_code=$(fetch_public_json "$index_url" "$published_index_json") || published_http_code=000
  authoritative_index_json="$work_dir/latest.authoritative.json"
  assemble_release_index "$authoritative_index_json"
  if [ "$published_http_code" = 200 ] \
    && jq -e '.schemaVersion == "foggy-runtime-release-index/v1" and (.components | type == "object")' \
      "$published_index_json" >/dev/null \
    && cmp -s \
      <(jq -S '.components' "$published_index_json") \
      <(jq -S '.components' "$authoritative_index_json"); then
    index_verified=true
    break
  fi
  echo "OBS shared index changed during publication; reconciling (attempt $reconcile_attempt/4)." >&2
  sleep 2
done
[ "$index_verified" = true ] || {
  echo "Unable to publish a shared OBS index consistent with the component indexes." >&2
  exit 1
}

printf 'OBS sync complete: component=%s version=%s assets=%s\n' "$component" "$version" "$asset_count"
printf 'OBS prefix: obs://%s/%s\n' "$bucket" "$component_prefix"
printf 'Component index: %s\n' "$component_index_url"
printf 'Public index: %s\n' "$index_url"
