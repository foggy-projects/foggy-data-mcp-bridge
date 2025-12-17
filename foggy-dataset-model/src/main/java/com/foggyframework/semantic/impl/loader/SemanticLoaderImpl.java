package com.foggyframework.semantic.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.event.SystemBundlesContextRefreshedEvent;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.semantic.common.Semantic;
import com.foggyframework.semantic.common.SemanticModule;
import com.foggyframework.semantic.impl.SemanticImpl;
import org.springframework.context.ApplicationListener;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * 负责
 */
public class SemanticLoaderImpl implements ApplicationListener<SystemBundlesContextRefreshedEvent> {

    Map<String, SemanticImpl> scope2Semantic;

    @Resource
    FileFsscriptLoader fileFsscriptLoader;

    SystemBundlesContext systemBundlesContext;


    public Semantic getSemanticByScope(String scope) {
        return scope2Semantic.get(scope);
    }

    public Semantic getPulbicSemantic() {
        return scope2Semantic.get("public");
    }

    @Override
    public void onApplicationEvent(SystemBundlesContextRefreshedEvent event) {

        systemBundlesContext = event.getSystemBundlesContext();
        //加载所有的语义定义文件
        List<BundleResource> bundleResources = new ArrayList<>();
        for (Bundle bundle : systemBundlesContext.getBundleList()) {

            BundleResource[] dataSyncList = bundle.findBundleResources("**/*.semantic");

            bundleResources.addAll(Arrays.asList(dataSyncList));
        }
        Map<String, SemanticImpl> tmp = new HashMap<>();
        for (BundleResource bundleResource : bundleResources) {
            loadSemantic(tmp, bundleResource);
        }

        scope2Semantic = tmp;

    }

    private void loadSemantic(Map<String, SemanticImpl> tmp, BundleResource bundleResource) {
        Fsscript fScript = fileFsscriptLoader.findLoadFsscript(bundleResource);
        ExpEvaluator ee = fScript.eval(systemBundlesContext.getApplicationContext());

        Object semanticDef = ee.getExportObject("semantic");
        SemanticModule semanticModule = FsscriptConversionService.getSharedInstance().convert(semanticDef, SemanticModule.class);

        String scope = semanticModule.getScope();
        if (StringUtils.isEmpty(scope)) {
            scope = bundleResource.getBundle().getName();
        }
        SemanticImpl semantic = getOrCreateSemantic(tmp, scope);
        semantic.addSemanticModule(semanticModule);

        if (semanticModule.isCommon()) {
            SemanticImpl publicScope = getOrCreateSemantic(tmp, "public");
            publicScope.addSemanticModule(semanticModule);
        }

        SemanticImpl packageSemantic = getOrCreateSemantic(tmp, bundleResource.getBundle().getPackageName());
        packageSemantic.addSemanticModule(semanticModule);
    }

//    Map<String, SemanticImpl> packageName2Semantic=new HashMap<>();

    private SemanticImpl getOrCreateSemantic(Map<String, SemanticImpl> tmp, String scope) {
        SemanticImpl semantic = tmp.get(scope);

        if (semantic == null) {
            semantic = new SemanticImpl();
            semantic.setScope(scope);
            tmp.put(scope, semantic);
        }
        return semantic;
    }


}
