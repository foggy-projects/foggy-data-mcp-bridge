/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.bundle;


/**
 * @author Foggy
 */
public abstract class AbstractClassBundleDefinition implements BundleDefinition {

    @Override
    public String getPackageName() {
        String nn = this.getClass().getPackage().getName();
        return nn;
    }

    @Override
    public String toString() {
        return "ClassBundleDefinition: packageName: "+getPackageName()+", name: "+getName();
    }

    @Override
    public Class<?> getDefinitionClass() {
        return getClass();
    }
}
