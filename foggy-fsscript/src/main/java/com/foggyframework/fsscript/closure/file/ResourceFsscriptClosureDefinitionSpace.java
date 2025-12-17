package com.foggyframework.fsscript.closure.file;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.resource.DefaultResourceFinder;
import com.foggyframework.core.utils.resource.ResourceFinder;
import com.foggyframework.fsscript.closure.AbstractFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;

public class ResourceFsscriptClosureDefinitionSpace extends AbstractFsscriptClosureDefinitionSpace {

    public ResourceFsscriptClosureDefinitionSpace(BundleResource bundleResource) {
        this.bundleResource = bundleResource;
    }

    public Resource getResource() {
        return bundleResource.getResource();
    }

    @Override
    public Bundle getBundle() {

//        SystemBundlesContext systemBundlesContext;
//        systemBundlesContext.getBundleByResource();
//        ee.get
        return bundleResource.getBundle();
    }

    @Override
    public String toString() {
        return "ResourceFsscriptClosureDefinitionSpace{" +
                "bundleResource=" + bundleResource +
                '}';
    }

    BundleResource bundleResource;

    @Override
    public Fsscript loadFsscript(ExpEvaluator ee, String path) {
        Resource res = getResource(ee, path);

        return FileFsscriptLoader.getInstance().findLoadFsscript(res,ee.getExpFactory());

    }

    public static String getResourcePath(Resource resource) {
        try {
//            String path = resource.getURI().getPath();
//            if(StringUtils.isEmpty(path)){
//            resource.getURI().getPath();
            if(resource instanceof FileSystemResource){
                return resource.getFile().getCanonicalPath();
            }else if(resource instanceof ClassPathResource){
                return resource.getFile().getCanonicalPath();
            }
            return resource.getURL().getPath();
//            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getResourcePath(File resource) {
        try {
            return resource.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPath() {
        return getResourcePath(bundleResource.getResource());
    }

    @Override
    public String getName() {
        return bundleResource.getResource().getFilename();
    }

    @Override
    public Resource getResource(ExpEvaluator ee, String location) {
        ResourceFinder finder = new DefaultResourceFinder(ee.getApplicationContext());

        Resource res = finder.findByResource(bundleResource.getResource(), location);
        return res;
    }


}
