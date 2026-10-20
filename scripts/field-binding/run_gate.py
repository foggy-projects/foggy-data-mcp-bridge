#!/usr/bin/env python3
"""Run field binding regression against SQLite or an isolated, real SQL database."""

from pathlib import Path
import argparse, contextlib, json, os, secrets, shutil, subprocess, time, uuid, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SQLITE_TESTS = "FieldBindingDependenciesRegressionTest,CalculatedFieldDependenciesTest,CalculatedDependencyGraphTest,QueryFieldBindingsTest,FieldAccessPermissionStepTest,CalculatedFieldServiceTest,SqlCalculatedFieldProcessorWindowOrderTest,MetricAdditivityAnalyzerTest,InlineExpressionPreprocessStepTest,QmFieldNamingRegressionTest,SemanticServiceV3Test,SemanticServiceV3MultiModelGovernanceTest,PhysicalColumnMappingIT,PhysicalColumnPermissionIT,FieldAccessPermissionIT,OdooModelLoadingTest,HierarchyTreeBuilderTest".split(
    ","
)
MATRIX_TESTS = "QmFieldNamingRegressionTest,NestedDimensionTest,DistinctOrderByRegressionIT,MultiDatabaseQueryTest,RequiredDatabaseQueryFacadeParityIT,FieldBindingDependenciesRegressionTest".split(
    ","
)
DATABASES = {
    "mysql57": ("mysql:5.7", 3306, "docker", "mysql57", "com.mysql.cj.jdbc.Driver"),
    "mysql8": ("mysql:8.0.44", 3306, "mysql8", "mysql8", "com.mysql.cj.jdbc.Driver"),
    "postgres": (
        "postgres:15",
        5432,
        "postgres",
        "postgres15",
        "org.postgresql.Driver",
    ),
    "sqlserver": (
        "mcr.microsoft.com/mssql/server:2022-latest",
        1433,
        "sqlserver",
        "sqlserver2022",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
    ),
}


