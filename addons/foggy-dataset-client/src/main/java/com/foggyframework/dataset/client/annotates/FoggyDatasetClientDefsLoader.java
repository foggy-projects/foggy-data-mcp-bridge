/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.foggyframework.dataset.client.annotates;

import com.foggyframework.dataset.client.FoggyDataSetClientFactoryBean;
import com.foggyframework.dataset.client.FoggyDatasetClientLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
class FoggyDatasetClientDefsLoader implements ImportBeanDefinitionRegistrar, BeanFactoryAware, ResourceLoaderAware, EnvironmentAware {

    private ResourceLoader resourceLoader;

    private Environment environment;

    BeanFactory beanFactory;

    FoggyDatasetClientLoader foggyDatasetClientLoader;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    FoggyDatasetClientDefsLoader() {
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        register(metadata, registry);
    }

    public void register(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableDatasetClient.class.getName());

        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.setResourceLoader(this.resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(DataSetClient.class));
        Set<String> basePackages = getBasePackages(metadata);
        for (String basePackage : basePackages) {
            candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
        }
        foggyDatasetClientLoader = beanFactory.getBean("foggyDatasetClientLoader", FoggyDatasetClientLoader.class);

        for (BeanDefinition candidateComponent : candidateComponents) {
            if (candidateComponent instanceof AnnotatedBeanDefinition) {
                AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();

                Map<String, Object> attributes = annotationMetadata
                        .getAnnotationAttributes(DataSetClient.class.getCanonicalName());

                registerDatasetClient(registry, annotationMetadata, attributes);
            }
        }
    }

    private void registerDatasetClient(BeanDefinitionRegistry registry,
                                       AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
        String className = annotationMetadata.getClassName();
        if (registry.containsBeanDefinition(className)) {
            log.warn("重复注册className: " + className + "，跳过，可能是包嵌套定义导致");
            return;
        }

        Class clazz = ClassUtils.resolveClassName(className, null);
        ConfigurableBeanFactory beanFactory = registry instanceof ConfigurableBeanFactory
                ? (ConfigurableBeanFactory) registry : null;
        String contextId = getContextId(beanFactory, attributes);
        if (!StringUtils.hasText(contextId)) {
            contextId = className;
        }
        String name = getName(attributes);
        FoggyDataSetClientFactoryBean factoryBean = new FoggyDataSetClientFactoryBean();
        factoryBean.setBeanFactory(beanFactory);
        factoryBean.setName(name);
        factoryBean.setContextId(contextId);
        factoryBean.setType(clazz);
        factoryBean.validate(attributes);
        BeanDefinitionBuilder definition = BeanDefinitionBuilder
                .genericBeanDefinition(clazz, () -> {
                    return factoryBean.getObject();
                });
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        definition.setLazyInit(true);

        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, className);
        beanDefinition.setAttribute("feignClientsRegistrarFactoryBean", factoryBean);

        boolean primary = (Boolean) attributes.get("primary");
        beanDefinition.setPrimary(primary);

        String[] qualifiers = getQualifiers(attributes);
        if (ObjectUtils.isEmpty(qualifiers)) {
            qualifiers = new String[]{contextId + "FoggyDatasetClient"};
        }

        BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className, qualifiers);
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
    }

    protected ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                boolean isCandidate = false;
                if (beanDefinition.getMetadata().isIndependent()) {
                    if (!beanDefinition.getMetadata().isAnnotation()) {
                        isCandidate = true;
                    }
                }
                return isCandidate;
            }
        };
    }

    protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableDatasetClient.class.getCanonicalName());

        Set<String> basePackages = new HashSet<>();
        for (String pkg : (String[]) attributes.get("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }

        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }
        return basePackages;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    String getName(Map<String, Object> attributes) {
        return getName(null, attributes);
    }

    String getName(ConfigurableBeanFactory beanFactory, Map<String, Object> attributes) {
        String name = (String) attributes.get("serviceId");
        if (!StringUtils.hasText(name)) {
            name = (String) attributes.get("name");
        }
        if (!StringUtils.hasText(name)) {
            name = (String) attributes.get("value");
        }
        name = resolve(beanFactory, name);
        return getName(name);
    }

    private String resolve(ConfigurableBeanFactory beanFactory, String value) {
        if (StringUtils.hasText(value)) {
            if (beanFactory == null) {
                return this.environment.resolvePlaceholders(value);
            }
            BeanExpressionResolver resolver = beanFactory.getBeanExpressionResolver();
            String resolved = beanFactory.resolveEmbeddedValue(value);
            if (resolver == null) {
                return resolved;
            }
            return String.valueOf(resolver.evaluate(resolved,
                    new BeanExpressionContext(beanFactory, null)));
        }
        return value;
    }

    static String getName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }

        String host = null;
        try {
            String url;
            if (!name.startsWith("http://") && !name.startsWith("https://")) {
                url = "http://" + name;
            } else {
                url = name;
            }
            host = new URI(url).getHost();

        } catch (URISyntaxException e) {
            // ignore
        }
        Assert.state(host != null, "Service id not legal hostname (" + name + ")");
        return name;
    }

    private String[] getQualifiers(Map<String, Object> client) {
        return null;
    }

    private String getQualifier(Map<String, Object> client) {
        if (client == null) {
            return null;
        }
        String qualifier = (String) client.get("qualifier");
        if (StringUtils.hasText(qualifier)) {
            return qualifier;
        }
        return null;
    }

    private String getContextId(ConfigurableBeanFactory beanFactory,
                                Map<String, Object> attributes) {
        String contextId = (String) attributes.get("contextId");
        if (!StringUtils.hasText(contextId)) {
            return getName(attributes);
        }

        contextId = resolve(beanFactory, contextId);
        return getName(contextId);
    }
}
