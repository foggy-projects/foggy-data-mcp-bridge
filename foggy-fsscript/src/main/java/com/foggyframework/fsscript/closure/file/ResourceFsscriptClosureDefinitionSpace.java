package com.foggyframework.fsscript.closure.file;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.resource.DefaultResourceFinder;
import com.foggyframework.core.utils.resource.ResourceFinder;
import com.foggyframework.fsscript.closure.AbstractFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.AbstractFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceFsscriptClosureDefinitionSpace extends AbstractFsscriptClosureDefinitionSpace {

    public ResourceFsscriptClosureDefinitionSpace(BundleResource bundleResource) {
        this(bundleResource, null);
    }

    public ResourceFsscriptClosureDefinitionSpace(
            BundleResource bundleResource,
            AbstractFileFsscriptLoader loader
    ) {
        this.bundleResource = bundleResource;
        this.loader = loader;
    }

    private final AbstractFileFsscriptLoader loader;

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

        AbstractFileFsscriptLoader effectiveLoader = loader != null
                ? loader
                : FileFsscriptLoader.getInstance();
        if (effectiveLoader == null) {
            throw new IllegalStateException("FSScript resource loader is not available");
        }
        return effectiveLoader.findLoadFsscript(res,ee.getExpFactory());

    }

    public static String getResourcePath(Resource resource) {
        try {
            if(resource.isFile()){
                return resource.getFile().getCanonicalPath();
            }
            return resource.getURL().toExternalForm();
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
        assertRelativeImportWithinBundleRoot(location, res);
        return res;
    }

    private void assertRelativeImportWithinBundleRoot(String location, Resource resolvedResource) {
        Bundle bundle = bundleResource.getBundle();
        if (bundle == null || StringUtils.isEmpty(bundle.getRootPath()) || !isRelativeLocation(location)) {
            return;
        }

        try {
            if (!resolvedResource.isFile()) {
                return;
            }

            Path rootPath = toRootPath(bundle.getRootPath());
            if (rootPath == null) {
                return;
            }
            Path resolvedPath = toComparablePath(resolvedResource.getFile().toPath());
            if (!resolvedPath.startsWith(rootPath)) {
                throw RX.throwB(String.format("FSScript相对导入[%s]不能越过Bundle根目录[%s]", location, bundle.getRootPath()));
            }
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    private static boolean isRelativeLocation(String location) {
        if (StringUtils.isEmpty(location)) {
            return false;
        }
        try {
            return !Paths.get(location).isAbsolute() && !location.contains(":");
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static Path toRootPath(String rootPath) throws IOException {
        try {
            if (rootPath.startsWith("file:")) {
                return toComparablePath(Paths.get(URI.create(rootPath)));
            }
            return toComparablePath(Paths.get(rootPath));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Path toComparablePath(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        if (Files.exists(absolutePath)) {
            return absolutePath.toRealPath();
        }
        return absolutePath;
    }

}
