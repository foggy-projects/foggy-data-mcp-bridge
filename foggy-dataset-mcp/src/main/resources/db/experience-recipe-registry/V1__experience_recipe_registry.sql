-- Experience recipe registry schema v1.
-- The DDL is intentionally limited to portable table definitions used by both
-- SQLite tests and MySQL deployments; indexes are created through metadata
-- checks because MySQL does not support CREATE INDEX IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS experience_recipe_registry (
    registry_key VARCHAR(255) PRIMARY KEY,
    recipe_id VARCHAR(255) NOT NULL,
    recipe_version VARCHAR(64) NOT NULL,
    canonical_recipe_id VARCHAR(255),
    title VARCHAR(500),
    business_type VARCHAR(255),
    route VARCHAR(64),
    namespace_scope VARCHAR(1000),
    tenant_scope VARCHAR(1000),
    permission_tags VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    active_for_discovery INTEGER NOT NULL,
    owner_role VARCHAR(64),
    record_version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS experience_recipe_registry_event (
    event_id VARCHAR(64) PRIMARY KEY,
    registry_key VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    api_result VARCHAR(64) NOT NULL,
    failure_stage VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    from_active_for_discovery INTEGER NOT NULL,
    to_active_for_discovery INTEGER NOT NULL,
    response_status VARCHAR(32) NOT NULL,
    response_active_for_discovery INTEGER NOT NULL,
    response_discoverable INTEGER NOT NULL,
    evidence_artifacts_json TEXT,
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS experience_recipe_closure_event (
    closure_event_id VARCHAR(64) PRIMARY KEY,
    registry_key VARCHAR(255) NOT NULL,
    closure_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);
