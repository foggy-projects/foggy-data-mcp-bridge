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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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

        String rootLocation = bundle.getRootPath().trim();
        boolean withinRoot;
        try {
            withinRoot = isWithinBundleRoot(rootLocation, bundleResource.getResource(), resolvedResource);
        } catch (IOException | IllegalArgumentException e) {
            throw RX.throwB(e);
        }
        if (!withinRoot) {
            throw RX.throwB(String.format("FSScript相对导入[%s]不能越过Bundle根目录[%s]", location, bundle.getRootPath()));
        }
    }

    private static boolean isWithinBundleRoot(String rootLocation, Resource source, Resource resolved)
            throws IOException {
        String lowerRoot = rootLocation.toLowerCase(Locale.ROOT);
        if (lowerRoot.startsWith("classpath*:") || lowerRoot.startsWith("classpath:")) {
            return isWithinClasspathRoot(rootLocation, source, resolved);
        }
        if (lowerRoot.startsWith("jar:")) {
            return isWithinJarRoot(rootLocation, source, resolved);
        }
        if (lowerRoot.startsWith("http:") || lowerRoot.startsWith("https:")) {
            return isWithinUriRoot(rootLocation, source, resolved);
        }
        if (!lowerRoot.startsWith("file:") && hasUnsupportedScheme(rootLocation)) {
            return false;
        }

        Path rootPath = toRootPath(rootLocation);
        return rootPath != null && source.isFile() && resolved.isFile()
                && toComparablePath(source.getFile().toPath()).startsWith(rootPath)
                && toComparablePath(resolved.getFile().toPath()).startsWith(rootPath);
    }

    private static boolean isWithinClasspathRoot(String rootLocation, Resource source, Resource resolved)
            throws IOException {
        List<String> logicalRoot = normalizeSegments(
                rootLocation.substring(rootLocation.indexOf(':') + 1).replace('\\', '/'));
        if (logicalRoot == null || logicalRoot.isEmpty()) {
            return false;
        }

        if (source.isFile() && resolved.isFile()) {
            Path sourcePath = source.getFile().toPath().toAbsolutePath().normalize();
            Path physicalRoot = classpathFileRoot(sourcePath, logicalRoot);
            if (physicalRoot == null) {
                return false;
            }
            Path comparableRoot = toComparablePath(physicalRoot);
            return toComparablePath(sourcePath).startsWith(comparableRoot)
                    && toComparablePath(resolved.getFile().toPath()).startsWith(comparableRoot);
        }

        JarLocation sourceJar = JarLocation.fromResource(source);
        JarLocation resolvedJar = JarLocation.fromResource(resolved);
        if (sourceJar == null || resolvedJar == null
                || !sourceJar.container().equals(resolvedJar.container())) {
            return false;
        }
        List<String> rootEntry = prefixThroughLastMatch(sourceJar.entrySegments(), logicalRoot);
        return rootEntry != null && startsWithSegments(resolvedJar.entrySegments(), rootEntry);
    }

    private static Path classpathFileRoot(Path sourcePath, List<String> logicalRoot) {
        List<String> sourceSegments = new ArrayList<>();
        for (Path segment : sourcePath) {
            sourceSegments.add(segment.toString());
        }
        List<String> rootSegments = prefixThroughLastMatch(sourceSegments, logicalRoot);
        if (rootSegments == null) {
            return null;
        }
        Path root = sourcePath.getRoot();
        for (String segment : rootSegments) {
            root = root.resolve(segment);
        }
        return root;
    }

    private static boolean isWithinJarRoot(String rootLocation, Resource source, Resource resolved)
            throws IOException {
        JarLocation rootJar = JarLocation.fromExternalForm(rootLocation);
        JarLocation sourceJar = JarLocation.fromResource(source);
        JarLocation resolvedJar = JarLocation.fromResource(resolved);
        return rootJar != null && sourceJar != null && resolvedJar != null
                && rootJar.container().equals(sourceJar.container())
                && rootJar.container().equals(resolvedJar.container())
                && startsWithSegments(sourceJar.entrySegments(), rootJar.entrySegments())
                && startsWithSegments(resolvedJar.entrySegments(), rootJar.entrySegments());
    }

    private static boolean isWithinUriRoot(String rootLocation, Resource source, Resource resolved)
            throws IOException {
        URI rootUri = URI.create(rootLocation);
        URI sourceUri = source.getURI();
        URI resolvedUri = resolved.getURI();
        List<String> rootPath = normalizeSegments(rootUri.getPath());
        List<String> sourcePath = normalizeSegments(sourceUri.getPath());
        List<String> resolvedPath = normalizeSegments(resolvedUri.getPath());
        return rootPath != null && sourcePath != null && resolvedPath != null
                && sameOrigin(rootUri, sourceUri) && sameOrigin(rootUri, resolvedUri)
                && startsWithSegments(sourcePath, rootPath)
                && startsWithSegments(resolvedPath, rootPath);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme() != null && right.getScheme() != null
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && Objects.equals(left.getRawAuthority(), right.getRawAuthority());
    }

    private static boolean hasUnsupportedScheme(String location) {
        int colon = location.indexOf(':');
        return colon > 1 && location.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*");
    }

    private static List<String> prefixThroughLastMatch(List<String> path, List<String> marker) {
        int lastEnd = -1;
        for (int index = 0; index + marker.size() < path.size(); index++) {
            if (path.subList(index, index + marker.size()).equals(marker)) {
                lastEnd = index + marker.size();
            }
        }
        return lastEnd < 0 ? null : path.subList(0, lastEnd);
    }

    private static boolean startsWithSegments(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private static List<String> normalizeSegments(String path) {
        if (path == null || path.indexOf('\\') >= 0) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        return List.copyOf(segments);
    }

    private record JarLocation(String container, List<String> entrySegments) {
        private static JarLocation fromResource(Resource resource) throws IOException {
            return fromExternalForm(resource.getURL().toExternalForm());
        }

        private static JarLocation fromExternalForm(String location) throws IOException {
            if (!location.toLowerCase(Locale.ROOT).startsWith("jar:")) {
                return null;
            }
            int separator = location.lastIndexOf("!/");
            if (separator < 0) {
                return null;
            }
            String rawEntry = location.substring(separator + 2);
            if (rawEntry.indexOf('?') >= 0 || rawEntry.indexOf('#') >= 0) {
                return null;
            }
            List<String> entry = normalizeSegments(URI.create("/" + rawEntry).getPath());
            String container = canonicalJarContainer(location.substring(0, separator));
            return entry == null ? null : new JarLocation(container, entry);
        }

        private static String canonicalJarContainer(String container) throws IOException {
            if (!container.toLowerCase(Locale.ROOT).startsWith("jar:file:")) {
                return container;
            }
            int nestedEntry = container.indexOf("!/");
            String outerJar = nestedEntry < 0 ? container : container.substring(0, nestedEntry);
            String nestedSuffix = nestedEntry < 0 ? "" : container.substring(nestedEntry);
            Path outerPath = Paths.get(URI.create(outerJar.substring("jar:".length())));
            return "jar:" + toComparablePath(outerPath).toUri() + nestedSuffix;
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
