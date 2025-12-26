package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.impl.query.DbQueryAccessImpl;


public interface DbQueryProperty extends DbObject {
    DbProperty getProperty();

    DbQueryAccessImpl getQueryAccess();


}
