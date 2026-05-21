package com.foggyframework.dataset.db.model.semantic.service;

import com.foggyframework.dataset.db.model.def.dict.DbDictionaryDiscoveryDef;
import com.foggyframework.dataset.db.model.semantic.domain.DictionaryDiscoveryResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

public interface DictionaryDiscoveryService {

    DictionaryDiscoveryResult discover(String modelName, String fieldName, DbDictionaryDiscoveryDef discovery,
                                       SemanticRequestContext context);
}
