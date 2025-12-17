package com.foggyframework.semantic.impl;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.semantic.common.Semantic;
import com.foggyframework.semantic.common.SemanticModule;
import com.foggyframework.semantic.common.TermItem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class SemanticImpl implements Semantic {

    String scope;

    Map<String, TermItem> key2SemanticItem = new HashMap<>();

    List<SemanticModule> modules = new ArrayList<>();

    public void addSemanticModule(SemanticModule semanticModule) {
        modules.add(semanticModule);

        List<TermItem> terms = semanticModule.getTerms();

        for (TermItem term : terms) {
            TermItem ti = key2SemanticItem.get(term.getName());
            if (ti != null) {
                log.warn(String.format("存在重复的语义定义,scope:%s，【%s】将被【%s】替换 ,", scope,ti,term));
            }
            key2SemanticItem.put(term.getName(),term);

            key2SemanticItem.put(StringUtils.to_sm_string(term.getName()),term);
            key2SemanticItem.put(StringUtils.to(term.getName()),term);
        }


    }

    @Nullable
    @Override
    public TermItem getTermItemByName(String name){
        return key2SemanticItem.get(name);
    }


}
