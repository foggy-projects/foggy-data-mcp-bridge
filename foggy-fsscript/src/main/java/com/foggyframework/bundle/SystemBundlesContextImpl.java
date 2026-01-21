package com.foggyframework.bundle;

import com.foggyframework.bundle.event.SystemBundlesContextRefreshedEvent;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.bundle.loader.BundleLoader;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.ThreadUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.*;

@Data
@Slf4j
public class SystemBundlesContextImpl implements SystemBundlesContext, InitializingBean {
    List<BundleLoader<BundleDefinition>> loaders;

    List<BundleDefinition> bundleDefinitions;

    List<Bundle> bundleList = new ArrayList<>();

    Map<String, BundleDefinition> name2BundleDefinition;

    @Resource
    ApplicationContext appCtx;

    boolean loadCompleted;

    public SystemBundlesContextImpl(List<BundleLoader<BundleDefinition>> loaders) {
        this.loaders = loaders;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        //开始注册
        startReg();
    }

    /**
     * @param name
     * @return
     */
    public BundleDefinition getBundleDefinitionByName(String name) {
        return name2BundleDefinition.get(name);
    }

    @Override
    public Bundle getBundleByPackageName(String packageName) {

        return getBundleByPackageName(packageName, true);
    }

    @Override
    public Bundle getBundleByClassName(String className,boolean errorIfNotFound) {
        if (className == null) {
            return null;
        }
        int idx = className.lastIndexOf(".");
        if (idx <= 0) {
            return getBundleByPackageName(className, errorIfNotFound);
        }

        return getBundleByPackageName(className.substring(0,idx),errorIfNotFound);
    }


    @Override
    public Bundle getBundleByPackageName(String packageName, boolean errorIfNotFound) {
        BundleDefinition def = getBundleDefinitionByPackageName(packageName);
        if (def == null) {
            if (errorIfNotFound) {
                throw RX.throwB("未能通过 packageName[" + packageName + "]找到模块定义");
            }
            return null;
        }
        for (Bundle bundle : bundleList) {
            if (StringUtils.equals(def.getName(), bundle.getName())) {
                return bundle;
            }
        }
        if (errorIfNotFound) {
            throw RX.throwB("未能通过 packageName[" + packageName + "]找到Bundle");
        }
        return null;
    }

    public BundleDefinition getBundleDefinitionByPackageName(String packageName) {
        for (BundleDefinition b : bundleDefinitions) {
            if (packageName.startsWith(b.getPackageName())) {
                return b;
            }
        }
        return null;
    }

    /**
     * 通过resource判断其所属的模块，主要有以下几种情况
     * <p>
     * 情况1：解压后为/WEB-INF/{模块的java包名}
     * 情况2：处于开发环境
     *
     * @param resource
     * @return
     * @throws IOException
     */
    @Override
    public Bundle getBundleByResource(org.springframework.core.io.Resource resource) {
        try {
            String path = resource.getURL().toString();
            if (resource.isFile()) {
                if (path.indexOf("/WEB-INF/") >= 0) {
                    //情况1，根据包判断即可
                    for (Bundle bundle : bundleList) {
                        if (path.indexOf("/" + bundle.getPackageName() + "/") >= 0) {
                            return bundle;
                        }
                    }
                } else {
                    //情况2：处于开发环境
                    for (Bundle bundle : bundleList) {
                        if (path.indexOf(bundle.getRootPath()) >= 0) {
                            return bundle;
                        }
                    }
                }

            } else {
                //我们认为是jar?
                for (Bundle bundle : bundleList) {
                    if (path.indexOf(bundle.getRootPath()) >= 0) {
                        return bundle;
                    }
                }
            }

        } catch (IOException e) {
            throw RX.throwB(e);
        }

        return null;
    }

