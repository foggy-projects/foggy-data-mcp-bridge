package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Canonical runtime guard contract for signed DSL_CTE join_align stages.
 */
public final class DslCteJoinAlignRuntimeGuardContract {

    private static final String STAGE_INVALID = DslCtePlanValidator.STAGE_INVALID;
    private static final Set<String> MULTIPLICITIES = Set.of("one", "many");
    private static final Set<String> NULL_KEY_POLICIES = Set.of("exclude_unmatched", "reject_null");
    private static final Set<String> TIME_ORDERS = Set.of("source_timestamp_authoritative", "source_at_or_before_target");

    private final Cardinality cardinality;
    private final TimeAttribution timeAttribution;

    private DslCteJoinAlignRuntimeGuardContract(Cardinality cardinality, TimeAttribution timeAttribution) {
        this.cardinality = cardinality;
        this.timeAttribution = timeAttribution;
    }

    public static DslCteJoinAlignRuntimeGuardContract parseNullable(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> guard = mapValue(raw);
        if (guard == null) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align.runtimeGuard must be an object.");
        }
        Map<String, Object> cardinalityRaw = nestedMap(guard, "cardinality", "runtimeGuard.cardinality");
        Map<String, Object> timeAttributionRaw =
                nestedMap(guard, "timeAttribution", "runtimeGuard.timeAttribution");
        if (cardinalityRaw == null && timeAttributionRaw == null) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard must declare cardinality or timeAttribution guard.");
        }
        Cardinality cardinality = cardinalityRaw == null ? null : parseCardinality(cardinalityRaw);
        TimeAttribution timeAttribution =
                timeAttributionRaw == null ? null : parseTimeAttribution(timeAttributionRaw);
        return new DslCteJoinAlignRuntimeGuardContract(cardinality, timeAttribution);
    }

    public Cardinality cardinality() {
        return cardinality;
    }

    public TimeAttribution timeAttribution() {
        return timeAttribution;
    }

    public Map<String, Object> toEvidenceMap() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (cardinality != null) {
            evidence.put("cardinality", cardinality.toEvidenceMap());
        }
        if (timeAttribution != null) {
            evidence.put("timeAttribution", timeAttribution.toEvidenceMap());
        }
        return evidence;
    }

    private static Cardinality parseCardinality(Map<String, Object> raw) {
        requireEnforced(raw, "runtimeGuard.cardinality");
        requireFailClosed(raw, "runtimeGuard.cardinality");
        String leftMultiplicity = requiredString(raw, "leftMultiplicity", "runtimeGuard.cardinality");
        String rightMultiplicity = requiredString(raw, "rightMultiplicity", "runtimeGuard.cardinality");
        if (!MULTIPLICITIES.contains(leftMultiplicity) || !MULTIPLICITIES.contains(rightMultiplicity)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard.cardinality must declare one/many multiplicities.");
        }
        String nullKeyPolicy = requiredString(raw, "nullKeyPolicy", "runtimeGuard.cardinality");
        if (!NULL_KEY_POLICIES.contains(nullKeyPolicy)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard.cardinality.nullKeyPolicy must be exclude_unmatched or reject_null.");
        }
        return new Cardinality(true, "fail_closed", leftMultiplicity, rightMultiplicity, nullKeyPolicy);
    }

    private static TimeAttribution parseTimeAttribution(Map<String, Object> raw) {
        requireEnforced(raw, "runtimeGuard.timeAttribution");
        requireFailClosed(raw, "runtimeGuard.timeAttribution");
        String sourceStage = requiredString(raw, "sourceStage", "runtimeGuard.timeAttribution");
        String sourceField = requiredString(raw, "sourceField", "runtimeGuard.timeAttribution");
        String nullPolicy = requiredString(raw, "nullPolicy", "runtimeGuard.timeAttribution");
        if (!"reject_null".equals(nullPolicy)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard.timeAttribution.nullPolicy must be reject_null.");
        }

        String targetStage = stringValue(raw.get("targetStage"));
        String targetField = stringValue(raw.get("targetField"));
        if (targetStage != null || targetField != null) {
            if (targetStage == null || targetStage.isBlank()
                    || targetField == null || targetField.isBlank()) {
                throw RX.throwB(STAGE_INVALID
                        + ": signed join_align.runtimeGuard.timeAttribution target guard must declare targetStage and targetField.");
            }
        }

        String order = stringValue(raw.get("order"));
        if (order != null && !TIME_ORDERS.contains(order)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard.timeAttribution.order must be source_timestamp_authoritative or source_at_or_before_target.");
        }
        if ("source_at_or_before_target".equals(order) && (targetStage == null || targetStage.isBlank())) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard.timeAttribution.order source_at_or_before_target requires targetStage and targetField.");
        }
        return new TimeAttribution(true, "fail_closed", sourceStage, sourceField,
                nullPolicy, targetStage, targetField, order);
    }

    private static void requireEnforced(Map<String, Object> raw, String usage) {
        if (!Boolean.TRUE.equals(raw.get("enforce"))) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align." + usage + ".enforce must be true.");
        }
    }

    private static void requireFailClosed(Map<String, Object> raw, String usage) {
        if (!"fail_closed".equals(stringValue(raw.get("policy")))) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align." + usage + ".policy must be fail_closed.");
        }
    }

    private static String requiredString(Map<String, Object> raw, String key, String usage) {
        String value = stringValue(raw.get(key));
        if (value == null || value.isBlank()) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align." + usage + "." + key + " must be declared.");
        }
        return value;
    }

    private static Map<String, Object> nestedMap(Map<String, Object> raw, String key, String usage) {
        if (!raw.containsKey(key)) {
            return null;
        }
        Map<String, Object> nested = mapValue(raw.get(key));
        if (nested == null) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align." + usage + " must be an object.");
        }
        return nested;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Cardinality(boolean enforce, String policy, String leftMultiplicity,
                              String rightMultiplicity, String nullKeyPolicy) {
        private Map<String, Object> toEvidenceMap() {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("enforce", enforce);
            evidence.put("policy", policy);
            evidence.put("leftMultiplicity", leftMultiplicity);
            evidence.put("rightMultiplicity", rightMultiplicity);
            evidence.put("nullKeyPolicy", nullKeyPolicy);
            return evidence;
        }
    }

    public record TimeAttribution(boolean enforce, String policy, String sourceStage,
                                  String sourceField, String nullPolicy, String targetStage,
                                  String targetField, String order) {
        private Map<String, Object> toEvidenceMap() {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("enforce", enforce);
            evidence.put("policy", policy);
            evidence.put("sourceStage", sourceStage);
            evidence.put("sourceField", sourceField);
            evidence.put("nullPolicy", nullPolicy);
            if (targetStage != null) {
                evidence.put("targetStage", targetStage);
            }
            if (targetField != null) {
                evidence.put("targetField", targetField);
            }
            if (order != null) {
                evidence.put("order", order);
            }
            return evidence;
        }
    }
}
