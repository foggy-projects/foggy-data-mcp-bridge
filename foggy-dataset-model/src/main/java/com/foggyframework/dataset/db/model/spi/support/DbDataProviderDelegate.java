package com.foggyframework.dataset.db.model.spi.support;

import com.foggyframework.dataset.db.model.spi.DbDataProvider;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

@NoArgsConstructor
@AllArgsConstructor
public abstract class DbDataProviderDelegate implements DbDataProvider {
    @Delegate
    DbDataProvider delegate;
}