    /**
     * 注册模块
     */
    void startReg() {
        name2BundleDefinition = new HashMap<>();

        log.debug("开始注册系统中定义的模块");

        List<BundleDefinition> definitionList = new ArrayList<>();
        for (BundleLoader<BundleDefinition> loader : loaders) {
            definitionList.addAll(loader.getBundleDefList());
        }

        log.debug("检查模块的定义是否冲突");
        check(definitionList);

        log.debug("对模块进行排序,包名越长越靠前");
        sort(definitionList);

        bundleDefinitions = definitionList;

        for (BundleDefinition bundleDefinition : definitionList) {
            log.debug("" + bundleDefinition);
            name2BundleDefinition.put(bundleDefinition.getName(), bundleDefinition);
        }

        log.debug("注册完毕，开始加载模块(异步)");
        ThreadUtils.run((obj) -> {
            try {
                load();
                log.debug("加载完毕");
                loadCompleted = true;
                //发出事件~~~
                SystemBundlesContextRefreshedEvent event = new SystemBundlesContextRefreshedEvent(SystemBundlesContextImpl.this);

                appCtx.publishEvent(event);

                return obj;
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            }
        });

    }

    void load() {

        for (BundleLoader<BundleDefinition> loader : loaders) {
            loader.load(this);
        }

    }

    /**
     * 用于检查模块的定义是否冲突
     *
     * @param list
     */
    void check(List<BundleDefinition> list) {


        Map<String, BundleDefinition> names = new HashMap();
        Map<String, BundleDefinition> packages = new HashMap<>();
        for (BundleDefinition d : list) {
            if (names.containsKey(d.getName())) {
                throw RX.throwB(String.format("模块名称定义存在冲突: 【%s】，【%s】", d, names.get(d.getName())));
            }
            if (packages.containsKey(d.getPackageName())) {
                throw RX.throwB(String.format("模块包名称定义存在冲突: 【%s】，【%s】", d, packages.get(d.getPackageName())));
            }

            names.put(d.getName(), d);
            packages.put(d.getPackageName(), d);

        }
    }

    /**
     * 根据包名长度进行排序,便于根据java包名更新
     */
    void sort(List<BundleDefinition> list) {
        Collections.sort(list, new Comparator<BundleDefinition>() {
            @Override
            public int compare(BundleDefinition o1, BundleDefinition o2) {
                return Integer.compare(o2.getPackageName().length(), (o1.getPackageName().length()));
            }
        });
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return appCtx;
    }

    @Override
    public void regBundle(Bundle bundle) {

        for (Bundle bundle1 : bundleList) {
            if (StringUtils.equals(bundle1.getName(), bundle.getName())) {
                throw RX.throwB("模块: " + bundle.getName() + "已经注册过了，请不要重复注册");
            }
        }
        bundleList.add(bundle);
    }

    @Override
    public boolean isReady() {
        return loadCompleted;
    }

    @Override
    public Bundle getBundleByName(String name, boolean throwError) {
        for (Bundle bundle : bundleList) {
            if (StringUtils.equals(name, bundle.getName())) {
                return bundle;
            }
        }
        if(throwError){
            throw RX.throwB("未能找到模块: "+name);
        }
        return null;
    }
    @Override
    public Bundle getBundleByName(String name) {

        return getBundleByName(name,false);
    }
    /**
     * 通过文件名，在模块下找到文件,如果未能找到，返回空，如果找到超过1个同名文件，则抛出异常！
     *
     * @param name
     * @return
     */
    public BundleResource findResourceByName(String name, boolean errorIfNotFound) {
        List<BundleResource> finds = new ArrayList<>(1);
        for (Bundle bundle : bundleList) {
            org.springframework.core.io.Resource[] resources = bundle.findResources("**/" + name);

            for (org.springframework.core.io.Resource resource : resources) {
                finds.add(new BundleResource(bundle, resource));
            }
        }

        if (finds.isEmpty()) {
            if (errorIfNotFound) {
                throw RX.RESOURCE_NOT_FOUND.throwErrorWithFormatArgs(name);
            }
            return null;
        }
        if (finds.size() > 1) {
            log.error(finds.toString());
            throw RX.throwB("期望只到0个或一个文件，但找到多个文件:" + name);
        }

        return finds.get(0);
    }

