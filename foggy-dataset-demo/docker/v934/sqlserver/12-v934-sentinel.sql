-- 9.3.4 required-database sentinel fixture.
-- Canonical LF-terminated manifest line:
-- v934_test_sentinel|contract_version|9.3.4
-- SHA-256: cef04c4c1269e1293bf243e61e0a9672697bfd55b0bca48297943026bd82c191

USE foggy_test;
GO

IF OBJECT_ID(N'dbo.v934_test_sentinel', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.v934_test_sentinel (
        sentinel_key VARCHAR(64) NOT NULL,
        sentinel_value VARCHAR(64) NOT NULL,
        CONSTRAINT pk_v934_test_sentinel PRIMARY KEY (sentinel_key)
    );
END;
GO

IF EXISTS (
    SELECT 1
    FROM dbo.v934_test_sentinel
    WHERE sentinel_key = 'contract_version'
)
BEGIN
    UPDATE dbo.v934_test_sentinel
    SET sentinel_value = '9.3.4'
    WHERE sentinel_key = 'contract_version';
END
ELSE
BEGIN
    INSERT INTO dbo.v934_test_sentinel (sentinel_key, sentinel_value)
    VALUES ('contract_version', '9.3.4');
END;
GO
