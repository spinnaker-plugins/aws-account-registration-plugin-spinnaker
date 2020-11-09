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

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;

@Slf4j
class LazyLoadCredentialsRepository extends MapBackedCredentialsRepository<NetflixAmazonCredentials> {
    AbstractCredentialsLoader<? extends NetflixAmazonCredentials> loader;

    public LazyLoadCredentialsRepository(
            @Lazy CredentialsLifecycleHandler<NetflixAmazonCredentials> eventHandler,
            @Lazy @Qualifier("amazonCredentialsLoader") AbstractCredentialsLoader<? extends NetflixAmazonCredentials> loader) {
        super("aws", eventHandler);
        this.loader = loader;
    }

    @Override
    public NetflixAmazonCredentials getOne(String key) {
        NetflixAmazonCredentials cred = super.getOne(key);
        if (cred == null) {
            log.info("Could not find account, {}. Checking remote repository.", key);
            loader.load();
            return super.getOne(key);
        }
        return cred;
    }
}
