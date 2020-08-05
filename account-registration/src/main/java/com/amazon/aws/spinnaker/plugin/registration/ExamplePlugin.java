/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.amazon.aws.spinnaker.plugin.registration;

import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin;
import org.pf4j.PluginWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class ExamplePlugin extends PrivilegedSpringPlugin {

    public ExamplePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void registerBeanDefinitions(BeanDefinitionRegistry registry) {
        BeanDefinition pollingBeanDefinition = beanDefinitionFor(AmazonPollingSynchronizer.class);
        BeanDefinition amazonCachingAgentScheduler = beanDefinitionFor(AmazonCachingAgentScheduler.class);
        BeanDefinition lazyLoadCredentialsRepositoryDefinition = primaryBeanDefinitionFor(LazyLoadCredentialsRepository.class);

        try {
            log.debug("Registering bean: {}", pollingBeanDefinition.getBeanClassName());
            registerBean(pollingBeanDefinition, registry);
            registerBean(amazonCachingAgentScheduler, registry);

        } catch (ClassNotFoundException e) {
            log.error("Could not register bean {}", pollingBeanDefinition.getBeanClassName());
        }

        try {
            log.debug("Registering bean: {}", lazyLoadCredentialsRepositoryDefinition.getBeanClassName());
            registry.registerBeanDefinition("accountCredentialsRepository", lazyLoadCredentialsRepositoryDefinition);
//            registerBean(pollingBeanDefinition, registry);
        } catch (BeanDefinitionStoreException e) {
            log.error("Could not register bean {}", lazyLoadCredentialsRepositoryDefinition.getBeanClassName());
        }
    }

    @Override
    public void start() {
        log.info("{} plugin started", this.getClass().getSimpleName());
    }

    @Override
    public void stop() {
        log.info("{} plugin stopped", this.getClass().getSimpleName());
    }
}