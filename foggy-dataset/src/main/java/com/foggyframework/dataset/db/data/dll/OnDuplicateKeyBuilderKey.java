package com.foggyframework.dataset.db.data.dll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data@AllArgsConstructor
@NoArgsConstructor
public class OnDuplicateKeyBuilderKey {
    OnDuplicateKeyBuilder builder;
    String sql;
}
