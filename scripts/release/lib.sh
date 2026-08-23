#!/usr/bin/env bash
# Shared helpers for the Foggy Java engine Maven Central release scripts.

set -euo pipefail

if [[ "${FOGGY_RELEASE_LIB_LOADED:-0}" == "1" ]]; then
    return 0
fi
FOGGY_RELEASE_LIB_LOADED=1

FOGGY_RELEASE_SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(git -C "$FOGGY_RELEASE_SCRIPT_DIR/../.." rev-parse --show-toplevel 2>/dev/null || true)

if [[ -z "$REPO_ROOT" ]]; then
    printf '%s\n' 'error: release scripts must live inside a Git worktree' >&2
    exit 1
fi

release_die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

release_warn() {
    printf 'warning: %s\n' "$*" >&2
}

release_info() {
    printf '%s\n' "$*"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || release_die "required command not found: $1"
}

validate_release_version() {
    local version=$1
    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)?$ ]] || \
        release_die "invalid release version: $version"
    [[ "$version" != *SNAPSHOT* ]] || release_die "Maven Central releases cannot use a SNAPSHOT version: $version"
}

validate_engine_version() {
    local version=$1
    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]] || \
        release_die "invalid engine version: $version"
}

assert_clean_worktree() {
    local status
    status=$(git -C "$REPO_ROOT" status --porcelain)
    [[ -z "$status" ]] || release_die "worktree is not clean; commit or stash changes before continuing"
}

current_branch() {
    git -C "$REPO_ROOT" branch --show-current
}

