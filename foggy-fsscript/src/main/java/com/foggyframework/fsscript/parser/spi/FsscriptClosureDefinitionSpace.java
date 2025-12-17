/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.bundle.Bundle;
import com.foggyframework.core.utils.resource.DefaultResourceFinder;
import com.foggyframework.core.utils.resource.ResourceFinder;
import org.springframework.core.io.Resource;

/**
 * 从2013-05-13的版本开始,获取BeanDefinition必须传入VisibleScope的信息
 * <p>
 * 呃,FoggyClass的集合,由于FoggyClass是定义在XML文件中的,同一个XML文件定义的FoggyClass由它来维护
 * <p>
 * FoggyBundle-->BeanDefinitionSpace-->FoggyClosureDefinition-->(ElementItems)
 *
 * @author Foggy
 * @since foggy-1.0
 */
public interface FsscriptClosureDefinitionSpace extends Destroyable {

    /**
     * 加载
     *
     * @param ee
     * @param file
     * @return
     */
    Fsscript loadFsscript(ExpEvaluator ee, String file);

    String getPath();

    String getName();

    Resource getResource(ExpEvaluator ee, String location);

    Resource getResource();

     FsscriptClosureDefinition newFsscriptClosureDefinition();

     Bundle getBundle();

//   default ExpFactory getExpFactory(){
//       return null;
//   }
}
