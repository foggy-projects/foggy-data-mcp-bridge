#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

VERSION=''

usage() {
    cat <<'EOF'
Usage:
  maven-central-verify.sh --version <published-version>

Checks every published POM/JAR/sources/javadoc file derived from the root
reactor, all four checksum sidecars, and every detached GPG signature.
The .asc files themselves are not expected to have checksum sidecars on Maven Central.
EOF
}

while (($#)); do
    case "$1" in
        --version) VERSION=${2:?missing value for --version}; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; release_die "unknown option: $1" ;;
    esac
done

[[ -n "$VERSION" ]] || { usage >&2; release_die '--version is required'; }
validate_release_version "$VERSION"

for command_name in curl gpg rg awk sed sha256sum md5sum sha1sum sha512sum; do
    require_command "$command_name"
done

tmp_root=$(mktemp -d -t "foggy-central-verify-${VERSION}.XXXXXX")
trap 'rm -rf -- "$tmp_root"' EXIT

base_url="https://repo.maven.apache.org/maven2/com/foggysource"
artifact_count=0
main_files_checked=0
checksum_files_checked=0
signatures_checked=0
failures=0

check_digest() {
    local algorithm=$1
    local file=$2
    case "$algorithm" in
        md5) md5sum "$file" | awk '{print $1}' ;;
        sha1) sha1sum "$file" | awk '{print $1}' ;;
        sha256) sha256sum "$file" | awk '{print $1}' ;;
        sha512) sha512sum "$file" | awk '{print $1}' ;;
        *) release_die "unsupported checksum algorithm: $algorithm" ;;
    esac
}

verify_main_file() {
    local artifact=$1
    local filename=$2
    local artifact_dir="$tmp_root/$artifact"
    local local_file="$artifact_dir/$filename"
    local url="$base_url/$artifact/$VERSION/$filename"
    local signature_file="$local_file.asc"
    local algorithm
    local remote_digest
    local local_digest

    mkdir -p -- "$artifact_dir"
    main_files_checked=$((main_files_checked + 1))
    if ! curl -L --retry 2 --max-time 60 -fsS "$url" -o "$local_file"; then
        printf 'HTTP_FAILURE %s\n' "$url" >&2
        failures=$((failures + 1))
        return 0
    fi

    if [[ "$filename" == *.pom ]] && ! rg -q --fixed-strings "<version>$VERSION</version>" "$local_file"; then
        printf 'POM_VERSION_FAILURE %s\n' "$url" >&2
        failures=$((failures + 1))
    fi

    for algorithm in md5 sha1 sha256 sha512; do
        checksum_files_checked=$((checksum_files_checked + 1))
        if ! curl -L --retry 2 --max-time 60 -fsS "$url.$algorithm" -o "$local_file.$algorithm"; then
            printf 'CHECKSUM_HTTP_FAILURE %s.%s\n' "$url" "$algorithm" >&2
            failures=$((failures + 1))
            continue
        fi
        remote_digest=$(awk 'NF {print $1; exit}' "$local_file.$algorithm")
        local_digest=$(check_digest "$algorithm" "$local_file")
        [[ "$remote_digest" == "$local_digest" ]] || {
            printf 'CHECKSUM_MISMATCH %s.%s remote=%s local=%s\n' "$url" "$algorithm" "$remote_digest" "$local_digest" >&2
            failures=$((failures + 1))
        }
    done

    signatures_checked=$((signatures_checked + 1))
    if ! curl -L --retry 2 --max-time 60 -fsS "$url.asc" -o "$signature_file"; then
        printf 'SIGNATURE_HTTP_FAILURE %s.asc\n' "$url" >&2
        failures=$((failures + 1))
        return 0
    fi
    if ! gpg --batch --verify "$signature_file" "$local_file" >/dev/null 2>&1; then
        printf 'SIGNATURE_INVALID %s.asc\n' "$url" >&2
        failures=$((failures + 1))
    fi
}

while IFS= read -r artifact; do
    artifact_count=$((artifact_count + 1))
    verify_main_file "$artifact" "$artifact-$VERSION.pom"
    if [[ "$artifact" != foggy-data-mcp-bridge ]]; then
        verify_main_file "$artifact" "$artifact-$VERSION.jar"
        verify_main_file "$artifact" "$artifact-$VERSION-sources.jar"
        verify_main_file "$artifact" "$artifact-$VERSION-javadoc.jar"
    fi
done < <(published_artifacts)

printf 'artifacts=%s main_files=%s checksum_files=%s signatures=%s failures=%s\n' \
    "$artifact_count" "$main_files_checked" "$checksum_files_checked" "$signatures_checked" "$failures"
(( failures == 0 )) || exit 1
