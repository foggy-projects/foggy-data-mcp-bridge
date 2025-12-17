package com.foggyframework.dataset.jdbc.model.common.result;

import lombok.Data;

import java.util.List;

@Data
public class KpiResultImpl {

    int total;

    List<KpiItem> kpiItems;
}
