package com.foggyframework.dataset.mcp.experience;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "foggy.mcp.experience-recipe.registry", name = "store", havingValue = "jdbc")
public class JdbcExperienceRecipeRegistryStore implements ExperienceRecipeRegistryStore {
    private static final RowMapper<ExperienceRecipeRegistryEntry> ENTRY_ROW_MAPPER = (rs, rowNum) -> {
        ExperienceRecipeRegistryEntry entry = new ExperienceRecipeRegistryEntry();
        entry.setRegistryKey(rs.getString("registry_key"));
        entry.setRecipeId(rs.getString("recipe_id"));
        entry.setRecipeVersion(rs.getString("recipe_version"));
        entry.setCanonicalRecipeId(rs.getString("canonical_recipe_id"));
        entry.setTitle(rs.getString("title"));
        entry.setBusinessType(rs.getString("business_type"));
        entry.setRoute(rs.getString("route"));
        entry.setNamespaceScope(rs.getString("namespace_scope"));
        entry.setTenantScope(rs.getString("tenant_scope"));
        entry.setPermissionTags(rs.getString("permission_tags"));
        entry.setStatus(ExperienceRecipeStatus.fromWireValue(rs.getString("status")));
        entry.setActiveForDiscovery(rs.getInt("active_for_discovery") == 1);
        entry.setOwnerRole(rs.getString("owner_role"));
        long recordVersion = rs.getLong("record_version");
        entry.setRecordVersion(rs.wasNull() ? null : recordVersion);
        entry.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        entry.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return entry;
    };

    private static final RowMapper<ExperienceRecipeRegistryEvent> EVENT_ROW_MAPPER = (rs, rowNum) -> {
        ExperienceRecipeRegistryEvent event = new ExperienceRecipeRegistryEvent();
        event.setEventId(rs.getString("event_id"));
        event.setRegistryKey(rs.getString("registry_key"));
        event.setIdempotencyKey(rs.getString("idempotency_key"));
        event.setOperation(ExperienceRecipeRegistryOperation.fromWireValue(rs.getString("operation")));
        event.setActorRole(rs.getString("actor_role"));
        event.setApiResult(ExperienceRecipeApiResult.valueOf(rs.getString("api_result")));
        event.setFailureStage(ExperienceRecipeFailureStage.fromWireValue(rs.getString("failure_stage")));
        event.setFromStatus(ExperienceRecipeStatus.fromWireValue(rs.getString("from_status")));
        event.setToStatus(ExperienceRecipeStatus.fromWireValue(rs.getString("to_status")));
        event.setFromActiveForDiscovery(rs.getInt("from_active_for_discovery") == 1);
        event.setToActiveForDiscovery(rs.getInt("to_active_for_discovery") == 1);
        event.setResponseStatus(ExperienceRecipeStatus.fromWireValue(rs.getString("response_status")));
        event.setResponseActiveForDiscovery(rs.getInt("response_active_for_discovery") == 1);
        event.setResponseDiscoverable(rs.getInt("response_discoverable") == 1);
        event.setReason(rs.getString("reason"));
        event.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        return event;
    };

    private final JdbcTemplate jdbcTemplate;
    private final ExperienceRecipeRegistryProperties properties;
    private final ExperienceRecipeRegistrySchemaInitializer schemaInitializer;

    public JdbcExperienceRecipeRegistryStore(
            JdbcTemplate jdbcTemplate,
            ExperienceRecipeRegistryProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.schemaInitializer = new ExperienceRecipeRegistrySchemaInitializer(jdbcTemplate);
    }

    @PostConstruct
    public void init() {
        if (properties.isAutoInitSchema()) {
            initializeSchema();
        }
    }

    public void initializeSchema() {
        schemaInitializer.initializeSchema();
    }

    @Override
    public Optional<ExperienceRecipeRegistryEntry> findByRegistryKey(String registryKey) {
        List<ExperienceRecipeRegistryEntry> rows = jdbcTemplate.query("""
                SELECT registry_key, recipe_id, recipe_version, canonical_recipe_id, title, business_type, route,
                       namespace_scope, tenant_scope, permission_tags, status, active_for_discovery, owner_role,
                       record_version, created_at, updated_at
                FROM experience_recipe_registry
                WHERE registry_key = ?
                """, ENTRY_ROW_MAPPER, registryKey);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ExperienceRecipeRegistryEvent> findEventByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        List<ExperienceRecipeRegistryEvent> rows = jdbcTemplate.query("""
                SELECT event_id, registry_key, idempotency_key, operation, actor_role, api_result, failure_stage,
                       from_status, to_status, from_active_for_discovery, to_active_for_discovery,
                       response_status, response_active_for_discovery, response_discoverable, reason, created_at
                FROM experience_recipe_registry_event
                WHERE idempotency_key = ?
                """, EVENT_ROW_MAPPER, idempotencyKey);
        return rows.stream().findFirst();
    }

