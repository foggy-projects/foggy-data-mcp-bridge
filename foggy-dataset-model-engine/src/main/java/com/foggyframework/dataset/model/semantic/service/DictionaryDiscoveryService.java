package com.foggyframework.dataset.model.semantic.service;

import com.foggyframework.dataset.model.def.dict.DbDictionaryDiscoveryDef;
import com.foggyframework.dataset.model.semantic.domain.DictionaryDiscoveryResult;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

public interface DictionaryDiscoveryService {

    DictionaryDiscoveryResult discover(String modelName, String fieldName, DbDictionaryDiscoveryDef discovery,
                                       SemanticRequestContext context);
}
