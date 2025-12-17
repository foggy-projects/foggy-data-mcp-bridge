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

package com.foggyframework.fsscript.client;


import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class FsscriptClientLoader {

//    Map<String, List<MockBeanDef>> beanName2MockBeanDef = new HashMap<>();
//
//    Map<Class<?>, List<MockBeanDef>> className2MockBeanDef = new HashMap<>();
//
//    public List<MockBeanDef> getMockBeanDefByBeanName(String beanName) {
//        return beanName2MockBeanDef.get(beanName);
//    }
//
//    public List<MockBeanDef> getMockBeanDefByClass(Class<?> beanClass) {
//        if (beanClass == Object.class) {
//            return Collections.EMPTY_LIST;
//        }
//        for (Map.Entry<Class<?>, List<MockBeanDef>> entry : className2MockBeanDef.entrySet()) {
//            if (beanClass.isAssignableFrom(entry.getKey())) {
//                return entry.getValue();
//            }
//            if (entry.getKey().isAssignableFrom(beanClass)) {
//                return entry.getValue();
//            }
//            if (beanClass == entry.getKey()) {
//                return entry.getValue();
//            }
//        }
//        return Collections.EMPTY_LIST;
//    }
//
//    public void load(Class<?> clazz) {
//        MockService mockService = clazz.getAnnotation(MockService.class);
//
//        log.info("加载MockService: " + clazz);
//
//        String beanName = mockService.value();
//        Class<?> beanClazz = mockService.clazz();
//        if (StringUtils.isEmpty(beanName) && beanClazz == Object.class) {
//            throw RX.throwB("Mock服务中，beanName或beanClazz必须至少定义一个: " + clazz);
//        }
//        Set<String> methods = new HashSet<>();
//
//        //注意，它仅加载clazz声明的类，而不去加载父类
//        for (Method method : clazz.getDeclaredMethods()) {
//            methods.add(method.getName());
//        }
//        MockBeanDef def = new MockBeanDef(beanName, clazz, methods,clazz);
//        if (!StringUtils.isEmpty(beanName)) {
//            log.info("beanName: " + beanName);
//            List<MockBeanDef> ll;
//            ll = beanName2MockBeanDef.computeIfAbsent(beanName, k -> new ArrayList<>());
//            ll.add(def);
//
//        }
//        if (beanClazz != Object.class) {
//            log.info("beanClass: " + beanClazz);
//            List<MockBeanDef> ll = className2MockBeanDef.computeIfAbsent(beanClazz, k -> new ArrayList<>());
//            ll.add(def);
//        }
//
//    }


}
