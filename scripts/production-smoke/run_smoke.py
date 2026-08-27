#!/usr/bin/env python3
"""Real-process OAuth/JWKS and PostgreSQL production smoke for Foggy launcher."""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from typing import Any
from urllib import error, parse, request

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
COMPOSE_FILE = SCRIPT_DIR / "docker-compose.yml"
KEYCLOAK = "http://localhost:18090"
LAUNCHER = "http://localhost:18091"
CLIENT_SECRET = "foggy-smoke-client-secret"


def run(command: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(command))
    return subprocess.run(
        command,
        cwd=REPO_ROOT,
        check=True,
        text=True,
        capture_output=capture,
    )


def compose(*args: str, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return run(["docker", "compose", "-f", str(COMPOSE_FILE), *args], capture=capture)


def http_json(
    url: str,
    *,
    method: str = "GET",
    token: str | None = None,
    body: dict[str, Any] | None = None,
    extra_headers: dict[str, str] | None = None,
) -> tuple[int, Any, dict[str, str]]:
    headers = {"Accept": "application/json"}
    payload = None
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if extra_headers:
        headers.update(extra_headers)
    call = request.Request(url, data=payload, headers=headers, method=method)
    try:
        with request.urlopen(call, timeout=15) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else None, dict(response.headers)
    except error.HTTPError as failure:
        raw = failure.read().decode("utf-8")
        try:
            parsed: Any = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return failure.code, parsed, dict(failure.headers)


def wait_for(url: str, timeout_seconds: int = 180) -> Any:
    deadline = time.monotonic() + timeout_seconds
    last: object = None
    while time.monotonic() < deadline:
        try:
            status, payload, _ = http_json(url)
            last = (status, payload)
            if status == 200:
                return payload
        except (OSError, ValueError) as failure:
            last = failure
        time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {url}: {last}")


def access_token(client_id: str, username: str, password: str) -> str:
    form = parse.urlencode(
        {
            "grant_type": "password",
            "client_id": client_id,
            "client_secret": CLIENT_SECRET,
            "username": username,
            "password": password,
        }
    ).encode("utf-8")
    call = request.Request(
        f"{KEYCLOAK}/realms/foggy-smoke/protocol/openid-connect/token",
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with request.urlopen(call, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))["access_token"]
    except error.HTTPError as failure:
        detail = failure.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Keycloak token request failed for {client_id}/{username}: HTTP {failure.code} {detail}"
        ) from failure


def jwt_header(token: str) -> dict[str, Any]:
    encoded = token.split(".", 1)[0]
    encoded += "=" * (-len(encoded) % 4)
    return json.loads(base64.urlsafe_b64decode(encoded).decode("utf-8"))


def expect(status: int, expected: int, label: str) -> None:
    if status != expected:
        raise AssertionError(f"{label}: expected HTTP {expected}, got {status}")
    print(f"PASS {label}: HTTP {status}")


def rpc(token: str | None, path: str, headers: dict[str, str] | None = None) -> tuple[int, Any]:
    status, payload, _ = http_json(
        f"{LAUNCHER}{path}",
        method="POST",
        token=token,
        body={"jsonrpc": "2.0", "id": "production-smoke", "method": "tools/list", "params": {}},
        extra_headers=headers,
    )
    return status, payload


def java_executable() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        binary = "java.exe" if os.name == "nt" else "java"
        candidate = Path(java_home) / "bin" / binary
        if candidate.is_file():
            return str(candidate)
    executable = shutil.which("java")
    if not executable:
        raise RuntimeError("Java 17 or newer is required")
    return executable


