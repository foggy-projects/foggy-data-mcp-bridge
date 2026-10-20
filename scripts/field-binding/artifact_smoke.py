"""Exercise field binding contracts through an actual Runtime Launcher JAR."""

from pathlib import Path
import argparse, hashlib, json, os, socket, sqlite3, subprocess, time, urllib.request, urllib.error


def main():
    if not __debug__:
        raise RuntimeError("Artifact smoke requires enabled assertions")
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    jar = args.jar.resolve()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    models = out / "models"
    (models / "model").mkdir(parents=True)
    (models / "query").mkdir()
    db = out / "data.sqlite"
    with sqlite3.connect(db) as c:
        c.execute(
            "CREATE TABLE binding_smoke(id INTEGER PRIMARY KEY,category TEXT,amount INTEGER,secret INTEGER)"
        )
        c.executemany(
            "INSERT INTO binding_smoke VALUES(?,?,?,?)",
            [(1, "A", 10, 300), (2, "A", 20, 100), (3, "B", 40, 200)],
        )
        oracle = dict(
            c.execute(
                "SELECT category,SUM(amount) FROM binding_smoke GROUP BY category"
            )
        )
        average_oracle = c.execute("SELECT AVG(amount) FROM binding_smoke").fetchone()[
            0
        ]
    (models / "model/Smoke.tm").write_text(
        "export const model={name:'Smoke',tableName:'binding_smoke',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'},{name:'secret',column:'secret',type:'INTEGER'}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'},{name:'meanAmount',column:'amount',type:'NUMBER',aggregation:'avg'}]};"
    )

    def qm(name, items, extra=""):
        (models / "query" / (name + ".qm")).write_text(
            "const m=loadTableModel('Smoke');export const queryModel={name:'"
            + name
            + "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:["
            + items
            + "]}],accesses:[]"
            + extra
            + "};"
        )

    base = "{ref:m.id},{ref:m.category},{ref:m.secret},{ref:m.amount}"
    qm("Base", base)
    qm(
        "Split",
        "{ref:m.category,name:'groupKey',alias:'groupOut'},{ref:m.amount,name:'revenue',alias:'revOut'}",
    )
    qm(
        "Average",
        "{ref:m.category,name:'groupKey',alias:'groupOut'},{ref:m.meanAmount,name:'meanSales',alias:'avgOut'}",
    )
    qm(
        "Formula",
        base + ",{name:'calc',alias:'calcOut',formula:'amount * 2',type:'NUMBER'}",
    )
    qm(
        "Window",
        base
        + ",{name:'ranked',alias:'rankOut',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'secret',dir:'ASC'}]}",
    )
    qm(
        "Restricted",
        base
        + ",{name:'ranked',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'secret',dir:'ASC'}]}",
        ",fieldPermissions:{hiddenFields:[{fields:['secret']}]}",
    )
    qm(
        "Chain",
        base
        + ",{name:'firstCalc',formula:'amount * 2',type:'NUMBER'},{name:'secondCalc',formula:'firstCalc + 1',type:'NUMBER'}",
    )
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    config = {
        "server": {"address": "127.0.0.1", "port": port},
        "spring": {
            "profiles": {"active": "lite"},
            "datasource": {"url": "jdbc:sqlite:" + str(db)},
            "sql": {"init": {"mode": "never"}},
        },
        "foggy": {
            "auth": {"mode": "DISABLED"},
            "demo": {"enabled": False},
            "bundle": {
                "external": {
                    "enabled": True,
                    "bundles": [
                        {
                            "name": "field-binding-smoke",
                            "namespace": "binding-smoke",
                            "path": models.as_uri(),
                            "watch": False,
                        }
                    ],
                }
            },
            "runtime-api": {
                "enabled": True,
                "security-mode": "none-dev-test-only",
                "bundle-registry": {"path": str(out / "bundles.json")},
                "datasource-registry": {"path": str(out / "datasources.json")},
                "authoring-workspaces": {
                    "path": str(out / "workspaces"),
                    "published-bundles-path": str(out / "published"),
                },
            },
            "mcp": {"audit": {"enabled": False}},
            "data-viewer": {
                "cache": {
                    "storage": "sqlite",
                    "sqlite-path": str(out / "viewer.sqlite"),
                },
                "list-preset": {
                    "storage": "file",
                    "file-path": str(out / "presets.json"),
                },
            },
        },
    }
    env = os.environ.copy()
    env["SPRING_APPLICATION_JSON"] = json.dumps(config)
    env.pop("OPENAI_API_KEY", None)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    records = {}
    checks = []

    def req(path, body=None, method=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(
            "http://127.0.0.1:" + str(port) + path,
            data=data,
            headers={"Content-Type": "application/json", "X-NS": "binding-smoke"},
            method=method,
        )
        try:
            with opener.open(request, timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            return json.loads(e.read())

    def query(name, payload, mode="execute", success=True):
        result = req(
            "/api/v1/query/" + name + "/" + mode,
            {"payload": payload, "includeSql": True} if mode == "explain" else payload,
        )
        records[name + "-" + mode + "-" + str(len(records))] = result
        assert result.get("success") == success, result
        return result.get("data")

    def pivot(metric, top=False):
        return {
            "pivot": {
                "rows": [
                    dict(
                        field="groupKey",
                        **({"limit": 1, "orderBy": ["-" + metric]} if top else {})
                    )
                ],
                "metrics": [metric],
                "outputFormat": "grid",
                "options": {"grandTotal": False},
            }
        }

    with (out / "launcher.log").open("wb") as log:
        proc = subprocess.Popen(
            ["java", "-Dfile.encoding=UTF-8", "-jar", str(jar)],
            env=env,
            stdout=log,
            stderr=log,
        )
        try:
            for _ in range(120):
                if proc.poll() is not None:
                    raise RuntimeError("launcher exited")
                try:
                    req("/readyz")
                    break
                except Exception:
                    time.sleep(1)
            else:
                raise RuntimeError("launcher readiness timeout")
            source = {
                "name": "smoke-sqlite",
                "type": "sqlite",
                "jdbcUrl": "jdbc:sqlite:" + str(db),
                "replace": True,
            }
            assert req("/api/v1/datasources", source, "POST")["success"]
            assert req(
                "/api/v1/namespaces/binding-smoke/datasource",
                {"dataSource": "smoke-sqlite"},
                "PUT",
            )["success"]
            r = query("Split", pivot("revenue"))["items"][0]
            assert {
                row["groupKey"]: r["cells"][i][0]
                for i, row in enumerate(r["rowHeaders"])
            } == oracle
            checks.append("pivot-bound-keys-and-sql-oracle")
            r = query("Split", pivot("revenue", True))["items"][0]
            assert r["rowHeaders"][0]["groupKey"] == "B" and r["cells"][0][0] == 40
            checks.append("pivot-top-n")
            r = query("Average", pivot("meanSales"))["items"][0]
            assert sorted(cell[0] for cell in r["cells"]) == [15, 40]
            checks.append("renamed-average")
            total = pivot("meanSales")
            total["pivot"]["options"]["grandTotal"] = True
            r = query("Average", total)["items"][0]
            assert any(
                abs(cell[0] - average_oracle) < 1e-8
                for cell in r["cells"]
                if cell[0] is not None
            )
            checks.append("non-additive-grand-total")
            a = query("Formula", {"columns": ["calc"]})
            b = query("Formula", {"columns": ["calc AS requested"]})
            assert (
                len(a["items"]) == len(b["items"]) == 1
                and a["items"][0]["calcOut"] == b["items"][0]["requested"] == 140
            )
            checks.append("formula-alias-and-aggregation")
            assert all(col["name"] in a["items"][0] for col in a["schema"]["columns"])
            checks.append("result-schema")
            query("Restricted", {"columns": ["ranked"]}, success=False)
            r = query("Window", {"columns": ["id", "ranked"]})
            assert len(r["items"]) == 3 and all("rankOut" in row for row in r["items"])
            checks.append("window-permission-and-output")
            r = query("Chain", {"columns": ["secondCalc"]})
            assert sorted(row["secondCalc"] for row in r["items"]) == [
                21,
                41,
                81,
            ] and all("firstCalc" not in row for row in r["items"])
            checks.append("dependency-injection")
            r = query(
                "Base",
                {
                    "columns": ["category", "sum(amount) AS total"],
                    "groupBy": ["category"],
                    "orderBy": ["total"],
                },
            )
            assert [x["total"] for x in r["items"]] == [30, 40]
            checks.append("aggregate-order-alias")
            for distinct in [False, True]:
                r = query(
                    "Base",
                    {
                        "columns": ["category"],
                        "distinct": distinct,
                        "orderBy": ["category", "category"],
                    },
                )
                assert [x["category"] for x in r["items"]] == (
                    ["A", "B"] if distinct else ["A", "A", "B"]
                )
            checks.append("ordinary-and-distinct-duplicate-order")
            for mode in ["validate", "explain"]:
                query(
                    "Split",
                    {"columns": ["groupKey", "revenue"], "groupBy": ["groupKey"]},
                    mode,
                )
            desc = req("/api/v1/models/Split/describe", {"format": "json"})
            assert desc["success"]
            records["metadata"] = desc
            checks.append("metadata-validate-explain")
            qm(
                "Formula",
                base
                + ",{name:'calc',alias:'newOut',formula:'amount * 3',type:'NUMBER'}",
            )
            refreshed = req("/api/v1/models/refresh", {"models": ["Formula"]})
            assert refreshed["success"], refreshed
            r = query("Formula", {"columns": ["calc"]})
            assert r["items"] == [{"newOut": 210}], r
            checks.append("upgrade-refresh-output-and-expression")
            qm(
                "Formula",
                base
                + ",{name:'calc',alias:'secureOut',formula:'secret * 3',type:'NUMBER'}",
                ",fieldPermissions:{hiddenFields:[{fields:['secret']}]}",
            )
            refreshed = req("/api/v1/models/refresh", {"models": ["Formula"]})
            assert refreshed["success"], refreshed
            query("Formula", {"columns": ["calc"]}, success=False)
            checks.append("refresh-rebuilds-permission-dependencies")
        finally:
            (out / "records.json").write_text(
                json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
    (out / "records.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    result = {
        "status": "passed",
        "checks": checks,
        "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "processStopped": True,
    }
    (out / "summary.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == "__main__":
    main()
