-- 9.3.4 required-database sentinel fixture.
-- Canonical LF-terminated manifest line:
-- v934_test_sentinel|contract_version|9.3.4
-- SHA-256: cef04c4c1269e1293bf243e61e0a9672697bfd55b0bca48297943026bd82c191

CREATE TABLE IF NOT EXISTS v934_test_sentinel (
    sentinel_key TEXT NOT NULL PRIMARY KEY,
    sentinel_value TEXT NOT NULL
);

INSERT INTO v934_test_sentinel (sentinel_key, sentinel_value)
VALUES ('contract_version', '9.3.4')
ON CONFLICT (sentinel_key)
DO UPDATE SET sentinel_value = excluded.sentinel_value;
