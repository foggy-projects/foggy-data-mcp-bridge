package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives a local model fingerprint from runtime bundle resources.
 */
public class RuntimeBundlePivotOuterCacheModelIdentityProvider implements PivotOuterCacheModelIdentityProvider {

    private final SystemBundlesContext systemBundlesContext;

    public RuntimeBundlePivotOuterCacheModelIdentityProvider(SystemBundlesContext systemBundlesContext) {
        this.systemBundlesContext = systemBundlesContext;
    }

    @Override
    public PivotOuterCacheModelIdentity resolve(String namespace, String model, QueryModel queryModel) {
        if (systemBundlesContext == null || model == null || model.isBlank()) {
            return PivotOuterCacheModelIdentity.empty();
        }
        List<String> parts = new ArrayList<>();
        parts.add("namespace=" + normalize(namespace));
        parts.add("model=" + normalize(model));
        addResourceFingerprint(parts, normalize(namespace), normalize(model) + ".qm");
        for (String tableModel : tableModelNames(queryModel)) {
            addResourceFingerprint(parts, normalize(namespace), tableModel + ".tm");
        }
        return new PivotOuterCacheModelIdentity("runtime-bundle:" + sha256(String.join("|", parts)), "");
    }

    private void addResourceFingerprint(List<String> parts, String namespace, String resourceName) {
        BundleResource bundleResource;
        try {
            bundleResource = systemBundlesContext.findResourceByName(resourceName, namespace, false);
        } catch (Exception e) {
            parts.add(resourceName + "=unavailable:" + e.getClass().getSimpleName());
            return;
        }
        if (bundleResource == null) {
            parts.add(resourceName + "=missing");
            return;
        }
        Resource resource = bundleResource.getResource();
        String bundleName = bundleResource.getBundle() != null ? bundleResource.getBundle().getName() : "";
        String rootPath = bundleResource.getBundle() != null ? bundleResource.getBundle().getRootPath() : "";
        parts.add(resourceName + ".bundle=" + bundleName);
        parts.add(resourceName + ".root=" + rootPath);
        parts.add(resourceName + ".desc=" + (resource != null ? resource.getDescription() : ""));
        parts.add(resourceName + ".content=" + resourceContentHash(bundleResource));
    }

    private String resourceContentHash(BundleResource bundleResource) {
        try (InputStream inputStream = bundleResource.getInputStream()) {
            if (inputStream == null) {
                return "no-input-stream";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "unreadable:" + e.getClass().getSimpleName();
        }
    }

    private Set<String> tableModelNames(QueryModel queryModel) {
        Set<String> names = new LinkedHashSet<>();
        if (queryModel == null) {
            return names;
        }
        addTableModelName(names, queryModel.getJdbcModel());
        if (queryModel.getJdbcModelList() != null) {
            queryModel.getJdbcModelList().stream()
                    .sorted(Comparator.comparing(table -> normalize(table != null ? table.getName() : null)))
                    .forEach(table -> addTableModelName(names, table));
        }
        return names;
    }

    private void addTableModelName(Set<String> names, TableModel tableModel) {
        if (tableModel != null && tableModel.getName() != null && !tableModel.getName().isBlank()) {
            names.add(tableModel.getName().trim());
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
