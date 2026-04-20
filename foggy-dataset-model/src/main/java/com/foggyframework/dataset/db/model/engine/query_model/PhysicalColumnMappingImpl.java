package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnRef;

import java.util.*;

/**
 * PhysicalColumnMapping 的不可变实现
 *
 * @since 8.2.0
 */
public class PhysicalColumnMappingImpl implements PhysicalColumnMapping {

    /** QM 字段名 → 物理列列表 */
    private final Map<String, List<PhysicalColumnRef>> qmToPhysical;

    /** "table.column" → QM 字段名列表 */
    private final Map<String, List<String>> physicalToQm;

    PhysicalColumnMappingImpl(Map<String, List<PhysicalColumnRef>> qmToPhysical,
                               Map<String, List<String>> physicalToQm) {
        // 深拷贝为不可变
        Map<String, List<PhysicalColumnRef>> q2p = new LinkedHashMap<>();
        for (var entry : qmToPhysical.entrySet()) {
            q2p.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.qmToPhysical = Collections.unmodifiableMap(q2p);

        Map<String, List<String>> p2q = new LinkedHashMap<>();
        for (var entry : physicalToQm.entrySet()) {
            p2q.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.physicalToQm = Collections.unmodifiableMap(p2q);
    }

    @Override
    public List<PhysicalColumnRef> getPhysicalColumns(String qmFieldName) {
        return qmToPhysical.getOrDefault(qmFieldName, List.of());
    }

    @Override
    public List<String> getQmFieldNames(String table, String column) {
        return physicalToQm.getOrDefault(table + "." + column, List.of());
    }

    @Override
    public Set<String> toDeniedQmFields(List<DeniedPhysicalColumn> deniedPhysicalColumns) {
        if (deniedPhysicalColumns == null || deniedPhysicalColumns.isEmpty()) {
            return Set.of();
        }
        Set<String> deniedQmFields = new LinkedHashSet<>();
        for (DeniedPhysicalColumn denied : deniedPhysicalColumns) {
            if (denied.getTable() == null || denied.getColumn() == null) {
                continue;
            }
            // table.column 匹配（schema 无关，始终匹配）
            String key = denied.getTable() + "." + denied.getColumn();
            List<String> qmFields = physicalToQm.get(key);
            if (qmFields != null) {
                deniedQmFields.addAll(qmFields);
            }
        }
        return Collections.unmodifiableSet(deniedQmFields);
    }

    @Override
    public Set<String> getAllQmFieldNames() {
        return qmToPhysical.keySet();
    }

    @Override
    public Set<String> getAllPhysicalTables() {
        Set<String> tables = new LinkedHashSet<>();
        for (List<PhysicalColumnRef> refs : qmToPhysical.values()) {
            for (PhysicalColumnRef ref : refs) {
                tables.add(ref.table());
            }
        }
        return tables;
    }
}
