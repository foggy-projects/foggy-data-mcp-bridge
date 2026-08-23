#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

VERSION=''
GPG_KEY=${FOGGY_GPG_KEY:-}
CONFIRM=0
SKIP_TESTS=0
LOG_FILE=''

usage() {
    cat <<'EOF'
Usage:
  maven-central-publish.sh --version <release-version> --gpg-key <key-id> --confirm [--skip-tests]

This script publishes only. Run maven-central-verify.sh and then
maven-central-tag.sh after the deployment reaches PUBLISHED.

Options:
  --version <version>  Release coordinate, for example 9.3.0
  --gpg-key <key>      Signing key id or fingerprint; may use FOGGY_GPG_KEY
  --confirm            Required because this command uploads to Maven Central
  --skip-tests         Add the explicit skip flags used by the approved release lane
  --log <file>         Save Maven output to this file; default is a temporary file
EOF
}

while (($#)); do
    case "$1" in
        --version) VERSION=${2:?missing value for --version}; shift 2 ;;
        --gpg-key) GPG_KEY=${2:?missing value for --gpg-key}; shift 2 ;;
        --confirm) CONFIRM=1; shift ;;
        --skip-tests) SKIP_TESTS=1; shift ;;
        --log) LOG_FILE=${2:?missing value for --log}; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; release_die "unknown option: $1" ;;
    esac
done

[[ -n "$VERSION" ]] || { usage >&2; release_die '--version is required'; }
[[ -n "$GPG_KEY" ]] || { usage >&2; release_die '--gpg-key or FOGGY_GPG_KEY is required'; }
(( CONFIRM == 1 )) || release_die 'publishing is disabled without --confirm'
validate_release_version "$VERSION"

"$SCRIPT_DIR/maven-central-preflight.sh" --version "$VERSION" --gpg-key "$GPG_KEY"

gpg_executable=${FOGGY_GPG_EXECUTABLE:-/usr/bin/gpg}
if [[ ! -x "$gpg_executable" ]]; then
    gpg_executable=$(command -v gpg)
fi

if [[ -z "$LOG_FILE" ]]; then
    LOG_FILE=$(mktemp -t "foggy-maven-central-${VERSION}.XXXXXX.log")
else
    mkdir -p -- "$(dirname -- "$LOG_FILE")"
fi

maven_args=(-B -P 'release,!multi-db' "-Dgpg.executable=$gpg_executable" "-Dgpg.keyname=$GPG_KEY")
if (( SKIP_TESTS == 1 )); then
    maven_args+=(
        -DskipUnitTests=true
        -DskipTests=true
        -DskipITs=true
    )
fi

release_info "publishing $VERSION to Maven Central"
release_info "Maven output log: $LOG_FILE"
set +e
mvn "${maven_args[@]}" clean deploy 2>&1 | tee "$LOG_FILE"
maven_status=${PIPESTATUS[0]}
set -e
(( maven_status == 0 )) || release_die "Maven deploy failed; log preserved at $LOG_FILE"

deployment_id=$(rg -o -i 'deployment[^[:alnum:]]{0,24}id[^0-9a-f]{0,8}[0-9a-f]{8}-[0-9a-f-]{27,28}' "$LOG_FILE" |
    rg -o '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | tail -n 1 || true)
if [[ -z "$deployment_id" ]]; then
    release_warn "Maven deploy succeeded but deployment id was not found in $LOG_FILE"
    release_warn 'Use the Central Portal deployment id, then run maven-central-verify.sh and maven-central-tag.sh manually.'
    exit 0
fi

read_central_credentials
wait_for_central_published "$deployment_id"
release_info "deployment_id=$deployment_id"
release_info 'Next: run maven-central-verify.sh, then maven-central-tag.sh with this deployment id.'
