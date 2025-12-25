package com.foggyframework.dataset.client;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.proxy.DatasetClientProxy;

import java.lang.reflect.Proxy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Map;

@Getter
@Setter
public class FoggyDataSetClientFactoryBean implements FactoryBean<Object>, InitializingBean, BeanFactoryAware {

    private BeanFactory beanFactory;
    String contextId;
    // 目前只支持接口
    Class type;

    Object proxy;

    String name;

    @Nullable
    @Override
    public Object getObject() {
        // 使用 JDK 动态代理创建接口代理对象
        DatasetClientProxy handler = new DatasetClientProxy(beanFactory.getBean(SystemBundlesContext.class), type);
        proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        );
        return proxy;
    }

    @Nullable
    @Override
    public Class<?> getObjectType() {
        return type;
    }

    @Override
    public void afterPropertiesSet() {
    }

    public void validate(Map<String, Object> attributes) {
        Assert.isTrue(type.isInterface(), "目前DatasetClient只支持定义在接口上！" + type + "，不是一个接口");
    }
}