assert_release_branch() {
    local branch
    branch=$(current_branch)
    [[ "$branch" == "main" || "$branch" == maintenance/* ]] || \
        release_die "release must run from main or maintenance/*; current branch is $branch"
}

assert_branch_pushed() {
    local branch=$1
    local remote_head
    local local_head
    remote_head=$(git -C "$REPO_ROOT" ls-remote --refs origin "refs/heads/$branch" | awk 'NR == 1 {print $1}')
    [[ -n "$remote_head" ]] || release_die "origin does not contain branch $branch"
    local_head=$(git -C "$REPO_ROOT" rev-parse HEAD)
    [[ "$local_head" == "$remote_head" ]] || \
        release_die "HEAD $local_head is not equal to origin/$branch $remote_head"
}

tag_exists_local() {
    git -C "$REPO_ROOT" show-ref --verify --quiet "refs/tags/$1"
}

tag_exists_remote() {
    git -C "$REPO_ROOT" ls-remote --exit-code --refs origin "refs/tags/$1" >/dev/null 2>&1
}

assert_tag_absent() {
    local tag=$1
    if tag_exists_local "$tag"; then
        release_die "local tag already exists: $tag"
    fi
    if tag_exists_remote "$tag"; then
        release_die "remote tag already exists: $tag"
    fi
}

assert_release_poms() {
    local version=$1
    local root_pom="$REPO_ROOT/pom.xml"
    local project_ref="<version>${version}</version>"
    local framework_ref="<foggy-framework.version>${version}</foggy-framework.version>"
    local stale_snapshots

    rg -q --fixed-strings "$project_ref" "$root_pom" || \
        release_die "root POM project version is not $version"
    rg -q --fixed-strings "$framework_ref" "$root_pom" || \
        release_die "root POM foggy-framework.version is not $version"

    stale_snapshots=$(rg -n --glob 'pom.xml' --glob '!target/**' 'SNAPSHOT' "$REPO_ROOT" || true)
    [[ -z "$stale_snapshots" ]] || {
        printf '%s\n' "$stale_snapshots" >&2
        release_die 'release POMs still contain SNAPSHOT versions'
    }

    local direct_refs
    direct_refs=$(rg -o --glob 'pom.xml' --fixed-strings "$project_ref" "$REPO_ROOT" | wc -l)
    [[ "$direct_refs" -gt 0 ]] || release_die "no module POM contains $project_ref"
}

module_paths() {
    sed -n '/<modules>/,/<\/modules>/p' "$REPO_ROOT/pom.xml" |
        sed -n 's:^[[:space:]]*<module>\(.*\)</module>[[:space:]]*$:\1:p' |
        while IFS= read -r module; do
            [[ "$module" == "build-support/foggy-coverage-report" ]] && continue
            printf '%s\n' "$module"
        done
}

published_artifacts() {
    printf '%s\n' foggy-data-mcp-bridge
    while IFS= read -r module; do
        basename -- "$module"
    done < <(module_paths)
}

module_is_pom_only() {
    local module=$1
    local pom="$REPO_ROOT/$module/pom.xml"
    rg -q '^[[:space:]]*<packaging>[[:space:]]*pom[[:space:]]*</packaging>' "$pom"
}

read_central_credentials() {
    local settings_file=${MAVEN_SETTINGS:-${HOME}/.m2/settings.xml}
    [[ -r "$settings_file" ]] || release_die "Maven settings file is not readable: $settings_file"

    local central_block
    central_block=$(sed -n '/<id>central<\/id>/,/<\/server>/p' "$settings_file")
    [[ -n "$central_block" ]] || release_die "Maven settings has no server with id central: $settings_file"

    CENTRAL_SETTINGS_FILE=$settings_file
    CENTRAL_USER=$(printf '%s\n' "$central_block" | sed -n 's:.*<username>\([^<]*\)</username>.*:\1:p' | head -n 1)
    CENTRAL_PASS=$(printf '%s\n' "$central_block" | sed -n 's:.*<password>\([^<]*\)</password>.*:\1:p' | head -n 1)
    [[ -n "$CENTRAL_USER" && -n "$CENTRAL_PASS" ]] || \
        release_die "central server credentials are incomplete in $settings_file"
    CENTRAL_AUTH=$(printf '%s:%s' "$CENTRAL_USER" "$CENTRAL_PASS" | base64 -w0)
}

gpg_preflight() {
    local key=$1
    local gpg_executable=${FOGGY_GPG_EXECUTABLE:-gpg}
    require_command "$gpg_executable"
    "$gpg_executable" --batch --list-secret-keys "$key" >/dev/null 2>&1 || \
        release_die "no usable secret GPG key found for $key"
    printf 'foggy-release-signing-preflight\n' |
        "$gpg_executable" --batch --yes --pinentry-mode loopback \
            --local-user "$key" --armor --detach-sign --output /dev/null || \
        release_die "GPG signing preflight failed for $key"
}

central_status() {
    local deployment_id=$1
    curl -fsS -X POST -G -H "Authorization: UserToken $CENTRAL_AUTH" \
        --data-urlencode 'orgId=org' \
        --data-urlencode "userId=$CENTRAL_USER" \
        --data-urlencode "id=$deployment_id" \
        https://central.sonatype.com/api/v1/publisher/status
}

central_component_published() {
    local artifact=$1
    local version=$2
    curl -fsS -G -H "Authorization: UserToken $CENTRAL_AUTH" \
        --data-urlencode 'orgId=org' \
        --data-urlencode "userId=$CENTRAL_USER" \
        --data-urlencode 'namespace=com.foggysource' \
        --data-urlencode "name=$artifact" \
        --data-urlencode "version=$version" \
        https://central.sonatype.com/api/v1/publisher/published
}

wait_for_central_published() {
    local deployment_id=$1
    local timeout_seconds=${FOGGY_CENTRAL_TIMEOUT_SECONDS:-1800}
    local started=$SECONDS
    local payload
    local state

    require_command jq
    while (( SECONDS - started < timeout_seconds )); do
        payload=$(central_status "$deployment_id")
        state=$(printf '%s\n' "$payload" | jq -r '.deploymentState // empty')
        case "$state" in
            PUBLISHED)
                release_info "Central deployment $deployment_id is PUBLISHED"
                return 0
                ;;
            FAILED|FAILED_VALIDATION|PUBLISHING_FAILED)
                printf '%s\n' "$payload" | jq -c '{deploymentState,errors}' >&2
                release_die "Central deployment $deployment_id failed"
                ;;
            *)
                release_info "Central deployment $deployment_id state: ${state:-unknown}"
                ;;
        esac
        sleep 10
    done
    release_die "Central deployment $deployment_id did not reach PUBLISHED within ${timeout_seconds}s"
}
