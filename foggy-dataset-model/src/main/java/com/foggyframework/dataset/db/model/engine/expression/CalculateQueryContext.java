package com.foggyframework.dataset.db.model.engine.expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Query-shape context required to lower CALCULATE expressions.
 */
public final class CalculateQueryContext {

    private final List<String> groupByFields;
    private final List<String> selectedFields;
    private final Set<String> systemSliceFields;
    private final boolean supportsGroupedAggregateWindow;
    private final boolean timeWindowPostCalculatedFields;

    public CalculateQueryContext(
            List<String> groupByFields,
            Set<String> systemSliceFields,
            boolean supportsGroupedAggregateWindow,
            boolean timeWindowPostCalculatedFields) {
        this(groupByFields, Collections.emptyList(), systemSliceFields,
                supportsGroupedAggregateWindow, timeWindowPostCalculatedFields);
    }

    public CalculateQueryContext(
            List<String> groupByFields,
            List<String> selectedFields,
            Set<String> systemSliceFields,
            boolean supportsGroupedAggregateWindow,
            boolean timeWindowPostCalculatedFields) {
        this.groupByFields = groupByFields == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(groupByFields));
        this.selectedFields = selectedFields == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(selectedFields));
        this.systemSliceFields = systemSliceFields == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(systemSliceFields));
        this.supportsGroupedAggregateWindow = supportsGroupedAggregateWindow;
        this.timeWindowPostCalculatedFields = timeWindowPostCalculatedFields;
    }

    public List<String> getGroupByFields() {
        return groupByFields;
    }

    public List<String> getSelectedFields() {
        return selectedFields;
    }

    public Set<String> getSystemSliceFields() {
        return systemSliceFields;
    }

    public boolean isSupportsGroupedAggregateWindow() {
        return supportsGroupedAggregateWindow;
    }

    public boolean isTimeWindowPostCalculatedFields() {
        return timeWindowPostCalculatedFields;
    }
}
