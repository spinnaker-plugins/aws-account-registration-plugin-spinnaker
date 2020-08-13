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

import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Data
public class AccountsStatus {
    public HashMap<String, CredentialsConfig.Account> ec2Accounts;
    public HashMap<String, ECSCredentialsConfig.Account> ecsAccounts;
    public List<String> deletedAccounts;

    private Long lastSyncTime;
    private Long lastAttemptedTIme;
    @Value("${accountProvision.url:http://localhost:8080}")
    private String remoteHostUrl;
    private final RestTemplate restTemplate;
    private final CredentialsConfig credentialsConfig;
    private final ECSCredentialsConfig ecsCredentialsConfig;

    @Autowired
    AccountsStatus(
            RestTemplate restTemplate, CredentialsConfig credentialsConfig, ECSCredentialsConfig ecsCredentialsConfig
    ) {
        this.restTemplate = restTemplate;
        this.credentialsConfig = credentialsConfig;
        this.ecsCredentialsConfig = ecsCredentialsConfig;
    }

    public List<CredentialsConfig.Account> getEC2AccountsAsList() {
        return new ArrayList<>(ec2Accounts.values());
    }

    public List<ECSCredentialsConfig.Account> getECSAccountsAsList() {
        return new ArrayList<>(ecsAccounts.values());
    }

    public Boolean getDesiredAccounts() {
        Response response = getResourceFromRemoteHost(remoteHostUrl);
        if (response == null) {
            return false;
        }
        response.convertCredentials();
        buildDesiredAccountConfig(response.getEc2Accounts(), response.getEcsAccounts(), response.getDeletedAccounts());
        return true;
    }

    private void buildDesiredAccountConfig(HashMap<String, CredentialsConfig.Account> ec2Accounts,
                                           HashMap<String, ECSCredentialsConfig.Account> ecsAccounts,
                                           List<String> deletedAccounts) {
        // Always use external source as credentials repo's correct state.
        // TODO: need a better way to check for account existence in current credentials repo.
        for (CredentialsConfig.Account currentAccount : credentialsConfig.getAccounts()) {
            for (CredentialsConfig.Account sourceAccount : ec2Accounts.values()) {
                if (currentAccount.getName().equals(sourceAccount.getName()) || deletedAccounts.contains(currentAccount.getName())) {
                    currentAccount = null;
                    break;
                }
            }
            if (currentAccount != null) {
                ec2Accounts.put(currentAccount.getName(), currentAccount);
            }
        }
        for (ECSCredentialsConfig.Account currentECSAccount : ecsCredentialsConfig.getAccounts()) {
            for (ECSCredentialsConfig.Account sourceAccount : ecsAccounts.values()) {
                if (currentECSAccount.getName().equals(sourceAccount.getName()) || deletedAccounts.contains(currentECSAccount.getAwsAccount())) {
                    currentECSAccount = null;
                    break;
                }
            }
            if (currentECSAccount != null) {
                ecsAccounts.put(currentECSAccount.getName(), currentECSAccount);
            }
        }
        this.setEc2Accounts(ec2Accounts);
        this.setEcsAccounts(ecsAccounts);
    }

    private Response getResourceFromRemoteHost(String url) {
        log.debug("Getting account information from {}.", url);
        if (lastSyncTime != null) {
            url = String.format("%s?after=%s", url, lastSyncTime.toString());
        }
        Response response = restTemplate.getForObject(url, Response.class);
        if (response == null || response.bookmark == null) {
            log.error("Response from remote host was null or did not receive a valid bookmark.");
            return null;
        }
        if (response.accounts == null) {
            lastSyncTime = response.bookmark;
            return null;
        }
        this.lastAttemptedTIme = response.bookmark;
        return response;
    }

    public void markSynced() {
        this.lastSyncTime = this.lastAttemptedTIme;
    }
}
