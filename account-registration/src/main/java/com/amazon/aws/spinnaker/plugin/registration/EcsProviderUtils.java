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

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.EcsAccountBuilder;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Most code in here are hacks. They really should be implemented in ECS driver instead.
public final class EcsProviderUtils {
    public static void synchronizeEcsCredentialsMapper(EcsAccountMapper ecsAccountMapper,
                                                       LazyLoadCredentialsRepository lazyLoadCredentialsRepository) throws IllegalAccessException, NoSuchFieldException {
        Set<? extends AccountCredentials> allAccounts = lazyLoadCredentialsRepository.getAll();
        Collection<NetflixAssumeRoleEcsCredentials> ecsAccounts =
                (Collection<NetflixAssumeRoleEcsCredentials>)
                        allAccounts.stream()
                                .filter(credentials -> credentials instanceof NetflixAssumeRoleEcsCredentials)
                                .collect(Collectors.toSet());
        Map<String, NetflixAssumeRoleEcsCredentials> ecsCredentialsMap = new HashMap<>();
        Map<String, NetflixAmazonCredentials> awsCredentialsMap = new HashMap<>();

        for (NetflixAssumeRoleEcsCredentials ecsAccount : ecsAccounts) {
            ecsCredentialsMap.put(ecsAccount.getAwsAccount(), ecsAccount);

            allAccounts.stream()
                    .filter(credentials -> credentials.getName().equals(ecsAccount.getAwsAccount()))
                    .findFirst()
                    .ifPresent(
                            v -> awsCredentialsMap.put(ecsAccount.getName(), (NetflixAmazonCredentials) v));
        }
        // Update EcsAccountMapper's private fields.
        Field ecsCredentialsMapField = ecsAccountMapper.getClass().getDeclaredField("ecsCredentialsMap");
        ecsCredentialsMapField.setAccessible(true);
        Field awsCredentialsMapField = ecsAccountMapper.getClass().getDeclaredField("awsCredentialsMap");
        awsCredentialsMapField.setAccessible(true);
        ecsCredentialsMapField.set(ecsAccountMapper, ecsCredentialsMap);
        awsCredentialsMapField.set(ecsAccountMapper, awsCredentialsMap);
    }

    // Sync ECS accounts. Code is mostly from com/netflix/spinnaker/clouddriver/ecs/security/EcsCredentialsInitializer.java
    public static void synchronizeEcsAccounts(
            LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
            CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
            ECSCredentialsConfig ecsCredentialsConfig, CatsModule catsModule
    ) throws Throwable {
        List<NetflixAmazonCredentials> credentials = new LinkedList<>();
        for (AccountCredentials accountCredentials : lazyLoadCredentialsRepository.getAll()) {
            if (accountCredentials instanceof NetflixAmazonCredentials) {
                for (ECSCredentialsConfig.Account ecsAccount : ecsCredentialsConfig.getAccounts()) {
                    if (ecsAccount.getAwsAccount().equals(accountCredentials.getName())) {

                        NetflixAmazonCredentials netflixAmazonCredentials =
                                (NetflixAmazonCredentials) accountCredentials;

                        // TODO: accountCredentials should be serializable or somehow cloneable.
                        CredentialsConfig.Account account =
                                EcsAccountBuilder.build(netflixAmazonCredentials, ecsAccount.getName(), "ecs");

                        CredentialsConfig ecsCopy = new CredentialsConfig();
                        ecsCopy.setAccounts(Collections.singletonList(account));

                        NetflixECSCredentials ecsCredentials =
                                new NetflixAssumeRoleEcsCredentials(
                                        (NetflixAssumeRoleAmazonCredentials) credentialsLoader.load(ecsCopy).get(0),
                                        ecsAccount.getAwsAccount());
                        credentials.add(ecsCredentials);
                        break;
                    }
                }
            }
        }
        // calculateAccountDeltas actually deletes accounts in repo.
        List results = ProviderUtils.calculateAccountDeltas(lazyLoadCredentialsRepository,
                NetflixECSCredentials.class, credentials);
        List<NetflixECSCredentials> accountsToAdd = new ArrayList<>();
        List<String> namesOfDeletedAccounts = new ArrayList<>();
        // Maybe unsafe. need checking.
        for (Object obj : (LinkedList<?>) results.get(0)) {
            accountsToAdd.add((NetflixECSCredentials) obj);
        }
        for (Object obj : (ArrayList<?>) results.get(1)) {
            namesOfDeletedAccounts.add((String) obj);
        }
        for (NetflixECSCredentials credential : accountsToAdd) {
            lazyLoadCredentialsRepository.save(credential.getName(), credential);
        }
        unscheduleAgents(namesOfDeletedAccounts, catsModule);
    }
    public static void unscheduleAgents(List<String> namesOfDeletedAccounts, CatsModule catsModule) {
        ProviderRegistry providerRegistry = catsModule.getProviderRegistry();
        EcsProvider ecsProvider = providerRegistry.getProviders().stream()
                .filter(provider -> provider instanceof EcsProvider)
                .map(provider -> (EcsProvider) provider)
                .findFirst().orElse(null);
        if (ecsProvider != null) {
            AgentScheduler agentScheduler = ecsProvider.getAgentScheduler();
            for (String accountName : namesOfDeletedAccounts) {
                List<Agent> agentsToRemove = getECSAgentsHandleAccount(ecsProvider, accountName);
                if (agentScheduler != null) {
                    agentsToRemove.forEach(agentScheduler::unschedule);
                }
            }
        }
    }
    // ECS agents do not implement AccountAware
    private static List<Agent> getECSAgentsHandleAccount(EcsProvider ecsProvider, String accountName) {
        List<Agent> handledAgents = new ArrayList<>();
        for (Agent agent : ecsProvider.getAgents()) {
            try {
                Field accountNameField = agent.getClass().getDeclaredField("accountName");
                accountNameField.setAccessible(true);
                String agentAccountName = (String) accountNameField.get(agent);
                if (accountName.equals(agentAccountName)) {
                    handledAgents.add(agent);
                }
            } catch (IllegalAccessException | NoSuchFieldException e) {
                String[] agentStringList = agent.getAgentType().split("/");
                String agentAccountName = Arrays.stream(agentStringList).limit(agentStringList.length - 2).collect(Collectors.joining());
                if (!agentAccountName.isEmpty() && accountName.equals(agentAccountName)) {
                    handledAgents.add(agent);
                }
            }
        }
        return handledAgents;
    }
}
