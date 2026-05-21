package com.foggyframework.dataset.db.model.semantic.service.impl;

import com.foggyframework.dataset.db.model.def.dict.DbDictionaryDiscoveryDef;
import com.foggyframework.dataset.db.model.semantic.domain.DictionaryDiscoveryResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.DictionaryDiscoveryService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DictionaryDiscoveryServiceImpl implements DictionaryDiscoveryService {

    private static final String COUNT_ALIAS = "__foggyDictionaryCount";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public DictionaryDiscoveryResult discover(String modelName, String fieldName, DbDictionaryDiscoveryDef discovery,
                                             SemanticRequestContext context) {
        if (discovery == null || !discovery.isEnabled() || !discovery.isLlmVisible()) {
            return DictionaryDiscoveryResult.sampled(List.of(), false, Instant.now());
        }
        SemanticRequestContext safeContext = context != null ? context : SemanticRequestContext.empty();
        long now = System.currentTimeMillis();
        long ttlMillis = discovery.getEffectiveRefreshTtlSeconds() * 1000L;
        String cacheKey = buildCacheKey(modelName, fieldName, discovery, safeContext);
        if (ttlMillis > 0) {
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && cached.expiresAtMillis > now) {
                return cached.result;
            }
        }

        DictionaryDiscoveryResult result;
        try {
            result = executeDiscovery(modelName, fieldName, discovery, safeContext);
        } catch (Exception e) {
            log.warn("Dictionary discovery failed for {}.{}: {}", modelName, fieldName, e.getMessage());
            result = DictionaryDiscoveryResult.failed(e.getMessage());
        }

        if (ttlMillis > 0 && DictionaryDiscoveryResult.STATUS_SAMPLED.equals(result.getStatus())) {
            cache.put(cacheKey, new CacheEntry(result, now + ttlMillis));
        }
        return result;
    }

    private DictionaryDiscoveryResult executeDiscovery(String modelName, String fieldName,
                                                       DbDictionaryDiscoveryDef discovery,
                                                       SemanticRequestContext context) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        int maxValues = discovery.getEffectiveMaxValues();
        request.setLimit(maxValues + 1);
        request.setReturnTotal(false);

        if (DbDictionaryDiscoveryDef.STRATEGY_DISTINCT.equals(discovery.getEffectiveStrategy())) {
            request.setDistinct(true);
            request.setColumns(List.of(fieldName));
        } else {
            request.setColumns(List.of(fieldName, "COUNT(" + fieldName + ") AS " + COUNT_ALIAS));
            request.setGroupBy(List.of(new SemanticQueryRequest.GroupByItem(fieldName, null)));
            request.setOrderBy(List.of(orderByCountDesc()));
        }

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(modelName, request, "execute", context);
        List<Map<String, Object>> rows = response.getItems() != null ? response.getItems() : List.of();
        boolean truncated = rows.size() > maxValues;

        List<DictionaryDiscoveryResult.ValueEntry> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (values.size() >= maxValues) {
                break;
            }
            Object value = readValue(row, fieldName);
            Long count = DbDictionaryDiscoveryDef.STRATEGY_DISTINCT.equals(discovery.getEffectiveStrategy())
                    ? null
                    : readLong(row, COUNT_ALIAS);
            values.add(new DictionaryDiscoveryResult.ValueEntry(value, count));
        }
        values.sort(Comparator
                .comparing(DictionaryDiscoveryResult.ValueEntry::getCount,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(entry -> String.valueOf(entry.getValue()), Comparator.nullsLast(String::compareTo)));
        return DictionaryDiscoveryResult.sampled(values, truncated, Instant.now());
    }

    private Object readValue(Map<String, Object> row, String fieldName) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(fieldName)) {
            return row.get(fieldName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(fieldName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long readLong(Map<String, Object> row, String fieldName) {
        Object value = readValue(row, fieldName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private SemanticQueryRequest.OrderItem orderByCountDesc() {
        SemanticQueryRequest.OrderItem order = new SemanticQueryRequest.OrderItem();
        order.setField(COUNT_ALIAS);
        order.setDir("desc");
        return order;
    }

    private String buildCacheKey(String modelName, String fieldName, DbDictionaryDiscoveryDef discovery,
                                 SemanticRequestContext context) {
        return String.join("|",
                Objects.toString(context.getNamespace(), ""),
                modelName,
                fieldName,
                discovery.getEffectiveStrategy(),
                String.valueOf(discovery.getEffectiveMaxValues()),
                normalizeSet(context.getFieldAccess()),
                Objects.toString(context.getDeniedColumns(), ""),
                Objects.toString(context.getSystemSlice(), ""),
                Objects.toString(context.getSecurityContext(), ""));
    }

    private String normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().sorted().toList().toString();
    }

    private static class CacheEntry {
        private final DictionaryDiscoveryResult result;
        private final long expiresAtMillis;

        private CacheEntry(DictionaryDiscoveryResult result, long expiresAtMillis) {
            this.result = result;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
