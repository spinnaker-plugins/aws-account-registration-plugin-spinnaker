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

import com.amazonaws.regions.Region;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import com.amazonaws.regions.RegionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Data
public class Response {

    Response() {
        this.accounts = new ArrayList<>();
        this.regions = new ArrayList<>();
        List<Region> awsRegions = RegionUtils.getRegions();
        for (Region awsRegion : awsRegions) {
            regions.add(awsRegion.getName());
        }
    }

    @JsonProperty("Accounts")
    List<Account> accounts;

    @JsonProperty("Pagination")
    private AccountPagination pagination;

    @JsonIgnore
    HashMap<String, CredentialsConfig.Account> ec2Accounts;
    @JsonIgnore
    HashMap<String, ECSCredentialsConfig.Account> ecsAccounts;
    @JsonIgnore
    List<String> deletedAccounts;
    @JsonIgnore
    List<String> regions;


    private ECSCredentialsConfig.Account makeECSAccount(Account account) {
        return new ECSCredentialsConfig.Account() {{
            setAwsAccount(account.getName());
            setName(account.getName() + "-ecs");
        }};
    }

    private CredentialsConfig.Account makeEC2Account(Account account) {
        List<CredentialsConfig.Region> regions = new ArrayList<>();
        for (String region : account.getRegions()) {
            CredentialsConfig.Region regionToAdd = new CredentialsConfig.Region();
            regionToAdd.setName(region);
            regions.add(regionToAdd);
        }
        CredentialsConfig.Account ec2Account = new CredentialsConfig.Account() {{
            setName(account.getName());
            setAccountId(account.getAccountId());
            setAssumeRole(account.getAssumeRole());
            setRegions(regions);
            setPermissions(account.getPermissions());
            setEnabled(true);
        }};
        if (!account.getAssumeRole().toLowerCase().startsWith("role/")) {
            ec2Account.setAssumeRole(String.format("role/%s", account.getAssumeRole()));
        }
        return ec2Account;
    }

    public boolean convertCredentials() {
        HashMap<String, CredentialsConfig.Account> ec2Accounts = new HashMap<>();
        HashMap<String, ECSCredentialsConfig.Account> ecsAccounts = new HashMap<>();
        List<String> deletedAccounts = new ArrayList<>();
        for (Account account : accounts) {
            log.trace(account.toString());
            if (!shouldConvert(account)) {
                continue;
            }
            String accountName = account.getName();
            if (ec2Accounts.get(accountName) != null) {
                continue;
            }
            if ("SUSPENDED".equals(account.getStatus()) || account.getProviders() == null || account.getProviders().isEmpty()) {
                log.debug("Account, {}, will be removed: {}", accountName, account);
                deletedAccounts.add(accountName);
                continue;
            }
            CredentialsConfig.Account ec2Account = makeEC2Account(account);
            ec2Account.setLambdaEnabled(false);
            for (String provider : account.getProviders()) {
                if ("lambda".equals(provider.toLowerCase())) {
                    log.debug("Enabling Lambda for {}", accountName);
                    ec2Account.setLambdaEnabled(true);
                    continue;
                }
                if ("ecs".equals(provider.toLowerCase())) {
                    log.debug("Enabling ECS for {}", accountName);
                    ECSCredentialsConfig.Account ecsAccount = makeECSAccount(account);
                    ecsAccounts.put(ecsAccount.getName(), ecsAccount);
                }
            }
            ec2Accounts.put(ec2Account.getName(), ec2Account);
        }
        log.debug("Converted AWS accounts {}", ec2Accounts);
        log.debug("Converted ECS accounts {}", ecsAccounts);
        log.debug("Accounts to be deleted {}", deletedAccounts);
        this.deletedAccounts = deletedAccounts;
        this.ec2Accounts = ec2Accounts;
        this.ecsAccounts = ecsAccounts;
        if (ec2Accounts.isEmpty() && ecsAccounts.isEmpty() && deletedAccounts.isEmpty()) {
            log.debug("No accounts to process.");
            return false;
        }
        return true;
    }

    private boolean shouldConvert(Account account) {
        for (String attributes : new ArrayList<>(Arrays.asList(
                account.getName(), account.getAccountId(), account.getAssumeRole(), account.getStatus()
        ))) {
            if (attributes == null || attributes.trim().isEmpty()) {
                log.error("Received account contained a field which is null or empty. Account: {}", account);
                return false;
            }
        }
        if (account.getRegions() == null || account.getRegions().isEmpty()) {
            log.error("Received account's region was null or empty. Account: {}", account);
            return false;
        }
        for (String regionInResponse : account.getRegions()) {
            if (!regions.contains(regionInResponse.trim()))  {
                log.error("Invalid region was specified. Region: {}", regionInResponse);
                return false;
            }
        }
        return true;
    }
}
