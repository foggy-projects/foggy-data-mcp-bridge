package com.foggyframework.bundle;

import com.foggyframework.core.bundle.BundleDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

public interface SystemBundlesContext {

    ApplicationContext getApplicationContext();

    void regBundle(Bundle bundle);

    /**
     * 获取当前系统中运行的所有模块
     *
     * @return
     */
    List<Bundle> getBundleList();

    boolean isReady();

    Bundle getBundleByName(String name, boolean throwError);

    Bundle getBundleByName(String name);

    /**
     * 根据模块名称寻找对应的模块定义
     *
     * @param name
     * @return
     */
    BundleDefinition getBundleDefinitionByName(String name);

    Bundle getBundleByPackageName(String packageName);

    Bundle getBundleByClassName(String className,boolean errorIfNotFound);

    Bundle getBundleByPackageName(String packageName,boolean errorIfNotFound);

    BundleDefinition getBundleDefinitionByPackageName(String packageName);

    /**
     * 通过资源，判断这个资源所在的模块
     * @param resource
     * @return
     * @throws IOException
     */
    Bundle getBundleByResource(Resource resource);

    /**
     * 通过文件名，在模块下找到文件,如果未能找到，返回空，如果找到超过1个同名文件，则抛出异常！
     *
     * @param name
     * @return
     */
    BundleResource findResourceByName(String name, boolean errorIfNotFound);

    /**
     * 在指定命名空间的Bundle中查找资源
     *
     * @param name 资源名称
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     * @param errorIfNotFound 找不到时是否抛出异常
     * @return BundleResource对象
     */
    BundleResource findResourceByName(String name, String namespace, boolean errorIfNotFound);

    boolean containBundle(String bundle);

    /**
     * 动态添加外部Bundle
     *
     * @param name      Bundle名称（唯一标识）
     * @param namespace 命名空间（空字符串表示默认命名空间）
     * @param path      外部目录路径
     * @param watch     是否监听文件变化
     * @return 是否添加成功
     */
    boolean addExternalBundle(String name, String namespace, String path, boolean watch);

    /**
     * 原子替换现有外部Bundle。替换期间读者只会看到旧版本或新版本。
     *
     * @return 是否替换成功
     */
    boolean replaceExternalBundle(String name, String namespace, String path, boolean watch);

    /**
     * 移除指定的Bundle
     *
     * @param bundleName Bundle名称
     * @return 是否移除成功
     */
    boolean removeBundle(String bundleName);

    /**
     * 获取所有外部Bundle的定义
     *
     * @return Bundle定义列表
     */
    List<BundleDefinition> listExternalBundles();
}
