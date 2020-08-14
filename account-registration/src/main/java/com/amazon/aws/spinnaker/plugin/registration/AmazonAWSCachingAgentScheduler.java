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
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class AmazonAWSCachingAgentScheduler {
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final Registry registry;
    private final EddaTimeoutConfig eddaTimeoutConfig;
    private final AmazonClientProvider amazonClientProvider;
    private final AwsProvider awsProvider;
    private final AmazonCloudProvider amazonCloudProvider;
    private final AmazonS3DataProvider amazonS3DataProvider;
    private final ObjectMapper objectMapper;
    private final EddaApiFactory eddaApiFactory;
    private final ApplicationContext ctx;
    private final Optional<ExecutorService> reservationReportPool;
    private final Collection<AgentProvider> agentProviders;
    private final DynamicConfigService dynamicConfigService;

    @Autowired
    private AmazonAWSCachingAgentScheduler(LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
                                           AmazonClientProvider amazonClientProvider,
                                           Registry registry, EddaTimeoutConfig eddaTimeoutConfig, AwsProvider awsProvider,
                                           AmazonCloudProvider amazonCloudProvider, AmazonS3DataProvider amazonS3DataProvider,
                                           ObjectMapper objectMapper, EddaApiFactory eddaApiFactory, ApplicationContext ctx,
                                           Optional<ExecutorService> reservationReportPool, Collection<AgentProvider> agentProviders,
                                           DynamicConfigService dynamicConfigService
    ) {
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.amazonClientProvider = amazonClientProvider;
        this.registry = registry;
        this.eddaTimeoutConfig = eddaTimeoutConfig;
        this.awsProvider = awsProvider;
        this.amazonCloudProvider = amazonCloudProvider;
        this.amazonS3DataProvider = amazonS3DataProvider;
        this.objectMapper = objectMapper;
        this.eddaApiFactory = eddaApiFactory;
        this.ctx = ctx;
        this.reservationReportPool = reservationReportPool;
        this.agentProviders = agentProviders;
        this.dynamicConfigService = dynamicConfigService;
    }

    @Scheduled(fixedDelayString = "${accountProvision.syncAgentFrequencyInMilliSeconds:10000}")
    public void synchronizeAwsProvider() {
        AmazonProviderUtils.synchronizeAwsProvider(awsProvider, amazonCloudProvider, amazonClientProvider,
                amazonS3DataProvider, lazyLoadCredentialsRepository, objectMapper, eddaApiFactory, ctx,
                registry, reservationReportPool, agentProviders, eddaTimeoutConfig, dynamicConfigService);
    }
}
