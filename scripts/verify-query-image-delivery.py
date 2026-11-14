#!/usr/bin/env python3
"""Linux packaged Java Runtime acceptance; requires Java 17, fontconfig, Python stdlib.

Build launcher with Maven -Pruntime-api before running. All mutable fixture state
and sanitized evidence go under --root. --serve retains the localhost process
for installed CLI/MCP clients until --root/stop-runtime exists. The test creates
synthetic SQLite data and model permission resolvers; it is not production IAM.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import socket
import sqlite3
import struct
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from html import escape

MODEL = "DeliverySalesQueryModel"
TABLE_MODEL = "DeliverySalesModel"
NAMESPACES = ("image_delivery_a", "image_delivery_b")
FIELDS = ["month", "salesAmount", "targetAmount", "orders"]
PAYLOAD = {"columns": FIELDS, "orderBy": [{"field": "month", "dir": "asc"}], "limit": 100}
EMPTY_PAYLOAD = {**PAYLOAD, "slice": [{"field": "month", "op": "=", "value": "2099-01"}]}
AMOUNTS = [120.5, 151.2, 136.8, 184.6, 201.3, 228.9]
FONT_HASH = "faa6c9df652116dde789d351359f3d7e5d2285a2b2a1f04a2d7244df706d5ea9"
AUTH_PREFIX = "query-image-fixture-reader:"
AUTH_EAST_PREFIX = "query-image-fixture-east:"


def redact(value):
    if isinstance(value, dict):
        return {k: ("<redacted>" if k.lower() in {"authorization", "jdbcurl", "password", "authcode"}
                    else redact(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v) for v in value]
    if isinstance(value, str):
        value = re.sub(r"query-image-fixture-(?:reader|east):[A-Za-z0-9_-]+", "<synthetic-identity>", value)
        value = re.sub(r"jdbc:[^\s\"']+", "jdbc:<fixture>", value)
        return value
    return value


class Acceptance:
    def __init__(self, root, jar, port):
        self.root = root.resolve()
        self.jar = jar.resolve()
        self.port = port
        self.base = f"http://127.0.0.1:{port}"
        self.root.mkdir(parents=True, exist_ok=True)
        self.identity = AUTH_PREFIX + uuid.uuid4().hex
        self.east_identity = AUTH_EAST_PREFIX + uuid.uuid4().hex
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self.checks = []
        self.java = None

    def save(self, name, value):
        (self.root / name).write_text(json.dumps(redact(value), ensure_ascii=False, indent=2), encoding="utf-8")

    def record(self, name, ok=True, **extra):
        self.checks.append({"check": name, "passed": bool(ok), **extra})
        self.save("checks.json", self.checks)
        print(json.dumps({"check": name, "passed": bool(ok), **extra}, ensure_ascii=False), flush=True)
        if not ok:
            raise RuntimeError(name)

    def api(self, path, body=None, namespace=None, identity=None, method=None):
        headers = {"Accept": "application/json"}
        if namespace:
            headers["X-NS"] = namespace
        if identity:
            headers["Authorization"] = identity
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with self.http.open(request, timeout=45) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            return json.load(error)

    def rpc(self, tool, arguments, namespace=NAMESPACES[0], identity=None):
        return self.api("/mcp/analyst/rpc", {"jsonrpc": "2.0", "id": "delivery-check",
                        "method": "tools/call", "params": {"name": tool, "arguments": arguments}},
                        namespace, identity)

    def environment(self):
        self.env = os.environ.copy()
        for key in list(self.env):
            if key.startswith("OPENAI_") or key.startswith("FOGGY_AUTH_") or key in {
                    "FOGGY_RUNTIME_API_AUTH_CODE", "FOGGY_RUNTIME_AUTHORIZATION"}:
                self.env.pop(key, None)
        font_cache = self.root / "font-cache"
        font_cache.mkdir(exist_ok=True)
        font_config = self.root / "fontconfig.xml"
        font_config.write_text("<?xml version=\"1.0\"?><!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">"
                              "<fontconfig><dir>/usr/share/fonts/truetype/dejavu</dir><cachedir>"
                              + escape(str(font_cache)) + "</cachedir></fontconfig>", encoding="utf-8")
        self.env["FONTCONFIG_FILE"] = str(font_config)
        self.env["NO_PROXY"] = "127.0.0.1,localhost"
        self.env["LANG"] = "C.UTF-8"
        font_list = subprocess.run(["fc-list", ":", "file", "family"], env=self.env,
                                   capture_output=True, text=True, check=True).stdout.splitlines()
        chinese = subprocess.run(["fc-list", ":lang=zh", "file", "family"], env=self.env,
                                 capture_output=True, text=True, check=True).stdout.splitlines()
        self.save("environment.json", {"java": subprocess.run(["java", "-version"], capture_output=True,
                  text=True).stderr.splitlines(), "headless": True, "fontConfig": "task-local DejaVu-only",
                  "systemChineseFonts": len(subprocess.run(["fc-list", ":lang=zh"], capture_output=True,
                   text=True).stdout.splitlines()), "isolatedChineseFonts": len(chinese), "isolatedFonts": font_list})
        self.record("isolated-fontconfig-has-no-chinese-fonts", not chinese, fonts=len(font_list))
        launcher_digest = hashlib.sha256()
        with self.jar.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                launcher_digest.update(block)
        with zipfile.ZipFile(self.jar) as launcher:
            if not any("/foggy-runtime-api-" in name for name in launcher.namelist()):
                raise RuntimeError("launcher is missing Runtime API; build with Maven -Pruntime-api")
            nested = next(name for name in launcher.namelist() if "/foggy-dataset-mcp-" in name and name.endswith(".jar"))
            with zipfile.ZipFile(io.BytesIO(launcher.read(nested))) as module:
                font = module.read("fonts/NotoSansSC-Regular.otf")
                license_text = module.read("fonts/OFL.txt").decode("utf-8")
                self.record("packaged-bundled-font-and-license", hashlib.sha256(font).hexdigest() == FONT_HASH
                            and "SIL OPEN FONT LICENSE Version 1.1" in license_text,
                            fontBytes=len(font), fontSha256=FONT_HASH, launcherBytes=self.jar.stat().st_size,
                            launcherSha256=launcher_digest.hexdigest())

    def fixture(self):
        self.db = self.root / "delivery.sqlite"
        with sqlite3.connect(self.db) as connection:
            for index, namespace in enumerate(NAMESPACES):
                table = "delivery_sales_a" if index == 0 else "delivery_sales_b"
                connection.execute(f"CREATE TABLE IF NOT EXISTS {table} (month TEXT PRIMARY KEY, sales_amount DECIMAL,"
                                   " target_amount DECIMAL, orders INTEGER, region TEXT, secret_margin DECIMAL)")
                connection.execute(f"DELETE FROM {table}")
                connection.executemany(f"INSERT INTO {table} VALUES (?,?,?,?,?,?)", [
                    (f"2026-{month+1:02d}", amount + 500 * index, 140 + 15 * month,
                     320 + 47 * month, "华东" if month % 2 == 0 else "华南", 18.3)
                    for month, amount in enumerate(AMOUNTS)])
                directory = self.root / "models" / namespace
                (directory / "model").mkdir(parents=True, exist_ok=True)
                (directory / "query").mkdir(exist_ok=True)
                tm = f"""export const model = {{
 name: '{TABLE_MODEL}', caption: '打包交付销售数据', type: 'jdbc', tableName: '{table}',
 properties: [
  {{column:'month',name:'month',caption:'月份',type:'STRING'}},
  {{column:'sales_amount',name:'salesAmount',caption:'销售额（万元）',type:'NUMBER'}},
  {{column:'target_amount',name:'targetAmount',caption:'目标额（万元）',type:'NUMBER'}},
  {{column:'orders',name:'orders',caption:'订单数（笔）',type:'INTEGER'}},
  {{column:'region',name:'region',caption:'区域',type:'STRING'}},
  {{column:'secret_margin',name:'secretMargin',caption:'受限利润',type:'NUMBER'}}
 ], measures: []
}};"""
                qm = f"""const source = loadTableModel('{TABLE_MODEL}');
