package com.foggyframework.semantic.common;

import com.foggyframework.semantic.common.annotates.SemanticUse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.*;

@Slf4j
public class BundleSemanticUseDefLoaderImpl implements ImportBeanDefinitionRegistrar {

    private static Map<String, List<String>> className2ScopeConfig = new HashMap<>();

//    private List<List<String>> className2ScopeConfig = new ArrayList<>();

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        register(metadata, registry);
    }

    public void register(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        try {
            String[] useScopes = (String[]) metadata.getAnnotationAttributes(SemanticUse.class.getName()).get("useScopes");
            if (useScopes != null) {
                className2ScopeConfig.put(metadata.getClassName(), Arrays.asList(useScopes));
            }

        } catch (Throwable t) {
            log.error("加载语义注释 Semantic出现异常" + t.getMessage());
            t.printStackTrace();
        }

    }

    public static List<String> getScopeByBundlePackage(String packageName){
        List<String> ll = new ArrayList<>();

        for (Map.Entry<String, List<String>> stringListEntry : className2ScopeConfig.entrySet()) {
            if(stringListEntry.getKey().startsWith(packageName)){
                ll.addAll(stringListEntry.getValue());
            }
        }
        return ll;
    }

}