def command(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


@contextlib.contextmanager
def database(name, external, out):
    if name == "sqlite":
        yield {}
        return
    if external:
        required = [
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
        ]
        if any(k not in os.environ for k in required):
            raise RuntimeError(
                "External database requires all SPRING_DATASOURCE connection variables"
            )
        yield {k: os.environ[k] for k in required} | {
            "SPRING_DATASOURCE_DRIVER_CLASS_NAME": DATABASES[name][4]
        }
        return
    image, port, _, _, driver = DATABASES[name]
    identity = "binding-gate-" + uuid.uuid4().hex[:12]
    password = secrets.token_hex(18) + "Aa1!"
    kind = "mysql" if name.startswith("mysql") else name
    env = {
        "mysql": {
            "MYSQL_ROOT_PASSWORD": password,
            "MYSQL_ROOT_HOST": "%",
            "MYSQL_DATABASE": "foggy_test",
        },
        "postgres": {"POSTGRES_PASSWORD": password, "POSTGRES_DB": "foggy_test"},
        "sqlserver": {
            "ACCEPT_EULA": "Y",
            "MSSQL_SA_PASSWORD": password,
            "MSSQL_MEMORY_LIMIT_MB": "2048",
        },
    }[kind]
    args = [
        "docker",
        "run",
        "-d",
        "--name",
        identity,
        "--label",
        "com.foggy.field-binding-gate=" + identity,
        "-p",
        f"127.0.0.1::{port}",
    ]
    for key, value in env.items():
        args += ["-e", key + "=" + value]
    started = False
    try:
        command(args + [image], stdout=subprocess.DEVNULL)
        started = True
        hostport = (
            subprocess.check_output(
                ["docker", "port", identity, f"{port}/tcp"], text=True
            )
            .strip()
            .split(":")[-1]
        )
        if kind == "mysql":
            client = [
                "docker",
                "exec",
                "-i",
                "-e",
                "MYSQL_PWD=" + password,
                identity,
                "mysql",
                "--default-character-set=utf8mb4",
                "-uroot",
                "foggy_test",
            ]
            probe = ["-e", "SELECT 1"]
            user = "root"
            url = f"jdbc:mysql://127.0.0.1:{hostport}/foggy_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
        elif kind == "postgres":
            client = [
                "docker",
                "exec",
                "-i",
                identity,
                "psql",
                "-U",
                "postgres",
                "-d",
                "foggy_test",
                "-v",
                "ON_ERROR_STOP=1",
            ]
            probe = ["-c", "SELECT 1"]
            user = "postgres"
            url = f"jdbc:postgresql://127.0.0.1:{hostport}/foggy_test"
        else:
            client = [
                "docker",
                "exec",
                "-i",
                "-e",
                "SQLCMDPASSWORD=" + password,
                identity,
                "/opt/mssql-tools18/bin/sqlcmd",
                "-S",
                "localhost",
                "-U",
                "sa",
                "-C",
                "-b",
            ]
            probe = ["-Q", "SELECT 1"]
            user = "sa"
            url = f"jdbc:sqlserver://127.0.0.1:{hostport};databaseName=foggy_test;encrypt=false;trustServerCertificate=true"
        for _ in range(150):
            if (
                subprocess.run(
                    client + probe, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
                ).returncode
                == 0
            ):
                break
            time.sleep(1)
        else:
            raise RuntimeError("Temporary database did not become ready")
        with (out / "fixture.log").open("wb") as log:
            if kind == "mysql":
                preamble = "SET GLOBAL innodb_flush_log_at_trx_commit=2; SET GLOBAL sync_binlog=0; ALTER DATABASE foggy_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; SET SESSION time_zone='+08:00'; SET SESSION timestamp=1710864000; SET SESSION rand_seed1=934, SESSION rand_seed2=934;\n"
                files = [
                    p
                    for p in sorted(
                        (ROOT / "foggy-dataset-demo/docker/mysql/init").glob("*.sql")
                    )
                    if p.name[:2]
                    in [
                        "01",
                        "02",
                        "03",
                        "04",
                        "06",
                        "07",
                        "08",
                        "09",
                        "10",
                        "11",
                        "14",
                    ]
                ]
                command(
                    client,
                    input=(
                        preamble
                        + "\n".join(p.read_text(encoding="utf-8-sig") for p in files)
                    ).encode(),
                    stdout=log,
                    stderr=log,
                )
            else:
                prefixes = (
                    ["01", "02", "03", "06", "07", "08", "09", "14"]
                    if kind == "postgres"
                    else ["01", "02", "03"]
                )
                for p in sorted(
                    (ROOT / "foggy-dataset-demo/docker" / kind / "init").glob("*.sql")
                ):
                    if p.name[:2] in prefixes:
                        command(client, input=p.read_bytes(), stdout=log, stderr=log)
            if kind == "sqlserver":
                client += ["-d", "foggy_test"]
            for p in sorted(
                (ROOT / "foggy-dataset-demo/docker/v934" / kind).glob("1[234]*.sql")
            ):
                command(client, input=p.read_bytes(), stdout=log, stderr=log)
        yield {
            "SPRING_DATASOURCE_URL": url,
            "SPRING_DATASOURCE_USERNAME": user,
            "SPRING_DATASOURCE_PASSWORD": password,
            "SPRING_DATASOURCE_DRIVER_CLASS_NAME": driver,
        }
    finally:
        if started:
            info = json.loads(subprocess.check_output(["docker", "inspect", identity]))[
                0
            ]
            if info["Config"]["Labels"].get("com.foggy.field-binding-gate") != identity:
                raise RuntimeError("Temporary container ownership mismatch")
            command(["docker", "rm", "-fv", identity], stdout=subprocess.DEVNULL)


def summarize(reports, started, required):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    classes = {}
    details = []
    for path in reports.glob("TEST-*.xml"):
        if path.stat().st_mtime < started:
            continue
        root = ET.parse(path).getroot()
        name = root.get("name", "")
        short = name.rsplit(".", 1)[-1].split("$")[0]
        counts = {key: int(root.get(key, "0")) for key in totals}
        for key in totals:
            totals[key] += counts[key]
        classes[short] = classes.get(short, 0) + counts["tests"]
        details.append(dict(name=name, **counts))
    missing = [name for name in required if classes.get(name, 0) == 0]
    return dict(totals, missingClasses=missing, details=details)


def verify(summary, exit_code):
    if (
        exit_code
        or not summary["tests"]
        or summary["missingClasses"]
        or any(summary[k] for k in ["failures", "errors", "skipped"])
    ):
        raise RuntimeError(
            "Field binding gate failed; inspect summary.json and maven.log"
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", choices=["sqlite", *DATABASES], default="sqlite")
    parser.add_argument("--external", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--maven", default=shutil.which("mvn") or "mvn")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    required = list(SQLITE_TESTS if args.database == "sqlite" else MATRIX_TESTS)
    if args.database in ["mysql8", "postgres"]:
        required += ["PivotSqlParityIT", "PivotCascadeGenerateSqlParityIT"]
    env = os.environ.copy()
    env["MAVEN_OPTS"] = "-Xmx2048m"
    env["TZ"] = "Asia/Shanghai"
    with database(args.database, args.external, out) as connection:
        env.update(connection)
        profile = "sqlite" if args.database == "sqlite" else DATABASES[args.database][2]
        cmd = [
            args.maven,
            "-B",
            "-ntp",
            *(["-o"] if args.offline else []),
            "-pl",
            "foggy-dataset-model-engine",
            "-am",
            "test",
            "-DskipITs=true",
            "-DskipUnitTests=false",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "-Dsurefire.failIfNoTests=false",
            "-DargLine=-Xmx2048m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai",
            "-Dspring.profiles.active=" + profile,
            "-Dtest=" + ",".join(required),
        ]
        if args.database != "sqlite":
            cmd += ["-Dv934.expectedDatabase=" + DATABASES[args.database][3]]
        (out / "command.json").write_text(json.dumps(cmd, indent=2))
        started = time.time()
        with (out / "maven.log").open("wb") as log:
            result = subprocess.run(cmd, cwd=ROOT, env=env, stdout=log, stderr=log)
        report = summarize(
            ROOT / "foggy-dataset-model-engine/target/surefire-reports",
            started,
            required,
        )
        report.update(
            database=args.database, exitCode=result.returncode, requiredClasses=required
        )
        (out / "summary.json").write_text(json.dumps(report, indent=2))
        verify(report, result.returncode)
        print(json.dumps({k: v for k, v in report.items() if k != "details"}))


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as failure:
        raise SystemExit(
            "Gate command failed with exit code "
            + str(failure.returncode)
            + "; inspect local evidence"
        ) from None