export const queryModel = {{
 name:'{MODEL}',caption:'打包交付销售查询',model:source,
 modelPermissions:{{mode:'resolver',resolver:(context)=>{{
  const identity = context.authorization;
  if(identity != null && identity.startsWith('{AUTH_EAST_PREFIX}')){{
   return {{allow:true,rowPredicates:[context.predicate.eq(source.region,'华东')]}};
  }}
  return {{allow:identity != null && identity.startsWith('{AUTH_PREFIX}')}};
 }}}},
 fieldPermissions:{{defaultVisible:true,hiddenFields:[{{fields:['secretMargin']}}]}},
 columnGroups:[{{caption:'销售字段',items:[{{ref:source.month}},{{ref:source.salesAmount}},
 {{ref:source.targetAmount}},{{ref:source.orders}},{{ref:source.region}},{{ref:source.secretMargin}}]}}],accesses:[]
}};"""
                (directory / "model" / (TABLE_MODEL + ".tm")).write_text(tm, encoding="utf-8")
                (directory / "query" / (MODEL + ".qm")).write_text(qm, encoding="utf-8")
        self.save("query.json", PAYLOAD)
        self.save("query-empty.json", EMPTY_PAYLOAD)

    def launch(self):
        with socket.socket() as port_probe:
            port_probe.bind(("127.0.0.1", self.port))
        # Fresh registry state keeps prior bundle autoload out of detached candidate validation on reruns.
        state = self.root / "runtime-state" / ("run-" + time.strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8])
        state.mkdir(parents=True, exist_ok=True)
        command = ["java", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Xmx512m", "-jar", str(self.jar),
                   "--spring.profiles.active=lite", "--server.address=127.0.0.1", f"--server.port={self.port}",
                   "--foggy.runtime-api.enabled=true", "--foggy.runtime-api.security-mode=none-dev-test-only",
                   "--spring.sql.init.mode=never", f"--spring.datasource.url=jdbc:sqlite:{self.db}",
                   f"--foggy.runtime-api.bundle-registry.path={state / 'bundles.json'}",
                   f"--foggy.runtime-api.datasource-registry.path={state / 'datasources.json'}",
                   f"--foggy.data-viewer.cache.sqlite-path={state / 'viewer-query-cache.sqlite'}",
                   "--foggy.demo.enabled=false", "--foggy.dataset.show-sql=false",
                   "--foggy.dataset.show-sql-parameters=false",
                   "--logging.level.root=INFO", "--logging.level.org.springframework.ai=WARN",
                   "--logging.level.com.foggyframework.core.spring.proxy=WARN",
                   "--foggy.mcp.audit.enabled=false"]
        self.java = subprocess.Popen(command, cwd=self.root, env=self.env, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        (self.root / "runtime.pid").write_text(str(self.java.pid), encoding="ascii")

        def log_reader():
            with (self.root / "runtime-sanitized.log").open("w", encoding="utf-8") as output:
                for line in self.java.stdout:
                    output.write(redact(line))
                    output.flush()
        threading.Thread(target=log_reader, daemon=True).start()
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if self.java.poll() is not None:
                raise RuntimeError("packaged launcher exited during startup; see sanitized log")
            try:
                capabilities = self.api("/api/v1/capabilities")
                self.save("readiness-last-response.json", capabilities)
                if capabilities.get("success") is True:
                    self.save("capabilities.json", capabilities)
                    self.record("runtime-ready-capabilities", True)
                    return
            except Exception as error:
                self.save("readiness-last-error.json", {"type": type(error).__name__, "message": str(error)})
            time.sleep(1)
        raise RuntimeError("runtime did not become ready within 90 seconds")

    def bootstrap(self):
        datasource = self.api("/api/v1/datasources", {"name": "delivery-sqlite", "type": "sqlite",
                             "jdbcUrl": f"jdbc:sqlite:{self.db}", "enabled": True, "replace": True})
        self.save("datasource-added.json", datasource)
        self.record("datasource-added", datasource.get("success") is True)
        for namespace in NAMESPACES:
            directory = self.root / "models" / namespace
            bound = self.api(f"/api/v1/namespaces/{namespace}/datasource", {"dataSource": "delivery-sqlite"}, method="PUT")
            self.save("namespace-binding-" + namespace + ".json", bound)
            self.record("namespace-datasource-bound-" + namespace, bound.get("success") is True)
            inspected = self.api("/api/v1/tables/inspect", {"dataSource": "delivery-sqlite",
                                 "table": "delivery_sales_a" if namespace == NAMESPACES[0] else "delivery_sales_b"}, namespace)
            self.save("table-inspection-" + namespace + ".json", inspected)
            self.record("actual-sqlite-table-inspected-" + namespace, inspected.get("success") is True)
            validated = self.api("/api/v1/models/validate", {"path": str(directory), "namespace": namespace,
                                 "includeStackTrace": False}, namespace)
            self.save("models-validate-" + namespace + ".json", validated)
            self.record("models-validated-" + namespace, validated.get("success") is True
                        and validated.get("data", {}).get("valid") is True)
            added = self.api("/api/v1/bundles", {"name": namespace + "-delivery", "namespace": namespace,
                             "path": str(directory), "watch": False, "enabled": True, "replace": True})
            self.save("bundle-" + namespace + ".json", added)
            self.record("bundle-registered-" + namespace, added.get("success") is True)
            refreshed = self.api("/api/v1/models/refresh", {"namespace": namespace, "models": [MODEL]}, namespace)
            self.save("refresh-" + namespace + ".json", refreshed)
            self.record("model-refreshed-" + namespace, refreshed.get("success") is True
                        and refreshed.get("data", {}).get("failedCount") == 0)
            described = self.api(f"/api/v1/models/{MODEL}/describe", {}, namespace, self.identity)
            self.save("describe-" + namespace + ".json", described)
            self.record("model-described-" + namespace, described.get("success") is True)

    def verify(self):
        discovery = self.api("/mcp/analyst/rpc", {"jsonrpc": "2.0", "id": "delivery-discovery",
                             "method": "server/discover", "params": {}}, NAMESPACES[0], self.identity)
        discovered = discovery.get("result", {})
        self.record("modern-discovery-complete-private", discovered.get("resultType") == "complete"
                    and discovered.get("cacheScope") == "private" and discovered.get("ttlMs", -1) >= 0)
        self.save("mcp-discovery.json", discovery)
        listing = self.api("/mcp/analyst/rpc", {"jsonrpc": "2.0", "id": "delivery-list",
                           "method": "tools/list", "params": {}}, NAMESPACES[0], self.identity)
        self.record("tool-discovered", "dataset.export_image" in [tool.get("name") for tool in listing.get("result", {}).get("tools", [])])
        self.record("modern-tool-list-complete-private-no-cache", listing.get("result", {}).get("resultType") == "complete"
                    and listing.get("result", {}).get("ttlMs") == 0 and listing.get("result", {}).get("cacheScope") == "private")
        self.save("mcp-tools-list.json", listing)
        hashes = {}
        for namespace in NAMESPACES:
            queried = self.api(f"/api/v1/query/{MODEL}/execute", PAYLOAD, namespace, self.identity)
            actual = queried.get("data", {}).get("items", [])
            table = "delivery_sales_a" if namespace == NAMESPACES[0] else "delivery_sales_b"
            with sqlite3.connect(self.db) as connection:
                expected = [dict(zip(FIELDS, row)) for row in connection.execute(
                    f"SELECT month,sales_amount,target_amount,orders FROM {table} ORDER BY month")]
            self.record("actual-engine-rows-match-independent-sql-" + namespace, queried.get("success") is True and actual == expected, rows=len(actual))
            self.save("actual-rows-" + namespace + ".json", actual)
            for kind in ["table", "bar", "line"]:
                args = {"model": MODEL, "payload": PAYLOAD, "kind": kind, "title": "2026 年上半年销售交付验证",
                        "xField": "month", "yField": "salesAmount", "xLabel": "月份", "yLabel": "销售额",
                        "unit": "万元", "width": 1200, "height": 720}
                started = time.monotonic()
                response = self.rpc("dataset.export_image", args, namespace, self.identity)
                elapsed_ms = round((time.monotonic() - started) * 1000, 1)
                result = response.get("result", {})
                images = [block for block in result.get("content", []) if block.get("type") == "image"]
                self.record("native-mcp-image-" + kind + "-" + namespace, result.get("resultType") == "complete"
                            and not result.get("isError") and len(images) == 1)
                png = base64.b64decode(images[0]["data"], validate=True)
                self.record("png-header-" + kind + "-" + namespace, png.startswith(b"\x89PNG\r\n\x1a\n"))
                (self.root / f"mcp-{kind}-{namespace}.png").write_bytes(png)
                hashes[namespace, kind] = hashlib.sha256(png).hexdigest()
                self.save(f"mcp-{kind}-{namespace}.json", result.get("structuredContent", {}))
                metadata = result.get("structuredContent", {})
                self.record("actual-six-rows-rendered-" + kind + "-" + namespace,
                            metadata.get("success") is True and metadata.get("rowsRendered") == len(expected)
                            and metadata.get("rowsReturned") == len(expected) and not metadata.get("truncated"))
                text = json.dumps(result.get("structuredContent", {}), ensure_ascii=False)
                self.record("safe-image-metadata-" + kind + "-" + namespace,
                            self.identity not in text and "jdbc:" not in text and '"data"' not in text,
                            pngBytes=len(png), dimensions=struct.unpack(">II", png[16:24]),
                            queryAndExportElapsedMs=elapsed_ms)
        self.record("same-model-namespace-images-isolated", all(hashes[NAMESPACES[0], kind] != hashes[NAMESPACES[1], kind]
                                                               for kind in ["table", "bar", "line"]))
        for name, namespace, identity, payload in [
            ("model-permission-denied", NAMESPACES[0], None, PAYLOAD),
            ("wrong-identity-denied", NAMESPACES[0], "invalid-fixture-identity", PAYLOAD),
            ("wrong-namespace-denied", "image_delivery_missing", self.identity, PAYLOAD),
            ("field-permission-denied", NAMESPACES[0], self.identity, {**PAYLOAD, "columns": ["month", "secretMargin"]}),
            ("empty-query-no-image", NAMESPACES[0], self.identity, EMPTY_PAYLOAD)]:
            response = self.rpc("dataset.export_image", {"model": MODEL, "payload": payload, "kind": "table"}, namespace, identity)
            result = response.get("result", {})
            self.record(name, bool(response.get("error")) or (result.get("resultType") == "complete" and result.get("isError") is True
                        and not any(item.get("type") == "image" for item in result.get("content", []))))
            self.save(name + ".json", response)
            if name == "empty-query-no-image":
                self.record("empty-query-typed-safe-error", result.get("structuredContent", {}).get("error", {}).get("code") == "NO_QUERY_DATA")
        east = self.api(f"/api/v1/query/{MODEL}/execute", PAYLOAD, NAMESPACES[0], self.east_identity)
        self.record("real-row-permission-restricts-engine-results", east.get("success") is True
                    and [row["month"] for row in east.get("data", {}).get("items", [])] == ["2026-01", "2026-03", "2026-05"])
        restricted = self.rpc("dataset.export_image", {"model": MODEL, "payload": PAYLOAD, "kind": "table"},
                              NAMESPACES[0], self.east_identity).get("result", {})
        self.record("row-permission-shared-by-image-query", restricted.get("structuredContent", {}).get("rowsRendered") == 3
                    and len([block for block in restricted.get("content", []) if block.get("type") == "image"]) == 1)
        self.save("row-permission-image.json", restricted.get("structuredContent", {}))
        self.save("endpoint.json", {"baseUrl": self.base, "mcpUrl": self.base + "/mcp/analyst/rpc", "model": MODEL,
                  "namespaces": NAMESPACES, "fields": FIELDS, "syntheticIdentity": "generate reader-prefix UUID into env only",
                  "payloadFile": "query.json", "port": self.port})
        self.record("packaged-linux-delivery-acceptance", True, checks=len(self.checks) + 1)
        print("READY_FOR_CLIENTS " + self.base, flush=True)

    def run(self, serve):
        try:
            self.environment()
            self.fixture()
            self.launch()
            self.bootstrap()
            self.verify()
            if serve:
                stop = self.root / "stop-runtime"
                while self.java.poll() is None and not stop.exists():
                    time.sleep(1)
        finally:
            if self.java is not None and self.java.poll() is None:
                self.java.terminate()
                try:
                    self.java.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    self.java.kill()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--port", type=int, default=19073)
    parser.add_argument("--serve", action="store_true")
    options = parser.parse_args()
    Acceptance(options.root, options.jar, options.port).run(options.serve)
