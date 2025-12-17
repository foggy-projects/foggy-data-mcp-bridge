package com.foggyframework.dataset.jdbc.model.spi.support;

import com.foggyframework.dataset.jdbc.model.spi.JdbcDataProvider;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

@NoArgsConstructor
@AllArgsConstructor
public abstract class JdbcDataProviderDelegate implements JdbcDataProvider {
    @Delegate
    JdbcDataProvider delegate;
}
