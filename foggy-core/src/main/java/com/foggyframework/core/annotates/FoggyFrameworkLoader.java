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

package com.foggyframework.core.annotates;


import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.spring.bean.ICGLibProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Spencer Gibb
 * @author Jakub Narloch
 * @author Venil Noronha
 * @author Gang Li
 * @author Michal Domagala
 * @author Marcin Grzejszczak
 * @author Olga Maciaszek-Sharma
 * @author Jasbir Singh
 */
@Slf4j
class FoggyFrameworkLoader implements ImportBeanDefinitionRegistrar, EnvironmentAware {


    private Environment environment;

    FoggyFrameworkLoader() {
    }


    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        register(metadata, registry);
    }

//    @Override
//    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
//        configurableListableBeanFactory.registerSingleton("commonXmlBeanDefinitionLoader",commonXmlBeanDefinitionLoader);
//    }

    public void register(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        DefaultListableBeanFactory dd;
//        dd.registerSingleton();registerSingleton
//        LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableFoggyFramework.class.getName());
        String bundleName = (String) attrs.get("bundleName");
        if (com.foggyframework.core.utils.StringUtils.isNotEmpty(bundleName)) {
            //注册一个 BundleDefinition

            BeanDefinitionBuilder definition = BeanDefinitionBuilder
                    .genericBeanDefinition(BundleDefinition.class, () -> {
                        return new BundleDefinition() {
                            @Override
                            public String getPackageName() {
                                try {
                                    return Class.forName(metadata.getClassName()).getPackage().getName();
                                } catch (ClassNotFoundException e) {
                                    log.error("加载异常？？" + e.getMessage());
                                    return metadata.getClassName().substring(0, metadata.getClassName().lastIndexOf("."));
                                }
                            }

                            @Override
                            public String toString() {
                                return bundleName+","+getPackageName();
                            }

                            @Override
                            public String getName() {
                                return bundleName;
                            }

                            @Override
                            public Class<?> getDefinitionClass() {
                                try {
                                    return Class.forName(metadata.getClassName());
                                } catch (ClassNotFoundException e) {
                                    throw RX.throwB(e);
                                }
                            }
                        };
                    });
            AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
            registry.registerBeanDefinition(bundleName + "-bundle", beanDefinition);
        }
        Set<String> xx = getBasePackages(metadata);
        for (String x : xx) {
            String p = x.replaceAll("\\*", "");
            ICGLibProxy.addFoggyFrameworkPackage(p);
        }

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
                .getAnnotationAttributes(EnableFoggyFramework.class.getCanonicalName());

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


}
