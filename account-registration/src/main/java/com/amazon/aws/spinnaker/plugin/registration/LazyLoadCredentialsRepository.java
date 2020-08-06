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

import com.netflix.discovery.shared.Application;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import groovy.lang.MetaClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Set;

@Component
@Slf4j
class LazyLoadCredentialsRepository extends MapBackedAccountCredentialsRepository {
    AmazonPollingSynchronizer synchronizer;

    @Autowired
    public void setSynchronizer( AmazonPollingSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    @Override
    public AccountCredentials getOne(String key) {
        log.info("Custom credentials repo! Processing {}.", key);
        synchronizer.sync();
        AccountCredentials cred = super.getOne(key);
        if (cred == null) {
            log.info("Could not find account, {}. Checking remote repository.", key);
            synchronizer.sync();
            cred = super.getOne(key);
            if (cred != null) {
                save(key, cred);
                return cred;
            }
            log.error("Could not find account, {}, in remote repository.", key);
        }
        return cred;
    }
}


