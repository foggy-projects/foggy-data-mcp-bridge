package com.foggyframework.semantic.impl.facade.impl;

import com.foggyframework.semantic.common.BundleSemanticUseDefLoaderImpl;
import com.foggyframework.semantic.common.Semantic;
import com.foggyframework.semantic.common.TermItem;
import com.foggyframework.semantic.impl.facade.SemanticFacade;
import com.foggyframework.semantic.impl.loader.SemanticLoaderImpl;

import javax.annotation.Nullable;
import java.util.List;

public class SemanticFacadeImpl implements SemanticFacade {
    SemanticLoaderImpl semanticLoader;

    public SemanticFacadeImpl(SemanticLoaderImpl semanticLoader) {
        this.semanticLoader = semanticLoader;
    }

    /**
     * @param packageName 使用该语义的模块包名
     * @param name        语义的key
     * @return
     */
    @Nullable
    @Override
    public TermItem getTermItem(String packageName, String name) {

        //先从模块自己的命名空间中找
        Semantic semantic = semanticLoader.getSemanticByScope(packageName);
        if (semantic != null) {
            TermItem ti = semantic.getTermItemByName(name);
            if (ti != null) {
                return ti;
            }

        }

        //上一步没找到？找到模块bundle，都引用了哪些命名空间
        List<String> scopes = BundleSemanticUseDefLoaderImpl.getScopeByBundlePackage(packageName);
        for (String scope : scopes) {
            semantic = semanticLoader.getSemanticByScope(scope);
            if (semantic == null) {
                continue;
            }
            TermItem ti = semantic.getTermItemByName(name);
            if (ti != null) {
                return ti;
            }

        }
        //没找到？使用公共scope
        semantic = semanticLoader.getPulbicSemantic();
        if (semantic != null) {
            TermItem ti = semantic.getTermItemByName(name);
            if (ti != null) {
                return ti;
            }
        }
        return null;
    }
}
