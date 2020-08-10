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

package com.amazon.aws.spinnaker.plugin.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.*
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.aws.security.*
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext

import java.util.concurrent.ExecutorService

import static com.amazonaws.regions.Regions.*

class AmazonProviderUtils {
    static void synchronizeAwsProvider(AwsProvider awsProvider,
                                       AmazonCloudProvider amazonCloudProvider,
                                       AmazonClientProvider amazonClientProvider,
                                       AmazonS3DataProvider amazonS3DataProvider,
                                       AccountCredentialsRepository accountCredentialsRepository,
                                       ObjectMapper objectMapper,
                                       EddaApiFactory eddaApiFactory,
                                       ApplicationContext ctx,
                                       Registry registry,
                                       Optional<ExecutorService> reservationReportPool,
                                       Collection<AgentProvider> agentProviders,
                                       EddaTimeoutConfig eddaTimeoutConfig,
                                       DynamicConfigService dynamicConfigService) {
        def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsProvider)
        Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials, AmazonCloudProvider.ID)

        List<Agent> newlyAddedAgents = []

        //only index public images once per region
        Set<String> publicRegions = []

        //sort the accounts in case of a reconfigure, we are more likely to re-index the public images in the same caching agent
        //TODO(cfieber)-rework this is after rework of AWS Image/NamedImage keys
        allAccounts.sort { it.name }.each { NetflixAmazonCredentials credentials ->
            for (AmazonCredentials.AWSRegion region : credentials.regions) {
                if (!scheduledAccounts.contains(credentials.name)) {
                    newlyAddedAgents << new ClusterCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, objectMapper, registry, eddaTimeoutConfig)
                    newlyAddedAgents << new LaunchConfigCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
                    newlyAddedAgents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry, false, dynamicConfigService)
                    if (!publicRegions.contains(region.name)) {
                        newlyAddedAgents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry, true, dynamicConfigService)
                        publicRegions.add(region.name)
                    }
                    newlyAddedAgents << new InstanceCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
                    newlyAddedAgents << new AmazonLoadBalancerCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, eddaApiFactory.createApi(credentials.edda, region.name), objectMapper, registry)
                    newlyAddedAgents << new AmazonApplicationLoadBalancerCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, eddaApiFactory.createApi(credentials.edda, region.name), objectMapper, registry, eddaTimeoutConfig)
                    newlyAddedAgents << new ReservedInstancesCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
                    newlyAddedAgents << new AmazonCertificateCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)

                    if (dynamicConfigService.isEnabled("aws.features.cloud-formation", false)) {
                        newlyAddedAgents << new AmazonCloudFormationCachingAgent(amazonClientProvider, credentials, region.name, registry)
                    }

                    if (credentials.eddaEnabled && !eddaTimeoutConfig.disabledRegions.contains(region.name)) {
                        newlyAddedAgents << new EddaLoadBalancerCachingAgent(eddaApiFactory.createApi(credentials.edda, region.name), credentials, region.name, objectMapper)
                    } else {
                        newlyAddedAgents << new AmazonLoadBalancerInstanceStateCachingAgent(
                                amazonClientProvider, credentials, region.name, objectMapper, ctx
                        )
                    }

                    if (dynamicConfigService.isEnabled("aws.features.launch-templates", false)) {
                        newlyAddedAgents << new AmazonLaunchTemplateCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
                    }
                }
            }
        }

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        if (reservationReportPool.isPresent()) {
            if (awsProvider.agentScheduler) {
                synchronizeReservationReportCachingAgentAccounts(awsProvider, allAccounts)
            } else {
                // This caching agent runs across all accounts in one iteration (to maintain consistency).
                newlyAddedAgents << new ReservationReportCachingAgent(
                        registry, amazonClientProvider, amazonS3DataProvider, allAccounts, objectMapper, reservationReportPool.get(), ctx
                )
            }
        }

        agentProviders.findAll { it.supports(AwsProvider.PROVIDER_NAME) }.each {
            newlyAddedAgents.addAll(it.agents())
        }
        // Actually schedule agents.
        if (awsProvider.agentScheduler) {
            ProviderUtils.rescheduleAgents(awsProvider, newlyAddedAgents)
        }
        awsProvider.agents.addAll(newlyAddedAgents)
        awsProvider.synchronizeHealthAgents()
    }

    static void synchronizeReservationReportCachingAgentAccounts(AwsProvider awsProvider,
                                                                 Collection<NetflixAmazonCredentials> allAccounts) {
        ReservationReportCachingAgent reservationReportCachingAgent = awsProvider.agents.find { agent ->
            agent instanceof ReservationReportCachingAgent
        }

        if (reservationReportCachingAgent) {
            def reservationReportAccounts = reservationReportCachingAgent.accounts
            def oldAccountNames = reservationReportAccounts.collect { it.name }
            def newAccountNames = allAccounts.collect { it.name }
            def accountNamesToDelete = oldAccountNames - newAccountNames
            def accountNamesToAdd = newAccountNames - oldAccountNames

            accountNamesToDelete.each { accountNameToDelete ->
                def accountToDelete = reservationReportAccounts.find { it.name == accountNameToDelete }

                if (accountToDelete) {
                    reservationReportAccounts.remove(accountToDelete)
                }
            }

            accountNamesToAdd.each { accountNameToAdd ->
                def accountToAdd = allAccounts.find { it.name == accountNameToAdd }

                if (accountToAdd) {
                    reservationReportAccounts.add(accountToAdd)
                }
            }
        }
    }

    static void synchronizeAwsInfrastructureProvider(AwsInfrastructureProvider awsInfrastructureProvider,
                                                     AmazonClientProvider amazonClientProvider,
                                                     AccountCredentialsRepository accountCredentialsRepository,
                                                     @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper,
                                                     Registry registry,
                                                     EddaTimeoutConfig eddaTimeoutConfig) {
        def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsInfrastructureProvider)
        def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials, AmazonCloudProvider.ID)

        Set<String> regions = new HashSet<>()
        allAccounts.each { NetflixAmazonCredentials credentials ->
            for (AmazonCredentials.AWSRegion region : credentials.regions) {
                if (!scheduledAccounts.contains(credentials.name)) {
                    def newlyAddedAgents = []

                    if (regions.add(region.name)) {
                        newlyAddedAgents << new AmazonInstanceTypeCachingAgent(region.name, accountCredentialsRepository)
                    }

                    newlyAddedAgents << new AmazonElasticIpCachingAgent(amazonClientProvider, credentials, region.name)
                    newlyAddedAgents << new AmazonKeyPairCachingAgent(amazonClientProvider, credentials, region.name)
                    newlyAddedAgents << new AmazonSecurityGroupCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper, registry, eddaTimeoutConfig)
                    newlyAddedAgents << new AmazonSubnetCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper)
                    newlyAddedAgents << new AmazonVpcCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper)

                    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
                    // In that case, we need to do the scheduling here (because accounts have been added to a running system).
                    if (awsInfrastructureProvider.agentScheduler) {
                        ProviderUtils.rescheduleAgents(awsInfrastructureProvider, newlyAddedAgents)
                    }

                    awsInfrastructureProvider.agents.addAll(newlyAddedAgents)
                }
            }
        }
    }

    // From com/netflix/spinnaker/clouddriver/aws/security/DefaultAmazonAccountsSynchronizer.groovy
    // use NetflixAssumeRoleAmazonCredentials instead of NetflixAmazonCredentials. Might be a problem when
    // NetflixAssumeRoleAmazonCredentials is not used.
    static void AmazonAccountsSynchronizer(
            CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
            CredentialsConfig credentialsConfig,
            AccountCredentialsRepository accountCredentialsRepository,
            DefaultAccountConfigurationProperties defaultAccountConfigurationProperties,
            CatsModule catsModule) {
        if (!credentialsConfig.accounts && !credentialsConfig.defaultAssumeRole) {
            def defaultEnvironment = defaultAccountConfigurationProperties.environment ?: defaultAccountConfigurationProperties.env
            def defaultAccountType = defaultAccountConfigurationProperties.accountType ?: defaultAccountConfigurationProperties.env
            credentialsConfig.accounts = [new CredentialsConfig.Account(name: defaultAccountConfigurationProperties.env, environment: defaultEnvironment, accountType: defaultAccountType)]
            if (!credentialsConfig.defaultRegions) {
                credentialsConfig.defaultRegions = [US_EAST_1, US_WEST_1, US_WEST_2, EU_WEST_1].collect {
                    new CredentialsConfig.Region(name: it.name)
                }
            }
        }

        List<? extends NetflixAmazonCredentials> accounts = credentialsLoader.load(credentialsConfig)

        def (ArrayList<NetflixAmazonCredentials> accountsToAdd, List<String> namesOfDeletedAccounts) =
        ProviderUtils.calculateAccountDeltas(accountCredentialsRepository, NetflixAssumeRoleAmazonCredentials, accounts)

        accountsToAdd.each { NetflixAmazonCredentials account ->
            accountCredentialsRepository.save(account.name, account)
        }

        ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule)

        accountCredentialsRepository.all.findAll {
            it instanceof NetflixAmazonCredentials
        } as List<NetflixAmazonCredentials>
    }

    // from com/netflix/spinnaker/clouddriver/aws/security/DefaultAmazonAccountsSynchronizer.groovy
    // Modified to not remove ECS account.
    static List calculateAccountDeltas(def accountCredentialsRepository, def credentialsType, def desiredAccounts) {
        def oldNames = accountCredentialsRepository.all.findAll {
            credentialsType.isInstance(it) && !NetflixECSCredentials.isInstance(it)
        }.collect {
            it.name
        }

        def newNames = desiredAccounts.collect {
            it.name
        }

        def accountNamesToDelete = oldNames - newNames
        def accountNamesToAdd = newNames - oldNames

        accountNamesToDelete.each { accountName ->
            accountCredentialsRepository.delete(accountName)
        }

        def accountsToAdd = desiredAccounts.findAll { account ->
            accountNamesToAdd.contains(account.name)
        }

        return [accountsToAdd, accountNamesToDelete]
    }
}
