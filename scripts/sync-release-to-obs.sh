#!/usr/bin/env bash

set -euo pipefail

component=
tag=
repo=$(printenv GITHUB_REPOSITORY || true)
version=
source_dir=
obsutil_bin=$(printenv OBSUTIL_BIN || true)
bucket=$(printenv OBS_BUCKET || true)
prefix=$(printenv OBS_PREFIX || true)
endpoint=$(printenv OBS_ENDPOINT || true)
[ -n "$bucket" ] || bucket=obs-fe55
[ -n "$prefix" ] || prefix=foggy-runtime
[ -n "$endpoint" ] || endpoint=obs.cn-north-4.myhuaweicloud.com

usage() {
  echo "Usage: $0 --component cli|skills|launcher --tag TAG [--repo OWNER/REPO] [--version VERSION] [--source-dir DIR] [--obsutil PATH]"
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

if [ -z "$obsutil_bin" ]; then
  obsutil_bin=$(command -v obsutil || true)
fi
[ -n "$obsutil_bin" ] && [ -x "$obsutil_bin" ] || {
  echo "obsutil is required; pass --obsutil or install it on PATH" >&2
  exit 1
}

tmp_base=$(printenv RUNNER_TEMP || true)
[ -n "$tmp_base" ] || tmp_base=/tmp
work_dir=$(mktemp -d "$tmp_base/foggy-release-obs.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT

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
checksum_count=0
while IFS= read -r -d '' checksum_file; do
  (cd "$source_dir" && sha256sum -c "$(basename "$checksum_file")")
  checksum_count=$((checksum_count + 1))
done < <(find "$source_dir" -mindepth 1 -maxdepth 1 -type f -name '*SHA256SUMS' -print0)
[ "$checksum_count" -gt 0 ] || {
  echo "Release assets do not contain a SHA256SUMS file: $source_dir" >&2
  exit 1
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
  public_url="https://$bucket.$endpoint/$component_prefix/$asset_name"
  jq --arg name "$asset_name" --arg sha256 "$sha256" --argjson size "$size" \
    --arg url "$public_url" \
    '. + {($name): {sha256: $sha256, size: $size, url: $url}}' \
    "$asset_json" > "$work_dir/assets.next.json"
  mv "$work_dir/assets.next.json" "$asset_json"
done < <(find "$source_dir" -mindepth 1 -maxdepth 1 -type f -not -name '.*' -print0 | sort -z)

index_url="https://$bucket.$endpoint/$prefix/latest.json"
index_json="$work_dir/latest.json"
index_http_code=$(curl --silent --show-error --location --retry 2 --retry-all-errors \
  --proto '=https' --proto-redir '=https' \
  --output "$index_json" --write-out '%{http_code}' "$index_url") || {
  echo "Unable to read the current OBS release index: $index_url" >&2
  exit 1
}
case "$index_http_code" in
  200) ;;
  404)
    jq -n '{schemaVersion: "foggy-runtime-release-index/v1", components: {}}' > "$index_json"
    ;;
  *)
    echo "Unexpected HTTP $index_http_code while reading OBS release index: $index_url" >&2
    exit 1
    ;;
esac
jq -e \
  '.schemaVersion == "foggy-runtime-release-index/v1"
   and (.components | type == "object")' \
  "$index_json" >/dev/null || {
  echo "OBS release index has an unsupported schema: $index_url" >&2
  exit 1
}

release_url="https://github.com/$repo/releases/tag/$tag"
now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq \
  --arg schema 'foggy-runtime-release-index/v1' \
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
  '.schemaVersion = $schema
   | .updatedAt = $now
   | .bucket = $bucket
   | .prefix = $prefix
   | .components = (.components // {})
   | .components[$component] = {
       version: $version,
       tag: $tag,
       repository: $repo,
       releaseUrl: $release_url,
       obsPrefix: $obs_prefix,
       assets: $assets[0]
     }' "$index_json" > "$work_dir/latest.updated.json"
mv "$work_dir/latest.updated.json" "$index_json"

obs_cp "$index_json" "obs://$bucket/$prefix/latest.json"

printf 'OBS sync complete: component=%s version=%s assets=%s\n' "$component" "$version" "$asset_count"
printf 'OBS prefix: obs://%s/%s\n' "$bucket" "$component_prefix"
printf 'Public index: %s\n' "$index_url"