    @Override
    public BundleResource findResourceByName(String name, String namespace, boolean errorIfNotFound) {
        // 标准化namespace（null或空字符串都视为默认命名空间）
        String normalizedNs = (namespace == null || namespace.trim().isEmpty()) ? "" : namespace.trim();

        List<BundleResource> finds = new ArrayList<>(1);
        for (Bundle bundle : bundleList) {
            // 获取bundle的namespace
            BundleDefinition bundleDef = getBundleDefinitionByName(bundle.getName());
            if (bundleDef == null) {
                continue;
            }

            String bundleNs = bundleDef.getNamespace();
            if (bundleNs == null) {
                bundleNs = "";
            }

            // 只在匹配的namespace中查找
            if (!normalizedNs.equals(bundleNs)) {
                continue;
            }

            org.springframework.core.io.Resource[] resources = bundle.findResources("**/" + name);

            for (org.springframework.core.io.Resource resource : resources) {
                finds.add(new BundleResource(bundle, resource));
            }
        }

        if (finds.isEmpty()) {
            if (errorIfNotFound) {
                String nsDesc = normalizedNs.isEmpty() ? "默认命名空间" : "命名空间: " + normalizedNs;
                throw RX.RESOURCE_NOT_FOUND.throwErrorWithFormatArgs(name + " (在" + nsDesc + "中)");
            }
            return null;
        }
        if (finds.size() > 1) {
            log.error(finds.toString());
            throw RX.throwB("期望只到0个或一个文件，但在命名空间 '" + normalizedNs + "' 中找到多个文件:" + name);
        }

        return finds.get(0);
    }

    @Override
    public boolean containBundle(String bundle) {
        return this.bundleList.stream().anyMatch(b -> StringUtils.equals(bundle, b.getName()));
    }

    @Override
    public synchronized boolean addExternalBundle(String name, String namespace, String path, boolean watch) {
        // 检查是否已存在同名Bundle
        if (containBundle(name)) {
            log.warn("Bundle [{}] 已存在，无法添加", name);
            return false;
        }

        // 验证路径
        java.io.File dir = new java.io.File(path);
        if (!dir.exists()) {
            log.warn("路径不存在: {}", path);
            return false;
        }
        if (!dir.isDirectory()) {
            log.warn("路径不是目录: {}", path);
            return false;
        }
        if (!dir.canRead()) {
            log.warn("路径无读取权限: {}", path);
            return false;
        }

        try {
            // 创建ExternalBundleDefinition
            ExternalBundleDefinition definition = new ExternalBundleDefinition(
                    name,
                    namespace != null ? namespace : "",
                    path,
                    watch
            );

            // 创建ExternalFileBundle
            ExternalFileBundle bundle = new ExternalFileBundle(this);
            bundle.setName(definition.getName());
            bundle.setBundleDefinition(definition);
            bundle.setBasePath(definition.getPath());
            bundle.setRootPath(definition.getPath());

            // 注册Bundle
            regBundle(bundle);

            log.info("动态添加外部Bundle成功: {} -> {} (namespace: {})", name, path, namespace);
            return true;

        } catch (Exception e) {
            log.error("动态添加外部Bundle失败: {}", name, e);
            return false;
        }
    }

    @Override
    public synchronized boolean removeBundle(String bundleName) {
        if (StringUtils.isEmpty(bundleName)) {
            return false;
        }

        // 查找Bundle
        Bundle targetBundle = null;
        for (Bundle bundle : bundleList) {
            if (StringUtils.equals(bundle.getName(), bundleName)) {
                targetBundle = bundle;
                break;
            }
        }

        if (targetBundle == null) {
            log.warn("Bundle [{}] 不存在，无法移除", bundleName);
            return false;
        }

        // 只允许移除外部Bundle
        if (!(targetBundle instanceof ExternalFileBundle)) {
            log.warn("Bundle [{}] 不是外部Bundle，无法移除", bundleName);
            return false;
        }

        // 移除Bundle
        bundleList.remove(targetBundle);
        log.info("移除外部Bundle成功: {}", bundleName);
        return true;
    }

    @Override
    public List<BundleDefinition> listExternalBundles() {
        return bundleList.stream()
                .filter(b -> b instanceof ExternalFileBundle)
                .map(Bundle::getDefinition)
                .collect(java.util.stream.Collectors.toList());
    }
}
