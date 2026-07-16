#!/usr/bin/env bash

# Optional JaCoCo instrumentation for canonical v934 lane runners.
# Callers keep normal Step 2/3 behavior when V934_COVERAGE_EXEC_ROOT is unset.

V934_COVERAGE_MAVEN_ARGS=()
V934_COVERAGE_EXEC_FILE=""
V934_COVERAGE_SESSION_ID_BASE=""
V934_COVERAGE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
V934_JACOCO_AGENT_SHA256="115e8e6e6593ca3a9892dfef695df4d487c706e59e71e64dc0ab95716ee02622"

v934_coverage_fail() {
  echo "[v934-coverage-runner] ERROR: $*" >&2
  return 1
}

v934_coverage_enabled() {
  [[ -n "${V934_COVERAGE_EXEC_ROOT:-}" ]]
}

v934_coverage_configure() {
  local runner_kind="${1:-}"
  local lane_token="${2:-}"
  local exec_root session_prefix file_name expected_exec_root variable_name variable_value

  V934_COVERAGE_MAVEN_ARGS=()
  V934_COVERAGE_EXEC_FILE=""
  V934_COVERAGE_SESSION_ID_BASE=""
  v934_coverage_enabled || return 0

  [[ "$runner_kind" == "ut" || "$runner_kind" == "it" ]] ||
    { v934_coverage_fail "runner kind must be ut or it"; return 1; }
  [[ "$lane_token" =~ ^[a-z0-9][a-z0-9._-]*$ ]] ||
    { v934_coverage_fail "unsafe lane token: $lane_token"; return 1; }
  exec_root="${V934_COVERAGE_EXEC_ROOT}"
  [[ "$exec_root" == /* ]] || { v934_coverage_fail "exec root must be absolute"; return 1; }
  [[ "$exec_root" != *$'\n'* && "$exec_root" != *$'\r'* ]] ||
    { v934_coverage_fail "exec root contains a line break"; return 1; }
  session_prefix="${V934_COVERAGE_SESSION_PREFIX:-}"
  [[ "$session_prefix" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] ||
    { v934_coverage_fail "invalid or missing V934_COVERAGE_SESSION_PREFIX"; return 1; }

  for variable_name in MAVEN_ARGS MAVEN_CONFIG; do
    variable_value="${!variable_name:-}"
    [[ -z "$variable_value" ]] || {
      v934_coverage_fail "$variable_name must be empty for a formal coverage run"
      return 1
    }
  done
  variable_value="${MAVEN_OPTS:-}"
  if [[ "$variable_value" =~ (^|[[:space:],])(-T|--threads)([[:space:]=,0-9C]|$) ]] ||
     [[ "$variable_value" =~ (^|[[:space:],])!?coverage([[:space:],]|$) ]] ||
     [[ "$variable_value" =~ (v934-coverage|jacoco\.|v934\.coverage\.|(^|[[:space:]])-D(argLine|test|it\.test|failIfNoTests|surefire\.|failsafe\.)) ]]; then
    v934_coverage_fail "MAVEN_OPTS contains a forbidden profile, selector, coverage, or parallel override"
    return 1
  fi
  if [[ -f "$V934_COVERAGE_REPO_ROOT/.mvn/maven.config" ]] &&
     grep -Eq '(^|[[:space:],])(-T|--threads)([[:space:]=,0-9C]|$)|(^|[[:space:],])!?coverage([[:space:],]|$)|v934-coverage|jacoco\.|v934\.coverage\.|(^|[[:space:]])-D(argLine|test|it\.test|failIfNoTests|surefire\.|failsafe\.)' \
       "$V934_COVERAGE_REPO_ROOT/.mvn/maven.config"; then
    v934_coverage_fail ".mvn/maven.config contains a forbidden profile, selector, coverage, or parallel override"
    return 1
  fi

  expected_exec_root="$V934_COVERAGE_REPO_ROOT/target/v934-step4-coverage/runs/$session_prefix/exec"
  [[ "$exec_root" == "$expected_exec_root" ]] ||
    { v934_coverage_fail "exec root must equal the canonical session run path: $expected_exec_root"; return 1; }

  mkdir -p "$exec_root"
  [[ -d "$exec_root" && ! -L "$exec_root" ]] ||
    { v934_coverage_fail "exec root must be a real directory"; return 1; }
  exec_root="$(cd "$exec_root" && pwd -P)"
  [[ "$exec_root" == "$expected_exec_root" ]] ||
    { v934_coverage_fail "exec root resolves outside the canonical session run path"; return 1; }

  if [[ "$runner_kind" == "ut" ]]; then
    [[ "$lane_token" == "unit" ]] ||
      { v934_coverage_fail "the UT lane token must be unit"; return 1; }
    file_name="jacoco-ut.exec"
  else
    file_name="jacoco-it-${lane_token}.exec"
  fi
  V934_COVERAGE_EXEC_FILE="$exec_root/$file_name"
  V934_COVERAGE_SESSION_ID_BASE="${session_prefix}-${lane_token}"
  [[ ! -e "$V934_COVERAGE_EXEC_FILE" && ! -L "$V934_COVERAGE_EXEC_FILE" ]] ||
    { v934_coverage_fail "refusing to overwrite exec: $V934_COVERAGE_EXEC_FILE"; return 1; }

  V934_COVERAGE_MAVEN_ARGS=(
    '-P!coverage,v934-coverage'
    "-Djacoco.${runner_kind}.destFile=$V934_COVERAGE_EXEC_FILE"
    "-Dv934.coverage.sessionId=$V934_COVERAGE_SESSION_ID_BASE"
  )
}

v934_coverage_verify_exec() {
  v934_coverage_enabled || return 0
  [[ -n "$V934_COVERAGE_EXEC_FILE" ]] ||
    { v934_coverage_fail "coverage was enabled but no exec was configured"; return 1; }
  [[ -f "$V934_COVERAGE_EXEC_FILE" && ! -L "$V934_COVERAGE_EXEC_FILE" ]] ||
    { v934_coverage_fail "exec is missing or not a regular file: $V934_COVERAGE_EXEC_FILE"; return 1; }
  [[ -s "$V934_COVERAGE_EXEC_FILE" ]] ||
    { v934_coverage_fail "exec is empty: $V934_COVERAGE_EXEC_FILE"; return 1; }
}

v934_coverage_configure_child_agent() {
  local agent_jar="${V934_COVERAGE_AGENT_JAR:-}"
  v934_coverage_enabled || return 0
  [[ -n "$V934_COVERAGE_EXEC_FILE" ]] ||
    { v934_coverage_fail "configure the owning IT exec before its child agent"; return 1; }
  [[ "$agent_jar" == /* && -f "$agent_jar" && ! -L "$agent_jar" ]] ||
    { v934_coverage_fail "V934_COVERAGE_AGENT_JAR must be an absolute regular file"; return 1; }
  [[ "$(sha256sum "$agent_jar" | cut -d' ' -f1)" == "$V934_JACOCO_AGENT_SHA256" ]] ||
    { v934_coverage_fail "V934_COVERAGE_AGENT_JAR hash differs from JaCoCo 0.8.12 runtime"; return 1; }
  [[ "$agent_jar" != *','* && "$agent_jar" != *'='* ]] ||
    { v934_coverage_fail "child agent path contains an unsupported character"; return 1; }
  [[ "$V934_COVERAGE_EXEC_FILE" != *','* && "$V934_COVERAGE_EXEC_FILE" != *'='* ]] ||
    { v934_coverage_fail "child exec path contains an unsupported character"; return 1; }
  export V934_JACOCO_CHILD_AGENT_JAR="$agent_jar"
  export V934_JACOCO_CHILD_EXEC_FILE="$V934_COVERAGE_EXEC_FILE"
  export V934_JACOCO_CHILD_SESSION_PREFIX="$V934_COVERAGE_SESSION_ID_BASE"
}

v934_coverage_clear_child_agent() {
  unset V934_JACOCO_CHILD_AGENT_JAR
  unset V934_JACOCO_CHILD_EXEC_FILE
  unset V934_JACOCO_CHILD_SESSION_PREFIX
}
