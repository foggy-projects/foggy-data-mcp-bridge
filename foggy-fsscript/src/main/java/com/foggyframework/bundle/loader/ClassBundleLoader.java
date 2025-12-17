/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.bundle.loader;


import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.bundle.BundleImpl;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.utils.ClazzUtils;
import com.foggyframework.core.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.List;

/**
 * @author Foggy
 */
@Slf4j
public class ClassBundleLoader extends BundleLoader<BundleDefinition> {

    public ClassBundleLoader(List<BundleDefinition> bundleDefList) {
        super(bundleDefList);
    }

    @Override
    public void load(SystemBundlesContext systemBundlesContext) {
        if (bundleDefList == null) {
            log.warn("系统中没有任何模块定义！!");
            return;
        }
        if (log.isDebugEnabled()) {
//            log.debug("开始加载系统中预定义的模块");
            for (BundleDefinition d : bundleDefList) {
                log.debug("" + d);
            }
        }

        //检查classBundleDefinitionList是否冲突
//        check(classBundleDefinitionList);
        for (BundleDefinition classBundleDefinition : bundleDefList) {
            BundleImpl bundle = new BundleImpl(systemBundlesContext);
            bundle.setName(classBundleDefinition.getName());
            bundle.setBundleDefinition(classBundleDefinition);
            loadBeanDef(bundle, classBundleDefinition);

            systemBundlesContext.regBundle(bundle);
        }
    }

    /**
     * 加载模块
     *
     * @param bundle
     * @param classBundleDefinition
     */
    void loadBeanDef(BundleImpl bundle, BundleDefinition classBundleDefinition) {
        //判断这个模块，是在jar中，还是开发项目

        String templatesLocation;
        String clazzFileName = ClazzUtils.getClazzName(classBundleDefinition.getDefinitionClass()) + ".class";
        URL res = classBundleDefinition.getDefinitionClass().getResource(clazzFileName);
        if (StringUtils.equals("jar", res.getProtocol())) {
            bundle.setMode(BundleImpl.MODE_JAR);
        } else {
            bundle.setMode(BundleImpl.MODE_CLASSPATH);
        }
        // g.e com.foggyframework.core.bundle.test1.FoggyBundleTest1Configuration.$1
        String bundleClassName = classBundleDefinition.getDefinitionClass().getName();
        // g.e com/foggyframework/core/bundle/test1/FoggyBundleTest1Configuration/$1
        String classPath = bundleClassName.replaceAll("\\.", "/");

        // g.e
        String templatePath = res.toString();
        templatePath = templatePath.substring(0, templatePath.length() - (classPath + ".class").length());
        bundle.setRootPath(templatePath);
        templatesLocation = templatePath + "foggy/templates";

        switch (bundle.getMode()) {
            case BundleImpl.MODE_JAR:
//                String basePath = "/WEB-INF/" + classBundleDefinition.getPackageName();
//                bundle.setBasePath(basePath);
//                log.warn(String.format("模块【%s】处于jar包模式中,准备将其解压到", basePath) );

//                if(tryUnzip(bundle, templatesLocation)){
//                    break;
//                }else{
                //...无法解压，可能是ServletContext resource [/] cannot be resolved to absolute file path - web application archive not expanded? 导至
//                    log.error("无法解压，可能是ServletContext resource [/] cannot be resolved to absolute file path - web application archive not expanded? 导至");

                //  由于解压可能解压在临时文件夹，有被删除的风险，因此不再将资源解压！直接使用jar包中的路径为模块根目录，请注意使用Resource不再使用File获取资源
                log.debug("使用" + templatesLocation + "为模块的根目录");
                bundle.setBasePath(templatesLocation);
                break;
//            case BundleImpl.MODE_JAR:
//                String basePath = "/WEB-INF/" + classBundleDefinition.getPackageName();
//                bundle.setBasePath(basePath);
//                log.warn(String.format("模块【%s】处于jar包模式中,准备将其解压到", basePath) );
//
//                if(tryUnzip(bundle, templatesLocation)){
//                    break;
//                }else {
////                ...无法解压，可能是ServletContext resource[/]cannot be resolved to absolute file path - web application archive
////                    not expanded?导至
//                    log.error("无法解压，可能是ServletContext resource [/] cannot be resolved to absolute file path - web application archive not expanded? 导至");
//                }
//                //  由于解压可能解压在临时文件夹，有被删除的风险，因此不再将资源解压！直接使用jar包中的路径为模块根目录，请注意使用Resource不再使用File获取资源
//                log.debug("使用" + templatesLocation + "为模块的根目录");
//                bundle.setBasePath(templatesLocation);
//                break;
            case BundleImpl.MODE_CLASSPATH:

                bundle.setBasePath(templatesLocation);
                log.warn(String.format("模块【%s】处于开发者目录，当前templatesLocation: ", classBundleDefinition.getName()) + "," + templatesLocation);


                break;
            default:
                throw new UnsupportedOperationException();
        }

    }


}
