#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

FROM_VERSION=''
TO_VERSION=''
APPLY=0

usage() {
    cat <<'EOF'
Usage:
  set-engine-version.sh --from <version> --to <version> [--apply]

By default this is a dry run. --apply changes only exact project-version and
foggy-framework.version references in POM files. It does not commit or push.

Examples:
  set-engine-version.sh --from 9.3.0-SNAPSHOT --to 9.3.0 --apply
  set-engine-version.sh --from 9.3.0 --to 9.4.0-SNAPSHOT --apply
EOF
}

while (($#)); do
    case "$1" in
        --from) FROM_VERSION=${2:?missing value for --from}; shift 2 ;;
        --to) TO_VERSION=${2:?missing value for --to}; shift 2 ;;
        --apply) APPLY=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; release_die "unknown option: $1" ;;
    esac
done

[[ -n "$FROM_VERSION" && -n "$TO_VERSION" ]] || { usage >&2; release_die '--from and --to are required'; }
[[ "$FROM_VERSION" != "$TO_VERSION" ]] || release_die '--from and --to must differ'
validate_engine_version "$FROM_VERSION"
validate_engine_version "$TO_VERSION"
assert_clean_worktree

old_project_ref="<version>${FROM_VERSION}</version>"
old_framework_ref="<foggy-framework.version>${FROM_VERSION}</foggy-framework.version>"
old_hits_project=$(rg -o --glob 'pom.xml' --fixed-strings "$old_project_ref" "$REPO_ROOT" | wc -l || true)
old_hits_framework=$(rg -o --glob 'pom.xml' --fixed-strings "$old_framework_ref" "$REPO_ROOT" | wc -l || true)
old_hits=$((old_hits_project + old_hits_framework))
(( old_hits > 0 )) || release_die "no POM version references found for $FROM_VERSION"

mapfile -t pom_files < <(
    {
        rg -l --glob 'pom.xml' --fixed-strings "$old_project_ref" "$REPO_ROOT" || true
        rg -l --glob 'pom.xml' --fixed-strings "$old_framework_ref" "$REPO_ROOT" || true
    } | sort -u
)

release_info "from=$FROM_VERSION to=$TO_VERSION references=$old_hits files=${#pom_files[@]}"
printf '%s\n' "${pom_files[@]}"
(( APPLY == 1 )) || exit 0

export FOGGY_RELEASE_FROM_VERSION=$FROM_VERSION
export FOGGY_RELEASE_TO_VERSION=$TO_VERSION
for pom_file in "${pom_files[@]}"; do
    perl -0pi -e '
        s{<version>\Q$ENV{FOGGY_RELEASE_FROM_VERSION}\E</version>}{<version>$ENV{FOGGY_RELEASE_TO_VERSION}</version>}g;
        s{<foggy-framework\.version>\Q$ENV{FOGGY_RELEASE_FROM_VERSION}\E</foggy-framework\.version>}{<foggy-framework.version>$ENV{FOGGY_RELEASE_TO_VERSION}</foggy-framework.version>}g;
    ' "$pom_file"
done

new_project_ref="<version>${TO_VERSION}</version>"
new_framework_ref="<foggy-framework.version>${TO_VERSION}</foggy-framework.version>"
remaining_old=$(rg -o --glob 'pom.xml' --fixed-strings "$old_project_ref" "$REPO_ROOT" | wc -l || true)
remaining_old_framework=$(rg -o --glob 'pom.xml' --fixed-strings "$old_framework_ref" "$REPO_ROOT" | wc -l || true)
remaining_old=$((remaining_old + remaining_old_framework))
new_hits=$(rg -o --glob 'pom.xml' --fixed-strings "$new_project_ref" "$REPO_ROOT" | wc -l || true)
new_hits_framework=$(rg -o --glob 'pom.xml' --fixed-strings "$new_framework_ref" "$REPO_ROOT" | wc -l || true)
new_hits=$((new_hits + new_hits_framework))
(( remaining_old == 0 )) || release_die "old version references remain after update: $remaining_old"
(( new_hits == old_hits )) || release_die "version reference count changed unexpectedly: $new_hits != $old_hits"
git -C "$REPO_ROOT" diff --check
release_info "updated $new_hits version references"
release_info 'review the diff, then commit and push explicitly'
