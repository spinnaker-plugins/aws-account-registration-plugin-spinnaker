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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.agent.*;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AmazonAwsInfrastructureProviderSynchronizer {
    public static void synchronizeAwsInfrastructureProvider(AwsInfrastructureProvider awsInfrastructureProvider,
                                                             AmazonClientProvider amazonClientProvider,
                                                             AccountCredentialsRepository accountCredentialsRepository,
                                                             ObjectMapper amazonObjectMapper, Registry registry,
                                                             EddaTimeoutConfig eddaTimeoutConfig) {

        Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(awsInfrastructureProvider);
        Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(
                accountCredentialsRepository, NetflixAmazonCredentials.class, AmazonCloudProvider.ID);
        Set<String> regions = new HashSet<String>();

        for (NetflixAmazonCredentials credentials: allAccounts ) {
            for (AmazonCredentials.AWSRegion region : credentials.getRegions()) {
                if (!scheduledAccounts.contains(credentials.getName())) {
                    List<Agent> newlyAddedAgents = new ArrayList<Agent>();
                    if (regions.add(region.getName())) {
                        newlyAddedAgents.add(new AmazonInstanceTypeCachingAgent(region.getName(), accountCredentialsRepository));
                    }
                    newlyAddedAgents.add(new AmazonElasticIpCachingAgent(amazonClientProvider, credentials, region.getName()));
                    newlyAddedAgents.add(new AmazonKeyPairCachingAgent(amazonClientProvider, credentials, region.getName()));
                    newlyAddedAgents.add(new AmazonSecurityGroupCachingAgent(amazonClientProvider, credentials, region.getName(), amazonObjectMapper, registry, eddaTimeoutConfig));
                    newlyAddedAgents.add(new AmazonSubnetCachingAgent(amazonClientProvider, credentials, region.getName(), amazonObjectMapper));
                    newlyAddedAgents.add(new AmazonVpcCachingAgent(amazonClientProvider, credentials, region.getName(), amazonObjectMapper));

                    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
                    // In that case, we need to do the scheduling here (because accounts have been added to a running system).
                    if (DefaultGroovyMethods.asBoolean(awsInfrastructureProvider.getAgentScheduler())) {
                        ProviderUtils.rescheduleAgents(awsInfrastructureProvider, newlyAddedAgents);
                    }

                    awsInfrastructureProvider.getAgents().addAll(newlyAddedAgents);
                }
            }
        }
    }
}
