/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.amazon.aws.spinnaker.plugin.registration;

import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class AccountRegistrationPlugin extends PrivilegedSpringPlugin {

    public AccountRegistrationPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void registerBeanDefinitions(BeanDefinitionRegistry registry) {
        BeanDefinition lazyLoadCredentialsRepositoryDefinition = primaryBeanDefinitionFor(LazyLoadCredentialsRepository.class);
        try {
            log.debug("Registering bean: {}", lazyLoadCredentialsRepositoryDefinition.getBeanClassName());
            registry.registerBeanDefinition("accountCredentialsRepository", lazyLoadCredentialsRepositoryDefinition);
        } catch (BeanDefinitionStoreException e) {
            log.error("Could not register bean {}", lazyLoadCredentialsRepositoryDefinition.getBeanClassName());
        }
        List<Class> classes = new ArrayList<>(Arrays.asList(AmazonPollingSynchronizer.class,
                AmazonEC2InfraCachingAgentScheduler.class,
                AmazonAWSCachingAgentScheduler.class, AmazonECSCachingAgentScheduler.class));
        for (Class calssToAdd : classes) {
            BeanDefinition beanDefinition = beanDefinitionFor(calssToAdd);
            try {
                log.debug("Registering bean: {}", beanDefinition.getBeanClassName());
                registerBean(beanDefinition, registry);
            } catch (ClassNotFoundException e) {
                log.error("Could not register bean {}", beanDefinition.getBeanClassName());
            }
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