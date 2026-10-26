#!/usr/bin/env python3
"""Small artifact-only Compose contract gate, using isolated HTTP/SQLite fixtures."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

MODES = ["validate", "preview", "execute"]
READONLY_CASES = frozenset({
    "group-owner-rename", "window-owner-rename", "window-offset-two", "union-branch-owner",
    "shared-group-forward", "shared-group-reverse", "shared-window-forward", "shared-window-reverse",
    "offset-overflow", "offset-fraction", "offset-string", "method-extra", "result-shape",
    "result-descriptor", "unsupported-timewindow", "explicit-execute", "pure-governance", "unsafe-governance",
})
REQUIRED_CHECKS = READONLY_CASES | {
    "native-plain-window", "native-mask-control", "native-deny-control", "native-mask-window",
    "native-deny-window", "native-pure-window", "native-terminal-window", "native-terminal-validate",
}


class GateError(RuntimeError):
    pass


def sha256(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def valid_counter(value: object) -> bool:
    return type(value) is int and value >= 0


def rejection_matches(response: dict, expected: str) -> bool:
    if expected == "owner":
        code = response.get("diagnostics", {}).get("attributes", {}).get("composeErrorCode")
        return code in {"compose-schema-error/derived-query/unknown-field", "compose-schema-error/column/plan-not-bound"}
    return expected in json.dumps(response, ensure_ascii=False)


def validate_summary(summary: object, expected_sha256: str) -> None:
    """Publisher admission: an absent probe must never masquerade as zero SQL."""
    if not isinstance(summary, dict):
        raise GateError("Compose stability summary must be an object")
    checks = summary.get("checks")
    if (summary.get("status") != "passed" or summary.get("processStopped") is not True
            or summary.get("launcherSha256") != expected_sha256 or summary.get("modes") != MODES
            or summary.get("g10") is not False or summary.get("counterFilterSelfTest") is not True
            or not isinstance(checks, list) or not all(isinstance(item, str) for item in checks)
            or len(checks) != len(set(checks)) or not REQUIRED_CHECKS.issubset(checks)):
        raise GateError("Compose stability evidence is incomplete, failed or mismatched")
    readonly = summary.get("readonlySql")
    positive = summary.get("positiveSql")
    if not isinstance(readonly, list) or not readonly or not isinstance(positive, list) or not positive:
        raise GateError("Compose stability SQL counters are missing")
    seen = set()
    for row in readonly:
        if (not isinstance(row, dict) or not isinstance(row.get("case"), str)
                or row.get("mode") not in MODES[:2]
                or not valid_counter(row.get("prepared")) or not valid_counter(row.get("executed"))
                or row["prepared"] != 0 or row["executed"] != 0):
            raise GateError("Compose validate/preview counters must prove zero business SQL")
        key = row["case"], row["mode"]
        if key in seen:
            raise GateError("Compose stability readonly observations are duplicated")
        seen.add(key)
    if not {(case, mode) for case in READONLY_CASES for mode in MODES[:2]}.issubset(seen):
        raise GateError("Compose stability readonly mode matrix is incomplete")
    for row in positive:
        if (not isinstance(row, dict) or row.get("mode") != "execute"
                or not isinstance(row.get("case"), str)
                or not valid_counter(row.get("prepared")) or not valid_counter(row.get("executed"))
                or row["prepared"] == 0 or row["executed"] == 0):
            raise GateError("Compose stability positive SQL control did not exercise the probe")
    if not any(row["case"] == "native-plain-window" for row in positive):
        raise GateError("Compose stability native time-window SQL control is missing")


def argument_file(path: Path, values: list[str]) -> None:
    path.write_text("\n".join('"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"' for value in values), encoding="utf-8")


def build_fixture(jar: Path, output: Path) -> tuple[Path, bool]:
    compiler = shutil.which("javac")
    if compiler is None:
        raise GateError("Compose artifact gate requires the release build JDK (javac)")
    libraries = output / "libraries"
    libraries.mkdir()
    with zipfile.ZipFile(jar) as archive:
        if "org/springframework/boot/loader/launch/PropertiesLauncher.class" not in archive.namelist():
            raise GateError("Candidate is missing its PropertiesLauncher")
        for entry in archive.namelist():
            if entry.startswith("BOOT-INF/lib/") and entry.endswith(".jar"):
                (libraries / Path(entry).name).write_bytes(archive.read(entry))
    classes = output / "fixture-classes"
    classes.mkdir()
    fixture_source = Path(__file__).parent / "tests" / "fixtures" / "ComposeStabilityFixture.java"
    fixture = output / "ComposeStabilityFixture.java"
    fixture.write_bytes(fixture_source.read_bytes())
    classpath = os.pathsep.join(path.as_posix() for path in sorted(libraries.glob("*.jar")))
    args = output / "fixture-compile.args"
    argument_file(args, ["--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", classpath, "-d", classes.as_posix(), fixture.as_posix()])
    with (output / "fixture-compile.log").open("wb") as log:
        subprocess.run([compiler, "@" + str(args)], check=True, stdout=log, stderr=subprocess.STDOUT)
    args = output / "counter-filter.args"
    argument_file(args, ["-cp", classes.as_posix() + os.pathsep + classpath,
                         "io.foggytest.release.ComposeStabilityFixture", "--counter-filter-self-test"])
    with (output / "counter-filter.log").open("wb") as log:
        subprocess.run(["java", "@" + str(args)], check=True, stdout=log, stderr=subprocess.STDOUT)
    imports = classes / "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    imports.parent.mkdir(parents=True)
    imports.write_text("io.foggytest.release.ComposeStabilityFixture\n", encoding="utf-8")
    fixture_jar = output / "release-fixture.jar"
    with zipfile.ZipFile(fixture_jar, "w") as archive:
        for path in classes.rglob("*"):
            if path.is_file():
                archive.write(path, path.relative_to(classes).as_posix())
    return fixture_jar, True


def make_models(output: Path) -> Path:
    models = output / "models"
    models.mkdir()
    with sqlite3.connect(output / "fixture.sqlite") as db:
        db.execute("CREATE TABLE compose_stability_fact(id INTEGER PRIMARY KEY,grp INTEGER,occurred_at TEXT,amount INTEGER)")
        db.executemany("INSERT INTO compose_stability_fact VALUES(?,?,?,?)",
                       [(1, 1, "2026-01-01", 10), (2, 1, "2026-01-02", 20),
                        (3, 2, "2026-01-03", 30), (4, 2, "2026-01-04", 40)])
    (models / "Stability.tm").write_text(
        "export const model={name:'Stability',tableName:'compose_stability_fact',idColumn:'id',"
        "properties:[{name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},"
        "{name:'occurredAt',column:'occurred_at',type:'DAY'}],"
        "measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'}]};", encoding="utf-8")
    for name in ["GateQM", "UnsafeMask", "UnsafeDeny", "PureDeny", "TerminalDeny"]:
        (models / (name + ".qm")).write_text(
            "const m=loadTableModel('Stability');export const queryModel={name:'" + name
            + "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:"
              "[{ref:m.id},{ref:m.grp},{ref:m.occurredAt},{ref:m.amount}]}],accesses:[]};", encoding="utf-8")
    return models


def scripts() -> list[tuple[str, str, str | None]]:
    base = "const p=dsl({model:'GateQM',columns:['id','grp as cat','amount as v']});"
    renamed = base + "const w=p.query({columns:['id','cat as v','v as renamed']});"
    shared = "const a=dsl({model:'GateQM',columns:['id','amount']});const b=dsl({model:'GateQM',columns:['id','amount']});"
    grouped = shared + "const aid=a.id;const av=a.amount;const good=a.groupBy(aid,av);const bad=b.groupBy(aid,av);"
    windowed = shared + "const aid=a.id;const win=Query.col('amount').lag().over({partitionBy:[aid],orderBy:['amount']}).as('prior');const good=a.select(win);const bad=b.select(win);"
    values = [
        ("group-owner-rename", renamed + "return {plans:w.groupBy(p.v)};", None),
        ("window-owner-rename", renamed + "return {plans:w.select(w.renamed,w.renamed.lag().over({partitionBy:[p.v],orderBy:['id']}).as('prior'))};", None),
        ("window-offset-two", renamed + "return {plans:w.select(w.renamed,w.renamed.lag(2).over({orderBy:['renamed']}).as('prior'))};", None),
        ("union-branch-owner", base + "const u=p.union(p,{all:true});return {plans:u.groupBy(p.v)};", "owner"),
        ("method-extra", base + "return {plans:p.execute({limit:0})};", "COMPOSE_ARGUMENT_COUNT_INVALID"),
        ("result-shape", base + "return {value:{nested:p}};", "COMPOSE_RESULT_SHAPE_UNSUPPORTED"),
        ("result-descriptor", base + "return {plans:p.v};", "COMPOSE_RESULT_SHAPE_UNSUPPORTED"),
        ("unsupported-timewindow", "return {plans:dsl({model:'GateQM',columns:['id'],timeWindow:{comparison:'mtd'}})};", "COMPOSE_PARAMETER_UNSUPPORTED"),
        ("explicit-execute", base + "return {plans:p.execute()};", "readonly-execute"),
        ("pure-governance", "return {plans:dsl({model:'PureDeny',columns:['id','amount']})};", "RELEASE_COMPOSE_POLICY_DENIED"),
        ("unsafe-governance", "return {plans:dsl({model:'UnsafeMask',columns:['id','amount']})};", "COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN"),
    ]
    for label, code in [("shared-group", grouped), ("shared-window", windowed)]:
        values.extend([(label + "-forward", code + "return {plans:good.union(bad,{all:true})};", "owner"),
                       (label + "-reverse", code + "return {plans:bad.union(good,{all:true})};", "owner")])
    for label, offset in [("overflow", "4294967296"), ("fraction", "1.9"), ("string", "'2'")]:
        values.append(("offset-" + label, renamed + "return {plans:w.select(w.renamed.lag(" + offset + ").over({orderBy:['id']}).as('prior'))};", "COMPOSE_PARAMETER_TYPE_INVALID"))
    return values


def execute_gate(jar: Path, output: Path) -> dict:
    output.mkdir(parents=True, exist_ok=False)
    (output / "gate-source.py").write_bytes(Path(__file__).read_bytes())
    expected_sha = sha256(jar)
    summary = {"status": "failed", "launcherSha256": expected_sha, "processStopped": False,
               "modes": MODES, "g10": False, "counterFilterSelfTest": False, "checks": [],
               "readonlySql": [], "positiveSql": []}
    records = []
    process = None
    try:
        fixture, summary["counterFilterSelfTest"] = build_fixture(jar, output)
        models = make_models(output)
        user_home = output / "jvm-home"
        datasource_directory = output / "mcp-datasources"
        user_home.mkdir()
        datasource_directory.mkdir()
        with socket.socket() as socket_:
            socket_.bind(("127.0.0.1", 0))
            port = socket_.getsockname()[1]
        config = {"server": {"address": "127.0.0.1", "port": port},
                  "spring": {"profiles": {"active": "lite"}, "datasource": {"url": "jdbc:sqlite:" + str(output / "fixture.sqlite")}, "sql": {"init": {"mode": "never"}}},
                  "foggy": {"auth": {"mode": "DISABLED"}, "demo": {"enabled": False},
                            "datasource": {"config": {"dir": str(datasource_directory)}},
                            "bundle": {"external": {"enabled": True, "bundles": [{"name": "compose-stability-smoke", "namespace": "", "path": models.as_uri(), "watch": False}]}},
                            "runtime-api": {"enabled": True, "security-mode": "none-dev-test-only", "bundle-registry": {"path": str(output / "bundles.json")},
                                            "datasource-registry": {"path": str(output / "datasources.json")}, "authoring-workspaces": {"path": str(output / "workspaces"), "published-bundles-path": str(output / "published")}},
                            "data-viewer": {"enabled": False}, "mcp": {"audit": {"enabled": False}}}}
        (output / "runtime-config.json").write_text(json.dumps(config, indent=2), encoding="utf-8")
        summary["isolation"] = {"workingDirectory": str(output), "userHome": str(user_home),
                                "mcpDatasourceDirectory": str(datasource_directory),
                                "runtimeBundleRegistry": str(output / "bundles.json"),
                                "runtimeDatasourceRegistry": str(output / "datasources.json")}
        environment = os.environ.copy()
        environment["SPRING_APPLICATION_JSON"] = json.dumps(config)
        environment.pop("OPENAI_API_KEY", None)
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

        def request(path, payload=None, timeout=30):
            body = None if payload is None else json.dumps(payload).encode("utf-8")
            message = urllib.request.Request("http://127.0.0.1:" + str(port) + path, data=body,
                                             headers={"Content-Type": "application/json"})
            try:
                response = opener.open(message, timeout=timeout)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                return response.status, json.load(response)

        with (output / "launcher.log").open("wb") as log:
            command = ["java", "-Dfile.encoding=UTF-8", "-Duser.home=" + str(user_home),
                       "-Dfoggy.compose.g10.enabled=false", "-Dloader.path=" + str(fixture),
                       "-cp", str(jar), "org.springframework.boot.loader.launch.PropertiesLauncher"]
            (output / "launcher-command.json").write_text(json.dumps(command, indent=2), encoding="utf-8")
            process = subprocess.Popen(command, env=environment, cwd=output, stdout=log, stderr=log)
            readiness_deadline = time.monotonic() + 120
            ready_found = False
            while time.monotonic() < readiness_deadline:
                if process.poll() is not None:
                    raise GateError("Isolated candidate launcher exited before readiness")
                try:
                    status, ready = request("/readyz", timeout=1)
                    if status == 200 and ready.get("status") == "ready":
                        ready_found = True
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            if not ready_found:
                raise GateError("Isolated candidate launcher readiness timed out")
            status, probe = request("/__release_compose_probe/counters")
            if status != 200 or probe.get("g10") is not False or jar.name not in probe.get("engineOrigin", ""):
                raise GateError("Counter fixture is missing or engine did not load from the candidate JAR")
            summary["engineOrigin"] = probe["engineOrigin"]
            for model in ["GateQM", "UnsafeMask", "UnsafeDeny", "PureDeny", "TerminalDeny"]:
                status, response = request("/api/v1/models/" + model + "/describe", {"format": "json"})
                if status != 200 or response.get("success") is not True:
                    raise GateError("Fixture model did not load: " + model)

            def call(label, mode, payload, native=None, reject=None):
                status, reset = request("/__release_compose_probe/reset", {})
                if status != 200 or reset.get("prepared") != 0 or reset.get("executed") != 0:
                    raise GateError("Business SQL counters did not reset")
                endpoint = "/api/v1/query/" + native + "/" + mode if native else "/api/v1/compose/" + mode
                status, response = request(endpoint, payload)
                counter_status, counters = request("/__release_compose_probe/counters")
                record = {"case": label, "mode": mode, "endpoint": endpoint, "httpStatus": status, "response": response, "counters": counters}
                records.append(record)
                if counter_status != 200 or not all(valid_counter(counters.get(key)) for key in ["prepared", "executed"]):
                    raise GateError("Business SQL probe returned missing or invalid counters")
                observation = {"case": label, "mode": mode, "prepared": counters["prepared"], "executed": counters["executed"]}
                if mode != "execute":
                    summary["readonlySql"].append(observation)
                    if observation["prepared"] != 0 or observation["executed"] != 0:
                        raise GateError(label + " executed business SQL in " + mode)
                if reject:
                    text = json.dumps(response, ensure_ascii=False)
                    if status != 200 or response.get("success") is not False or not rejection_matches(response, reject) or counters["prepared"] != 0 or counters["executed"] != 0:
                        raise GateError(label + "/" + mode + " did not reject safely: " + text)
                elif status != 200 or response.get("success") is not True:
                    raise GateError(label + "/" + mode + " failed: " + json.dumps(response, ensure_ascii=False))
                elif mode == "execute":
                    if counters["prepared"] == 0 or counters["executed"] == 0:
                        raise GateError(label + " positive SQL control did not reach the probe")
                    summary["positiveSql"].append(observation)
                return response, counters

            plain = {"columns": ["occurredAt", "amount"], "groupBy": [{"field": "occurredAt"}], "orderBy": [{"field": "occurredAt", "dir": "asc"}]}
            masked, counts = call("native-mask-control", "execute", plain, "UnsafeMask")
            if counts.get("maskCalls") != 1 or not masked["data"]["items"] or any(row["amount"] != 0 for row in masked["data"]["items"]):
                raise GateError("Native mask control did not invoke its real execution hook")
            call("native-deny-control", "execute", plain, "UnsafeDeny", "RELEASE_COMPOSE_POLICY_DENIED")
            window = {**plain, "returnTotal": True, "columns": ["occurredAt", "amount", "amount__mtd"],
                      "timeWindow": {"field": "occurredAt", "grain": "day", "comparison": "mtd", "value": ["2026-01-01", "2026-01-31"], "targetMetrics": ["amount"]}}
            result, _ = call("native-plain-window", "execute", window, "GateQM")
            if [row["amount__mtd"] for row in result["data"]["items"]] != [10, 30, 60, 100]:
                raise GateError("Native time-window numerical control failed")
            if result["data"].get("total") != 4:
                raise GateError("Native time-window count control failed")
            for label, model, code in [("native-mask-window", "UnsafeMask", "COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN"),
                                       ("native-deny-window", "UnsafeDeny", "COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN"),
                                       ("native-pure-window", "PureDeny", "RELEASE_COMPOSE_POLICY_DENIED"),
                                       ("native-terminal-window", "TerminalDeny", "RELEASE_COMPOSE_POLICY_DENIED")]:
                call(label, "execute", window, model, code)
            call("native-terminal-validate", "validate", window, "TerminalDeny", "RELEASE_COMPOSE_POLICY_DENIED")
            summary["checks"].extend(sorted(REQUIRED_CHECKS - READONLY_CASES))
            for label, script, code in scripts():
                (output / (label + ".fsscript")).write_text(script, encoding="utf-8")
                for mode in MODES:
                    expected = "COMPOSE_EXPLICIT_EXECUTE_FORBIDDEN" if code == "readonly-execute" and mode != "execute" else None if code == "readonly-execute" else code
                    response, _ = call(label, mode, {"script": script, "options": {"dialect": "sqlite"}}, reject=expected)
                    if mode == "execute" and code is None:
                        rows = response["data"]["value"]["plans"]
                        if len(rows) != 4 or sorted(row.get("renamed") for row in rows) != [10, 20, 30, 40]:
                            raise GateError(label + " ownership numerical control failed")
                        if label == "window-owner-rename" and any(row.get("prior") is not None for row in rows):
                            raise GateError("Window partition owner bound to a same-named sibling")
                        if label == "window-offset-two" and [row.get("prior") for row in sorted(rows, key=lambda row: row["renamed"])] != [None, None, 10, 20]:
                            raise GateError("Window offset numerical control failed")
                summary["checks"].append(label)
        if sha256(jar) != expected_sha:
            raise GateError("Artifact gate modified the candidate JAR")
        summary["status"] = "passed"
    except (GateError, OSError, ValueError, KeyError, subprocess.SubprocessError) as failure:
        summary["error"] = str(failure)
    finally:
        if process is not None:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            summary["processStopped"] = process.poll() is not None
        else:
            summary["processStopped"] = True
        (output / "responses.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
        (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    if summary["status"] == "passed":
        try:
            validate_summary(summary, expected_sha)
        except GateError as failure:
            summary["status"] = "failed"
            summary["error"] = str(failure)
            (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    summary = execute_gate(args.jar.resolve(), args.output.resolve())
    print(json.dumps(summary, ensure_ascii=False))
    return 0 if summary["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
