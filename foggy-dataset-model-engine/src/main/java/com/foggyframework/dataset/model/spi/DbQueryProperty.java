package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.impl.query.DbQueryAccessImpl;


public interface DbQueryProperty extends DbObject {
    DbProperty getProperty();

    DbQueryAccessImpl getQueryAccess();


}
