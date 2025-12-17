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

    boolean containBundle(String bundle);
}
