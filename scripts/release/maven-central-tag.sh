#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

VERSION=''
TAG=''
DEPLOYMENT_ID=''
GPG_KEY=${FOGGY_GPG_KEY:-}
CONFIRM=0

usage() {
    cat <<'EOF'
Usage:
  maven-central-tag.sh --version <published-version> --gpg-key <key-id> \
    [--deployment-id <central-id>] --confirm

The script repeats the after-publication preflight, verifies all public
artifacts/signatures, then creates and pushes only the new annotated tag.
EOF
}

while (($#)); do
    case "$1" in
        --version) VERSION=${2:?missing value for --version}; shift 2 ;;
        --tag) TAG=${2:?missing value for --tag}; shift 2 ;;
        --gpg-key) GPG_KEY=${2:?missing value for --gpg-key}; shift 2 ;;
        --deployment-id) DEPLOYMENT_ID=${2:?missing value for --deployment-id}; shift 2 ;;
        --confirm) CONFIRM=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; release_die "unknown option: $1" ;;
    esac
done

[[ -n "$VERSION" ]] || { usage >&2; release_die '--version is required'; }
[[ -n "$GPG_KEY" ]] || { usage >&2; release_die '--gpg-key or FOGGY_GPG_KEY is required'; }
[[ -n "$TAG" ]] || TAG="v$VERSION"
(( CONFIRM == 1 )) || release_die 'tag creation/push is disabled without --confirm'
validate_release_version "$VERSION"

"$SCRIPT_DIR/maven-central-preflight.sh" \
    --version "$VERSION" --tag "$TAG" --gpg-key "$GPG_KEY" --published

if [[ -n "$DEPLOYMENT_ID" ]]; then
    require_command jq
    read_central_credentials
    deployment_payload=$(central_status "$DEPLOYMENT_ID")
    deployment_state=$(printf '%s\n' "$deployment_payload" | jq -r '.deploymentState // empty')
    [[ "$deployment_state" == PUBLISHED ]] || {
        printf '%s\n' "$deployment_payload" | jq -c '{deploymentState,errors}' >&2
        release_die "Central deployment is not PUBLISHED: $DEPLOYMENT_ID"
    }
fi

"$SCRIPT_DIR/maven-central-verify.sh" --version "$VERSION"

head_commit=$(git -C "$REPO_ROOT" rev-parse HEAD)
tag_message_deployment=${DEPLOYMENT_ID:-not-supplied}
git -C "$REPO_ROOT" tag -a "$TAG" "$head_commit" \
    -m "Release $TAG" \
    -m "Maven Central deployment: $tag_message_deployment" \
    -m "Published root POM and reactor JAR modules at $VERSION." \
    -m "Public POM, JAR, sources, javadoc, checksums, and GPG signatures verified."
git -C "$REPO_ROOT" push origin "$TAG"

remote_target=$(git -C "$REPO_ROOT" ls-remote --tags origin "refs/tags/$TAG^{}" | awk 'NR == 1 {print $1}')
[[ "$remote_target" == "$head_commit" ]] || release_die "remote tag target mismatch: $remote_target != $head_commit"
release_info "annotated tag $TAG pushed to origin at $head_commit"