    @Override
    public void save(ExperienceRecipeRegistryEntry entry) {
        Long expectedRecordVersion = entry.getRecordVersion();
        if (expectedRecordVersion == null) {
            Optional<ExperienceRecipeRegistryEntry> current = findByRegistryKey(entry.getRegistryKey());
            if (current.isPresent()) {
                expectedRecordVersion = current.get().getRecordVersion();
                if (entry.getCreatedAt() == null) {
                    entry.setCreatedAt(current.get().getCreatedAt());
                }
            }
        }
        if (!saveWithVersionCheck(entry, expectedRecordVersion)) {
            throw new IllegalStateException(
                    "Experience recipe record version changed: " + entry.getRegistryKey());
        }
    }

    @Override
    public boolean saveWithVersionCheck(
            ExperienceRecipeRegistryEntry entry,
            Long expectedRecordVersion) {
        Instant now = Instant.now();
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(now);
        }
        entry.setUpdatedAt(now);
        if (expectedRecordVersion != null && expectedRecordVersion > 0) {
            long nextRecordVersion = expectedRecordVersion + 1;
            entry.setRecordVersion(nextRecordVersion);
            int updated = jdbcTemplate.update("""
                    UPDATE experience_recipe_registry
                    SET recipe_id = ?, recipe_version = ?, canonical_recipe_id = ?, title = ?, business_type = ?,
                        route = ?, namespace_scope = ?, tenant_scope = ?, permission_tags = ?, status = ?,
                        active_for_discovery = ?, owner_role = ?, record_version = ?, updated_at = ?
                    WHERE registry_key = ? AND record_version = ?
                    """,
                    entry.getRecipeId(), entry.getRecipeVersion(), entry.getCanonicalRecipeId(), entry.getTitle(),
                    entry.getBusinessType(), entry.getRoute(), entry.getNamespaceScope(), entry.getTenantScope(),
                    entry.getPermissionTags(), entry.getStatus().wireValue(), bool(entry.isActiveForDiscovery()),
                    entry.getOwnerRole(), nextRecordVersion,
                    Timestamp.from(entry.getUpdatedAt()), entry.getRegistryKey(), expectedRecordVersion);
            if (updated == 1) {
                return true;
            }
            entry.setRecordVersion(expectedRecordVersion);
            return false;
        }
        entry.setRecordVersion(1L);
        jdbcTemplate.update("""
                INSERT INTO experience_recipe_registry(
                    registry_key, recipe_id, recipe_version, canonical_recipe_id, title, business_type, route,
                    namespace_scope, tenant_scope, permission_tags, status, active_for_discovery, owner_role,
                    record_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.getRegistryKey(), entry.getRecipeId(), entry.getRecipeVersion(), entry.getCanonicalRecipeId(),
                entry.getTitle(), entry.getBusinessType(), entry.getRoute(), entry.getNamespaceScope(),
                entry.getTenantScope(), entry.getPermissionTags(), entry.getStatus().wireValue(),
                bool(entry.isActiveForDiscovery()), entry.getOwnerRole(), entry.getRecordVersion(),
                Timestamp.from(entry.getCreatedAt()), Timestamp.from(entry.getUpdatedAt()));
        return true;
    }

    @Override
    public void appendEvent(ExperienceRecipeRegistryEvent event) {
        jdbcTemplate.update("""
                INSERT INTO experience_recipe_registry_event(
                    event_id, registry_key, idempotency_key, operation, actor_role, api_result, failure_stage,
                    from_status, to_status, from_active_for_discovery, to_active_for_discovery,
                    response_status, response_active_for_discovery, response_discoverable, reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.getEventId(), event.getRegistryKey(), event.getIdempotencyKey(),
                event.getOperation().wireValue(), event.getActorRole(), event.getApiResult().name(),
                event.getFailureStage().wireValue(), event.getFromStatus().wireValue(), event.getToStatus().wireValue(),
                bool(event.isFromActiveForDiscovery()), bool(event.isToActiveForDiscovery()),
                event.getResponseStatus().wireValue(), bool(event.isResponseActiveForDiscovery()),
                bool(event.isResponseDiscoverable()), event.getReason(), Timestamp.from(event.getCreatedAt()));
    }

    @Override
    public List<ExperienceRecipeRegistryEntry> findDiscoverable() {
        return jdbcTemplate.query("""
                SELECT registry_key, recipe_id, recipe_version, canonical_recipe_id, title, business_type, route,
                       namespace_scope, tenant_scope, permission_tags, status, active_for_discovery, owner_role,
                       record_version, created_at, updated_at
                FROM experience_recipe_registry
                WHERE status = ? AND active_for_discovery = 1
                """, ENTRY_ROW_MAPPER, ExperienceRecipeStatus.VALIDATED.wireValue()).stream()
                .sorted(Comparator.comparing(ExperienceRecipeRegistryEntry::getRegistryKey))
                .toList();
    }

    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
