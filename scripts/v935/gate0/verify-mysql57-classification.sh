#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
container_name="v935-gate0-mysql57-$$"
mysql_image="mysql:5.7"
mysql_image_id="sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
database="foggy_test"
username="foggy_it"
password="gate0_it_940"
root_password="gate0_root_940"
negative_log="$(mktemp)"

cleanup() {
  docker stop "${container_name}" >/dev/null 2>&1 || true
  rm -f "${negative_log}"
}
trap cleanup EXIT

cd "${repo_root}"

if [[ "${V935_GATE0_SKIP_UNIT:-false}" != "true" ]]; then
  echo "[gate0] verifying hermetic Unit lane"
  mvn -pl foggy-dataset -am -DskipITs test
fi

echo "[gate0] verifying missing MySQL configuration fails closed"
if env -u V935_GATE0_MYSQL57_URL \
    -u V935_GATE0_MYSQL57_USERNAME \
    -u V935_GATE0_MYSQL57_PASSWORD \
    mvn -pl foggy-dataset -Pmysql57-it -DskipUnitTests \
      -Dit.test=DatasetJdbcUtilsTest clean verify >"${negative_log}" 2>&1; then
  echo "expected mysql57-it invocation to fail without required configuration" >&2
  exit 1
fi
grep -q 'V935_GATE0_MYSQL57_URL' "${negative_log}"

echo "[gate0] starting run-owned MySQL 5.7 fixture"
actual_image_id="$(docker image inspect --format '{{.Id}}' "${mysql_image}")"
if [[ "${actual_image_id}" != "${mysql_image_id}" ]]; then
  echo "unexpected local mysql:5.7 image id: ${actual_image_id}" >&2
  exit 1
fi
docker run --rm -d \
  --name "${container_name}" \
  -e MYSQL_ROOT_PASSWORD="${root_password}" \
  -e MYSQL_DATABASE="${database}" \
  -e MYSQL_USER="${username}" \
  -e MYSQL_PASSWORD="${password}" \
  -p 127.0.0.1::3306 \
  "${mysql_image}" >/dev/null

for _ in $(seq 1 60); do
  if docker exec "${container_name}" mysql \
      -uroot -p"${root_password}" -Nse 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! docker exec "${container_name}" mysql \
    -uroot -p"${root_password}" -Nse 'SELECT 1' >/dev/null 2>&1; then
  echo "run-owned MySQL 5.7 fixture did not become healthy" >&2
  exit 1
fi

docker exec -i "${container_name}" mysql \
  -uroot -p"${root_password}" "${database}" <<'SQL'
CREATE TABLE IF NOT EXISTS M_ETL_TEST (
  test_id varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  c1 varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  c2 varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  c3 int(11) DEFAULT NULL,
  c4 varchar(88) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  c5 varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT '2',
  PRIMARY KEY (test_id),
  KEY idx_M_ETL_TEST_c3 (c3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

port_mapping="$(docker port "${container_name}" 3306/tcp)"
host_port="${port_mapping##*:}"
test_classes="FDialectTest,DatasetJdbcUtilsTest,JdbcTableUtilsTest,SyncSqlTableTest,JdbcUpdaterTest,SqlTableRowEditorTest,BugFixInsertUpdateMapTest"

echo "[gate0] verifying governed MySQL 5.7 lane"
V935_GATE0_MYSQL57_URL="jdbc:mysql://127.0.0.1:${host_port}/${database}?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&enabledTLSProtocols=TLSv1.2" \
V935_GATE0_MYSQL57_USERNAME="${username}" \
V935_GATE0_MYSQL57_PASSWORD="${password}" \
  mvn -pl foggy-dataset -Pmysql57-it -DskipUnitTests -Dit.test="${test_classes}" clean verify

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

reports = sorted(Path("foggy-dataset/target/failsafe-reports").glob("TEST-*.xml"))
if len(reports) != 7:
    raise SystemExit(f"expected 7 Failsafe reports, found {len(reports)}")

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.attrib.get(key, "0"))

expected = {"tests": 12, "failures": 0, "errors": 0, "skipped": 0}
if totals != expected:
    raise SystemExit(f"unexpected Failsafe totals: {totals}, expected {expected}")
print("[gate0] verified 7 reports / 12 testcase nodes / F0E0S0")
PY