def launcher_jar() -> Path:
    candidates = sorted((REPO_ROOT / "foggy-mcp-launcher" / "target").glob("foggy-mcp-launcher-*.jar"))
    candidates = [path for path in candidates if not path.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if not candidates:
        raise FileNotFoundError("Launcher JAR not found; run without --skip-build")
    return candidates[-1]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--keep", action="store_true", help="keep Docker services after the smoke")
    args = parser.parse_args()

    if not shutil.which("docker"):
        raise RuntimeError("docker is required")
    if not args.skip_build:
        maven = shutil.which("mvn") or shutil.which("mvn.cmd")
        if not maven:
            raise RuntimeError("mvn is required unless --skip-build is used")
        run([maven, "-B", "-ntp", "-q", "-pl", "foggy-mcp-launcher", "-am", "-DskipTests", "package"])

    log_dir = REPO_ROOT / "target" / "production-smoke"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / "launcher.log"
    process: subprocess.Popen[bytes] | None = None
    issued_tokens: list[str] = []
    summary: dict[str, Any] = {}

    try:
        compose("up", "-d", "--wait")
        database_count = compose(
            "exec", "-T", "postgres", "psql", "-U", "foggy", "-d", "foggy_smoke",
            "-tAc", "SELECT count(*) FROM smoke_orders", capture=True,
        ).stdout.strip()
        if database_count != "2":
            raise AssertionError(f"PostgreSQL fixture count expected 2, got {database_count!r}")
        summary["postgresRows"] = 2

        environment = os.environ.copy()
        environment.update(
            {
                "SPRING_PROFILES_ACTIVE": "lite",
                "SERVER_PORT": "18091",
                "SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost:15433/foggy_smoke",
                "SPRING_DATASOURCE_USERNAME": "foggy",
                "SPRING_DATASOURCE_PASSWORD": "foggy-smoke-password",
                "SPRING_DATASOURCE_DRIVER_CLASS_NAME": "org.postgresql.Driver",
                "SPRING_SQL_INIT_MODE": "never",
                "FOGGY_COMPOSE_DIALECT": "postgresql",
                "DATA_VIEWER_ENABLED": "false",
                "MCP_AUDIT_ENABLED": "false",
                "FOGGY_AUTH_MODE": "oauth-resource-server",
                "FOGGY_AUTH_RESOURCE_URI": f"{LAUNCHER}/mcp",
                "FOGGY_AUTHORIZATION_SERVERS": f"{KEYCLOAK}/realms/foggy-smoke",
                "FOGGY_AUTH_REQUIRED_SCOPES": "profile",
                "FOGGY_AUTH_SCOPES_SUPPORTED": "profile",
                "FOGGY_AUTH_JWT_ISSUER_URI": f"{KEYCLOAK}/realms/foggy-smoke",
                "FOGGY_AUTH_JWT_AUDIENCES": "foggy-data",
                "FOGGY_AUTH_JWT_ALLOW_INSECURE_HTTP": "true",
                "FOGGY_AUTH_JWT_ROLES_CLAIM": "realm_access.roles",
                "FOGGY_AUTH_JWT_REQUIRE_TENANT": "true",
                "LOGGING_LEVEL_COM_FOGGYFRAMEWORK_DATASET_MCP_AUTH": "DEBUG",
            }
        )
        with log_path.open("wb") as launcher_log:
            process = subprocess.Popen(
                [java_executable(), "-jar", str(launcher_jar())],
                cwd=REPO_ROOT,
                env=environment,
                stdout=launcher_log,
                stderr=subprocess.STDOUT,
            )
            health = wait_for(f"{LAUNCHER}/actuator/health")
            if not isinstance(health, dict) or health.get("status") != "UP":
                raise AssertionError(f"Launcher health is not UP: {health}")

            status, metadata, _ = http_json(
                f"{LAUNCHER}/.well-known/oauth-protected-resource/mcp"
            )
            expect(status, 200, "OAuth protected-resource metadata")
            if KEYCLOAK not in json.dumps(metadata):
                raise AssertionError("OAuth metadata does not advertise Keycloak")

            status, _ = rpc(None, "/mcp/analyst")
            expect(status, 401, "missing bearer token")

            analyst = access_token("foggy-data", "analyst", "analyst-password")
            admin = access_token("foggy-data", "admin", "admin-password")
            wrong_audience = access_token("foggy-wrong-audience", "analyst", "analyst-password")
            no_scope = access_token("foggy-no-scope", "analyst", "analyst-password")
            issued_tokens.extend([analyst, admin, wrong_audience, no_scope])

            status, analyst_payload = rpc(
                analyst,
                "/mcp/analyst",
                {"X-User-Id": "attacker", "X-Tenant-Id": "attacker", "X-Roles": "ADMIN"},
            )
            expect(status, 200, "verified analyst token with spoofed identity headers")
            if not isinstance(analyst_payload, dict) or "result" not in analyst_payload:
                raise AssertionError(f"tools/list returned an invalid payload: {analyst_payload}")

            status, _ = rpc(analyst, "/mcp/admin")
            expect(status, 403, "analyst rejected from admin MCP")
            status, _ = rpc(admin, "/mcp/admin")
            expect(status, 200, "admin accepted by admin MCP")
            status, _ = rpc(wrong_audience, "/mcp/analyst")
            expect(status, 401, "wrong audience rejected")
            status, _ = rpc(no_scope, "/mcp/analyst")
            expect(status, 403, "missing required scope rejected")

            old_kid = jwt_header(analyst).get("kid")
            compose("up", "-d", "--force-recreate", "--no-deps", "--wait", "keycloak")
            rotated = access_token("foggy-data", "analyst", "analyst-password")
            issued_tokens.append(rotated)
            new_kid = jwt_header(rotated).get("kid")
            if not old_kid or not new_kid or old_kid == new_kid:
                raise AssertionError(f"Keycloak signing key did not rotate: old={old_kid}, new={new_kid}")
            status, _ = rpc(rotated, "/mcp/analyst")
            expect(status, 200, "unknown kid triggers JWKS refresh")
            summary["jwksRotation"] = {"oldKid": old_kid, "newKid": new_kid}

            launcher_log.flush()
            log_text = log_path.read_text(encoding="utf-8", errors="replace")
            if any(token in log_text for token in issued_tokens):
                raise AssertionError("A raw access token was found in launcher logs")
            summary["rawTokenInLogs"] = False
            summary["launcherHealth"] = "UP"
            summary["checks"] = 8

        print(json.dumps(summary, indent=2, sort_keys=True))
        return 0
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        if not args.keep:
            compose("down", "--volumes", "--remove-orphans")
        print(f"launcher log: {log_path}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as failure:
        print(f"SMOKE FAILED: {failure}", file=sys.stderr)
        raise