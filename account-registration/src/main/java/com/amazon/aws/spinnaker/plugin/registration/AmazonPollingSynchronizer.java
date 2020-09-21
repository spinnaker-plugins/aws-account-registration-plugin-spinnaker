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


@Component
@DependsOn("netflixAmazonCredentials")
@Slf4j
class AmazonPollingSynchronizer {
    private final AccountsStatus accountsStatus;
    // agent removal
    private final CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader;
    private final CredentialsConfig credentialsConfig;
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final DefaultAccountConfigurationProperties defaultAccountConfigurationProperties;
    private CatsModule catsModule;
    // ECS accounts
    private ECSCredentialsConfig ecsCredentialsConfig;
    private final ApplicationContext applicationContext;
    private EcsAccountMapper ecsAccountMapper;

    @Autowired
    AmazonPollingSynchronizer(
            AccountsStatus accountsStatus,
            CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
            CredentialsConfig credentialsConfig,
            LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
            DefaultAccountConfigurationProperties defaultAccountConfigurationProperties,
            ApplicationContext applicationContext
    ) {
        this.accountsStatus = accountsStatus;
        this.credentialsLoader = credentialsLoader;
        this.credentialsConfig = credentialsConfig;
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.defaultAccountConfigurationProperties = defaultAccountConfigurationProperties;
        this.applicationContext = applicationContext;
    }

    // circular dependency if not lazily loaded.
    @Autowired
    void setCatsModule(@Lazy CatsModule catsModule) {
        this.catsModule = catsModule;
    }

    @Autowired(required = false)
    void setECSCredentialsConfig(ECSCredentialsConfig ecsCredentialsConfig) {
        this.ecsCredentialsConfig = ecsCredentialsConfig;
    }

    @Scheduled(fixedDelayString = "${accountProvision.pullFrequencyInMilliSeconds:10000}")
    void schedule() throws Throwable {
        sync();
    }

    void sync() throws Throwable {
        log.debug("Checking remote host for account updates.");
        if (!accountsStatus.getDesiredAccounts()) {
            log.debug("Nothing to do.");
            return;
        }
        log.info("Total of {} EC2 accounts will be in credential repository.", accountsStatus.getEC2AccountsAsList().size());
        // Sync Amazon credentials in repo
        // CANNOT use defaultAmazonAccountsSynchronizer. Otherwise it will remove ECS accounts everytime there is a change
        // due to it passing NetflixAmazonCredentials, which includes NetflixAssumeRoleEcsCredentials, to
        // ProviderUtils.calculateAccountDeltas()
        credentialsConfig.setAccounts(accountsStatus.getEC2AccountsAsList());
        AmazonProviderUtils.AmazonAccountsSynchronizer(
                credentialsLoader,
                credentialsConfig,
                lazyLoadCredentialsRepository,
                defaultAccountConfigurationProperties,
                catsModule
        );
        // Sync ECS credentials in repo
        log.info("Total of {} ECS accounts will be in credential repository", accountsStatus.getECSAccountsAsList().size());
        // EcsAccountMapper is normally initialized and never refreshed. Need to refresh here.
        // Cannot autowire EcsAccountMapper for some reason. It returns every field wth null values.
        // Doing it this way is a problem.
        if (this.ecsAccountMapper == null) {
            this.ecsAccountMapper = (EcsAccountMapper) applicationContext.getBean("ecsAccountMapper");
        }
        ecsCredentialsConfig.setAccounts(accountsStatus.getECSAccountsAsList());
        EcsProviderUtils.synchronizeEcsAccounts(lazyLoadCredentialsRepository, credentialsLoader,
                ecsCredentialsConfig, catsModule);
        EcsProviderUtils.synchronizeEcsCredentialsMapper(ecsAccountMapper, lazyLoadCredentialsRepository);
        log.info("Finished syncing accounts. Setting last successful timestamp to {}", accountsStatus.getLastSyncTime());
        accountsStatus.markSynced();
        log.debug("Accounts synced successfully.");
    }
}
