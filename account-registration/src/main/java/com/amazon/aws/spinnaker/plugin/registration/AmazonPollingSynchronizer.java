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

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.aws.security.DefaultAccountConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;


@Component
@DependsOn("netflixAmazonCredentials")
@Slf4j
class AmazonPollingSynchronizer {
    private Long lastSyncTime;
    private final RestTemplate restTemplate;
    private final AccountRegistrationProperties accountRegistrationProperties;
    // agent removal
    private final CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader;
    private final CredentialsConfig credentialsConfig;
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final DefaultAccountConfigurationProperties defaultAccountConfigurationProperties;
    private CatsModule catsModule;
    // ECS accounts
    private final ECSCredentialsConfig ecsCredentialsConfig;
    private final ApplicationContext applicationContext;
    private EcsAccountMapper ecsAccountMapper;

    @Autowired
    AmazonPollingSynchronizer(
            RestTemplate restTemplate,
            CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
            CredentialsConfig credentialsConfig,
            LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
            DefaultAccountConfigurationProperties defaultAccountConfigurationProperties,
            ECSCredentialsConfig ecsCredentialsConfig, ApplicationContext applicationContext,
            AccountRegistrationProperties accountRegistrationProperties
    ) {
        this.restTemplate = restTemplate;
        this.credentialsLoader = credentialsLoader;
        this.credentialsConfig = credentialsConfig;
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.defaultAccountConfigurationProperties = defaultAccountConfigurationProperties;
        this.ecsCredentialsConfig = ecsCredentialsConfig;
        this.applicationContext = applicationContext;
        this.accountRegistrationProperties = accountRegistrationProperties;
    }

    // circular dependency if not lazily loaded.
    @Autowired
    void setCatsModule(@Lazy CatsModule catsModule) {
        this.catsModule = catsModule;
    }

    @Scheduled(fixedDelayString = "${accountProvision.pullFrequencyInMilliSeconds:10000}")
    void schedule() {
        sync();
    }

    @PostConstruct
    void sync() {
        // Get accounts from remote
        Response response;
        response = getResourceFromRemoteHost(accountRegistrationProperties.getUrl());
        if (response == null) {
            return;
        }
        // convert to credentialsConfig from received response.
        AccountsStatus status = response.getAccountStatus();
        buildDesiredAccountConfig(status);
        // Sync Amazon credentials in repo
        // CANNOT use defaultAmazonAccountsSynchronizer. Otherwise it will remove ECS accounts everytime there is a change
        // due to it passing NetflixAmazonCredentials, which includes NetflixAssumeRoleEcsCredentials, to
        // ProviderUtils.calculateAccountDeltas()
        credentialsConfig.setAccounts(status.getEc2Accounts());
        AmazonProviderUtils.AmazonAccountsSynchronizer(
                credentialsLoader,
                credentialsConfig,
                lazyLoadCredentialsRepository,
                defaultAccountConfigurationProperties,
                catsModule
        );
        // Sync ECS credentials in repo
        ecsCredentialsConfig.setAccounts(status.getEcsAccounts());
        try {
            EcsProviderUtils.synchronizeEcsAccounts(lazyLoadCredentialsRepository, credentialsLoader,
                    ecsCredentialsConfig, catsModule);
        } catch (Throwable throwable) {
            log.error("Error encountered while adding ECS accounts: {}", throwable.getMessage());
            return;
        }
        // EcsAccountMapper is normally initialized and never refreshed. Need to refresh here.
        try {
//            EcsProviderUtils.synchronizeEcsCredentialsMapper(ecsAccountMapper, lazyLoadCredentialsRepository);
            // Cannot autowire EcsAccountMapper for some reason. It returns every field wth null values.
            // Doing it this way is a problem.
            if (this.ecsAccountMapper == null) {
                this.ecsAccountMapper = (EcsAccountMapper) applicationContext.getBean("ecsAccountMapper");
            }
            EcsProviderUtils.synchronizeEcsCredentialsMapper(ecsAccountMapper, lazyLoadCredentialsRepository);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            log.error("Error encountered while updating ECS credentials mapper: {}", e.getMessage());
            e.printStackTrace();
            return;
        } catch (BeansException e) {
            log.error("Error obtaining EcsAccountMapper bean from Spring context.");
            return;
        }

        lastSyncTime = response.bookmark;
    }

    private Response getResourceFromRemoteHost(String url) {
        log.debug("Getting account information from {}.", url );
        Response response;
        if (lastSyncTime != null) {
            url = String.format("%s?after=%s", url, lastSyncTime.toString());
        }
        response = restTemplate.getForObject(url, Response.class);
        if (response != null && response.bookmark == null) {
            log.error("Response from remote host did not contain a valid marker");
            return null;
        }
        if (response != null && response.accounts == null) {
            lastSyncTime = response.bookmark;
            return null;
        }
        return response;
    }

    private void buildDesiredAccountConfig(AccountsStatus status) {
        // Always use external source as credentials repo's correct state.
        // TODO: need a better way to check for account existence in current credentials repo.
        List<CredentialsConfig.Account> ec2Accounts = status.getEc2Accounts();
        List<ECSCredentialsConfig.Account> ecsAccounts = status.getEcsAccounts();
        for (CredentialsConfig.Account currentAccount : credentialsConfig.getAccounts()) {
            for (CredentialsConfig.Account sourceAccount : ec2Accounts) {
                if (currentAccount.getName().equals(sourceAccount.getName())) {
                    currentAccount = null;
                    break;
                }
            }
            if (currentAccount != null) {
                ec2Accounts.add(currentAccount);
            }
        }
        for (ECSCredentialsConfig.Account currentECSAccount : ecsCredentialsConfig.getAccounts()) {
            for (ECSCredentialsConfig.Account sourceAccount : ecsAccounts) {
                if (currentECSAccount.getName().equals(sourceAccount.getName())) {
                    currentECSAccount = null;
                    break;
                }
            }
            if (currentECSAccount != null) {
                ecsAccounts.add(currentECSAccount);
            }
        }
        status.setEc2Accounts(ec2Accounts);
        status.setEcsAccounts(ecsAccounts);
    }

}
