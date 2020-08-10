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

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.EcsAccountBuilder;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;

import java.lang.reflect.Field;
import java.util.*;
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
        ProviderUtils.unscheduleAndDeregisterAgents(namesOfDeletedAccounts, catsModule);
    }

//    private <T, L extends List<T>> List<T> doSafeCast(Object listObject,
//                                                      Class<T> type,
//                                                      Class<L> listClass) {
//        List<T> result = listClass.newInstance();
//
//        if (listObject instanceof List) {
//            List<?> list = (List<?>) listObject;
//
//            for (Object obj : list) {
//                if (type.isInstance(obj)) {
//                    result.add(type.cast(obj));
//                }
//            }
//        }
//        return result;
//    }
}
