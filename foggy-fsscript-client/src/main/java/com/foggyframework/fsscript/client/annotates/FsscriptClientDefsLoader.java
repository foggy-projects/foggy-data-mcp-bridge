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

package com.foggyframework.fsscript.client.annotates;


import com.foggyframework.fsscript.client.FsscriptClientFactoryBean;
import com.foggyframework.fsscript.client.FsscriptClientLoader;
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
class FsscriptClientDefsLoader implements ImportBeanDefinitionRegistrar, BeanFactoryAware, ResourceLoaderAware, EnvironmentAware {

    // patterned after Spring Integration IntegrationComponentScanRegistrar
    // and RibbonClientsConfigurationRegistgrar

    private ResourceLoader resourceLoader;

    private Environment environment;

    BeanFactory beanFactory;

    FsscriptClientLoader fsscriptClientLoader;// = new CommonXmlBeanDefinitionLoader();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    FsscriptClientDefsLoader() {
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
        DefaultListableBeanFactory dd;
//        dd.registerSingleton();registerSingleton
        LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableFsscriptClient.class.getName());
//		final Class<?>[] clients = attrs == null ? null : (Class<?>[]) attrs.get("clients");
//		if (clients == null || clients.length == 0) {
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.setResourceLoader(this.resourceLoader);
//        scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(FsscriptClient.class));
        Set<String> basePackages = getBasePackages(metadata);
        for (String basePackage : basePackages) {
            candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
        }
        fsscriptClientLoader = beanFactory.getBean("fsscriptClientLoader", FsscriptClientLoader.class);

        for (BeanDefinition candidateComponent : candidateComponents) {
            if (candidateComponent instanceof AnnotatedBeanDefinition) {
                // verify annotated class is an interface
                AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();


//                foggyMockServicesLoader.load(clazz);
                Map<String, Object> attributes = annotationMetadata
                        .getAnnotationAttributes(FsscriptClient.class.getCanonicalName());

                registerFsscriptClient(registry, annotationMetadata, attributes);
            }
        }

    }

    private void registerFsscriptClient(BeanDefinitionRegistry registry,
                                        AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
        String className = annotationMetadata.getClassName();
        if(registry.containsBeanDefinition(className)){
            log.warn("重复注册className: "+className+"，跳过，可能是包嵌套定义导致");
            //TODO 检查bean是否一致
            return ;
        }

        Class clazz = ClassUtils.resolveClassName(className, null);
        ConfigurableBeanFactory beanFactory = registry instanceof ConfigurableBeanFactory
                ? (ConfigurableBeanFactory) registry : null;
        String contextId = getContextId(beanFactory, attributes);
        if(StringUtils.isEmpty(contextId)){
            contextId = className;
        }
        String name = getName(attributes);
        FsscriptClientFactoryBean factoryBean = new FsscriptClientFactoryBean();
//         ApplicationContext applicationContext = beanFactory.getBean(ApplicationContext.class);
//        factoryBean.setApplicationContext(applicationContext);
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
//        validate(attributes);

        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, className);
        beanDefinition.setAttribute("feignClientsRegistrarFactoryBean", factoryBean);

        // has a default, won't be null
        boolean primary = (Boolean) attributes.get("primary");

        beanDefinition.setPrimary(primary);

        String[] qualifiers = getQualifiers(attributes);
        if (ObjectUtils.isEmpty(qualifiers)) {
            String simpleName = clazz.getSimpleName();
            simpleName = simpleName.substring(0,1).toLowerCase()+simpleName.substring(1);
            qualifiers = new String[]{contextId + "FoggyDatasetClient",simpleName};
        }

//        for (String qualifier : qualifiers) {
//            if(registry.containsBeanDefinition(qualifier)){
//                log.warn("重复注册: "+qualifier+"，跳过，可能是包嵌套定义导致");
//                return ;
//            }
//        }

        BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className,
                qualifiers);
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
                .getAnnotationAttributes(EnableFsscriptClient.class.getCanonicalName());

        Set<String> basePackages = new HashSet<>();
//        for (String pkg : (String[]) attributes.get("value")) {
//            if (StringUtils.hasText(pkg)) {
//                basePackages.add(pkg);
//            }
//        }
        for (String pkg : (String[]) attributes.get("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }
//        for (Class<?> clazz : (Class[]) attributes.get("basePackageClasses")) {
//            basePackages.add(ClassUtils.getPackageName(clazz));
//        }

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
        }
        Assert.state(host != null, "Service id not legal hostname (" + name + ")");
        return name;
    }

    private String[] getQualifiers(Map<String, Object> client) {
//        if (client == null) {
//            return null;
//        }
//        List<String> qualifierList = new ArrayList<>(
//                Arrays.asList((String[]) client.get("qualifiers")));
//        qualifierList.removeIf(qualifier -> !StringUtils.hasText(qualifier));
//        if (qualifierList.isEmpty() && getQualifier(client) != null) {
//            qualifierList = Collections.singletonList(getQualifier(client));
//        }
//        return !qualifierList.isEmpty() ? qualifierList.toArray(new String[0]) : null;
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
