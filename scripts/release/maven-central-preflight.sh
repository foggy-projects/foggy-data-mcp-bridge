#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

VERSION=''
TAG=''
GPG_KEY=${FOGGY_GPG_KEY:-}
ALLOW_PUBLISHED=0

usage() {
    cat <<'EOF'
Usage:
  maven-central-preflight.sh --version <release-version> --gpg-key <key-id> [--tag <tag>]

Options:
  --version <version>  Release coordinate, for example 9.3.0
  --gpg-key <key>      Signing key id or fingerprint; may use FOGGY_GPG_KEY
  --tag <tag>          Release tag, default v<version>
  --published          Check that the public POM exists instead of requiring 404
EOF
}

while (($#)); do
    case "$1" in
        --version) VERSION=${2:?missing value for --version}; shift 2 ;;
        --gpg-key) GPG_KEY=${2:?missing value for --gpg-key}; shift 2 ;;
        --tag) TAG=${2:?missing value for --tag}; shift 2 ;;
        --published) ALLOW_PUBLISHED=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; release_die "unknown option: $1" ;;
    esac
done

[[ -n "$VERSION" ]] || { usage >&2; release_die '--version is required'; }
[[ -n "$GPG_KEY" ]] || { usage >&2; release_die '--gpg-key or FOGGY_GPG_KEY is required'; }
[[ -n "$TAG" ]] || TAG="v$VERSION"
validate_release_version "$VERSION"

for command_name in git mvn curl gpg rg sed awk base64; do
    require_command "$command_name"
done

assert_clean_worktree
assert_release_branch
assert_branch_pushed "$(current_branch)"
assert_release_poms "$VERSION"
assert_tag_absent "$TAG"
gpg_preflight "$GPG_KEY"
read_central_credentials

public_pom="https://repo.maven.apache.org/maven2/com/foggysource/foggy-data-mcp-bridge/$VERSION/foggy-data-mcp-bridge-$VERSION.pom"
public_code=$(curl -L --retry 2 --max-time 45 -sS -o /dev/null -w '%{http_code}' "$public_pom")
if (( ALLOW_PUBLISHED == 0 )); then
    [[ "$public_code" == '404' ]] || release_die "release version already exists or Central is unavailable: HTTP $public_code $public_pom"
else
    [[ "$public_code" == '200' ]] || release_die "published release POM is not public yet: HTTP $public_code $public_pom"
fi

release_info 'preflight passed'
release_info "branch=$(current_branch)"
release_info "version=$VERSION"
release_info "tag=$TAG"
release_info "gpg_key=$GPG_KEY"
release_info "maven_settings=$CENTRAL_SETTINGS_FILE"
release_info "central_pom_http=$public_code"
release_info 'no existing local or remote release tag found'
