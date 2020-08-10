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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@DependsOn("netflixAmazonCredentials")
@Slf4j
public class AmazonEC2InfraCachingAgentScheduler {
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final AwsInfrastructureProvider awsInfrastructureProvider;
    private final AmazonClientProvider amazonClientProvider;
    @Qualifier("amazonObjectMapper")
    private final ObjectMapper amazonObjectMapper;
    private final Registry registry;
    private final EddaTimeoutConfig eddaTimeoutConfig;

    @Autowired
    AmazonEC2InfraCachingAgentScheduler(
            LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
            AwsInfrastructureProvider awsInfrastructureProvider,
            AmazonClientProvider amazonClientProvider, ObjectMapper amazonObjectMapper,
            Registry registry, EddaTimeoutConfig eddaTimeoutConfig
    ) {
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.awsInfrastructureProvider = awsInfrastructureProvider;
        this.amazonClientProvider = amazonClientProvider;
        this.amazonObjectMapper = amazonObjectMapper;
        this.registry = registry;
        this.eddaTimeoutConfig = eddaTimeoutConfig;
    }

    @Scheduled(fixedDelayString = "${accountProvision.syncAgentFrequencyInMilliSeconds:10000}")
    public void synchronizeAwsInfrastructureProvider() {
        AmazonProviderUtils.synchronizeAwsInfrastructureProvider(awsInfrastructureProvider, amazonClientProvider,
                lazyLoadCredentialsRepository, amazonObjectMapper, registry, eddaTimeoutConfig);
    }
}


