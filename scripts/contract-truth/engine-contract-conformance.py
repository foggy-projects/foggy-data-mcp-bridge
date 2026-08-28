#!/usr/bin/env python3
"""Offline contract conformance for the Stream A Engine Truth Pack.

This is deliberately a protocol/adapter harness. It uses a deterministic local
fixture and does not claim that Java engine SQL, a datasource, or a production
permission resolver has been verified.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from urllib.error import HTTPError
from urllib.parse import unquote
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "docs" / "待定" / "engine-contract-matrix-v1.json"
MODEL = "SalesModel"
DIMENSION = "customer$id"
AUTHORIZATION = "opaque-test-principal"


def fingerprint(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def success(data: object) -> dict[str, object]:
    return {
        "success": True,
        "engine": "fixture",
        "runtimeApiVersion": "foggy-runtime-api/v1",
        "data": data,
    }


def denied(phase: str) -> dict[str, object]:
    return {
        "success": False,
        "engine": "fixture",
        "runtimeApiVersion": "foggy-runtime-api/v1",
        "error": {
            "code": "MODEL_ACCESS_DENIED",
            "phase": phase,
            "target": "model",
        },
    }


class FixtureHandler(BaseHTTPRequestHandler):
    server_version = "FoggyContractFixture/1"

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def _send(self, payload: object) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _body(self) -> dict[str, object]:
        length = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(length) or b"{}")

    def do_GET(self) -> None:  # noqa: N802
        path = unquote(self.path)
        if path == "/api/v1/capabilities":
            self._send(success({"capabilities": {"query.execute": "supported", "models.list": "supported", "members.list": "supported"}}))
            return
        if path.startswith("/api/v1/models"):
            namespace = self.headers.get("X-NS") or ""
            self._send(success({"models": [MODEL], "namespace": namespace}))
            return
        self._send({"success": False, "error": {"code": "NOT_FOUND"}})

    def do_POST(self) -> None:  # noqa: N802
        path = unquote(self.path)
        body = self._body()
        namespace = self.headers.get("X-NS") or body.get("namespace") or ""
        authorized = self.headers.get("Authorization") == AUTHORIZATION
        if path == f"/api/v1/query/{MODEL}/execute":
            self._send(success({"items": [{"customer$id": "c-1", "amount": 10}], "namespace": namespace}) if authorized else denied("query.execute"))
            return
        if path == f"/api/v1/members/{MODEL}/{DIMENSION}" or path == f"/jdbc-model/dimension/v2/{MODEL}/{DIMENSION}":
            self._send(success({"items": [{"id": "c-1", "caption": "Customer 1"}], "namespace": namespace}) if authorized else denied("members.list"))
            return
        if path == "/mcp/analyst/rpc":
            method = body.get("method")
            params = body.get("params") if isinstance(body.get("params"), dict) else {}
            name = params.get("name")
            if method == "tools/call" and name == "dataset.query_model":
                result = success({"items": [{"customer$id": "c-1", "amount": 10}], "namespace": namespace}) if authorized else denied("query.execute")
            elif method == "tools/call" and name == "dataset.list_models":
                result = success({"models": [MODEL], "namespace": namespace})
            else:
                result = {"success": False, "error": {"code": "UNSUPPORTED_TOOL"}}
            self._send({"jsonrpc": "2.0", "id": body.get("id"), "result": {"content": [{"type": "text", "text": json.dumps(result, sort_keys=True)}]}})
            return
        self._send({"success": False, "error": {"code": "NOT_FOUND"}})


def request(base_url: str, method: str, path: str, namespace: str | None = None, authorization: str | None = None, body: object | None = None) -> dict[str, object]:
    headers = {"Content-Type": "application/json"}
    if namespace:
        headers["X-NS"] = namespace
    if authorization:
        headers["Authorization"] = authorization
    raw = None if body is None else json.dumps(body).encode("utf-8")
    response = urlopen(Request(base_url + path, method=method, headers=headers, data=raw), timeout=5)
    return json.loads(response.read())


def mcp_result(envelope: dict[str, object]) -> dict[str, object]:
    result = envelope["result"]
    content = result["content"]
    return json.loads(content[0]["text"])


def run_cli(cli_root: Path, base_url: str, namespace: str, payload_path: Path) -> dict[str, object]:
    env = dict(__import__("os").environ)
    env["PYTHONPATH"] = str(cli_root / "src")
    completed = subprocess.run(
        [sys.executable, "-m", "foggy_runtime_cli.main", "--base-url", base_url, "--namespace", namespace,
         "--authorization", AUTHORIZATION, "query", "execute", MODEL, "--payload", str(payload_path)],
        check=False,
        capture_output=True,
        text=True,
        env=env,
    )
    if completed.returncode != 0:
        raise AssertionError(f"CLI failed: {completed.returncode}: {completed.stderr}")
    return json.loads(completed.stdout)


def run_cli_members(cli_root: Path, base_url: str, namespace: str) -> dict[str, object]:
    env = dict(__import__("os").environ)
    env["PYTHONPATH"] = str(cli_root / "src")
    completed = subprocess.run(
        [sys.executable, "-m", "foggy_runtime_cli.main", "--base-url", base_url,
         "--namespace", namespace, "--authorization", AUTHORIZATION,
         "members", "list", MODEL, DIMENSION],
        check=False,
        capture_output=True,
        text=True,
        env=env,
    )
    if completed.returncode != 0:
        raise AssertionError(
            f"CLI members failed: {completed.returncode}: stdout={completed.stdout!r} stderr={completed.stderr!r}")
    return json.loads(completed.stdout)


def validate_matrix() -> None:
    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    assert matrix["contractVersion"] == "v1"
    assert matrix["sourceFacts"]["cliVersion"] == "0.1.22-source-baseline"
    assert matrix["sourceFacts"]["cliReleaseAuthority"] == "Stream B Release Truth Pack"
    assert matrix["namespace"]["precedence"] == ["X-NS", "body.namespace", "default-namespace", "empty"]
    ids = {entry["id"] for entry in matrix["surfaces"]}
    assert {
        "query.execute", "models.list", "members.list", "members.legacy",
        "members.deprecated", "engine.query.internal",
    } <= ids
    cli_surface = next(entry for entry in matrix["surfaces"] if entry["id"] == "cli.query-and-members")
    assert "/jdbc-model/dimension/v2/**" in cli_surface["route"]
    assert cli_surface["capability"] is None


def run(cli_root: Path | None) -> None:
    validate_matrix()
    server = ThreadingHTTPServer(("127.0.0.1", 0), FixtureHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base_url = f"http://127.0.0.1:{server.server_port}"
    try:
        runtime_query = request(base_url, "POST", f"/api/v1/query/{MODEL}/execute", "tenant-a", AUTHORIZATION, {"payload": {"columns": ["customer$id"]}})
        runtime_query_fp = fingerprint(runtime_query)
        if cli_root is not None:
            with tempfile.TemporaryDirectory() as temp_dir:
                payload = Path(temp_dir) / "query.json"
                payload.write_text(json.dumps({"columns": ["customer$id"]}), encoding="utf-8")
                cli_query_fp = fingerprint(run_cli(cli_root, base_url, "tenant-a", payload))
                assert runtime_query_fp == cli_query_fp, (runtime_query_fp, cli_query_fp)

        metadata = request(base_url, "GET", "/api/v1/models", "tenant-a", AUTHORIZATION)
        mcp_metadata = mcp_result(request(base_url, "POST", "/mcp/analyst/rpc", "tenant-a", AUTHORIZATION, {
            "jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "dataset.list_models", "arguments": {}}}))
        assert fingerprint(metadata) == fingerprint(mcp_metadata)

        stable_members = request(base_url, "POST", f"/api/v1/members/{MODEL}/{DIMENSION}", "tenant-a", AUTHORIZATION)
        legacy_members = request(base_url, "POST", f"/jdbc-model/dimension/v2/{MODEL}/{DIMENSION}", "tenant-a", AUTHORIZATION)
        assert fingerprint(stable_members) == fingerprint(legacy_members)
        if cli_root is not None:
            cli_members = run_cli_members(cli_root, base_url, "tenant-a")
            assert fingerprint(legacy_members) == fingerprint(cli_members)

        stable_denied = request(base_url, "POST", f"/api/v1/members/{MODEL}/{DIMENSION}", "tenant-a")
        legacy_denied = request(base_url, "POST", f"/jdbc-model/dimension/v2/{MODEL}/{DIMENSION}", "tenant-a")
        assert fingerprint(stable_denied) == fingerprint(legacy_denied)
        assert stable_denied["error"]["code"] == "MODEL_ACCESS_DENIED"

        runtime_denied = request(base_url, "POST", f"/api/v1/query/{MODEL}/execute", "tenant-a")
        mcp_denied = mcp_result(request(base_url, "POST", "/mcp/analyst/rpc", "tenant-a", None, {
            "jsonrpc": "2.0", "id": 2, "method": "tools/call",
            "params": {"name": "dataset.query_model", "arguments": {}}}))
        assert fingerprint(runtime_denied) == fingerprint(mcp_denied)
        assert runtime_denied["error"]["code"] == "MODEL_ACCESS_DENIED"

        header_wins = request(
            base_url, "POST", f"/api/v1/query/{MODEL}/execute", "tenant-header", AUTHORIZATION,
            {"namespace": "tenant-body"})
        body_fallback = request(
            base_url, "POST", f"/api/v1/query/{MODEL}/execute", None, AUTHORIZATION,
            {"namespace": "tenant-body"})
        assert header_wins["data"]["namespace"] == "tenant-header"
        assert body_fallback["data"]["namespace"] == "tenant-body"

        tenant_a = request(base_url, "POST", f"/api/v1/query/{MODEL}/execute", "tenant-a", AUTHORIZATION, {})
        tenant_b = request(base_url, "POST", f"/api/v1/query/{MODEL}/execute", "tenant-b", AUTHORIZATION, {})
        assert tenant_a["data"]["namespace"] == "tenant-a"
        assert tenant_b["data"]["namespace"] == "tenant-b"
        assert fingerprint(tenant_a) != fingerprint(tenant_b)
    finally:
        server.shutdown()
        thread.join(timeout=5)
    print("Engine contract conformance passed: matrix, Runtime/CLI query, MCP metadata, members adapter, auth failure, namespace isolation")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli-root", type=Path, help="Optional foggy-runtime-cli source root to exercise the CLI adapter")
    args = parser.parse_args()
    try:
        run(args.cli_root)
    except (AssertionError, HTTPError, OSError, json.JSONDecodeError) as exc:
        print(f"contract conformance failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
