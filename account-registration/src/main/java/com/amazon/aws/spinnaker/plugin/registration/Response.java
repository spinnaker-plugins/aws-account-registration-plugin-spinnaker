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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class Response {

    Response() {
        this.accounts = new ArrayList<>();
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
            setEnvironment(account.getEnvironment());
        }};
        if (!account.getAssumeRole().startsWith("role/")) {
            ec2Account.setAssumeRole(String.format("role/%s", account.getAssumeRole()));
        }
        return ec2Account;
    }

    public void convertCredentials() {
        HashMap<String, CredentialsConfig.Account> ec2Accounts = new HashMap<>();
        HashMap<String, ECSCredentialsConfig.Account> ecsAccounts = new HashMap<>();
        List<String> deletedAccounts = new ArrayList<>();
        for (Account account : accounts) {
            CredentialsConfig.Account exists = ec2Accounts.get(account.getName());
            if (exists != null) {
                continue;
            }
            if ("SUSPENDED".equals(account.getStatus()) || account.getProviders() == null || account.getProviders().isEmpty()) {
                deletedAccounts.add(account.getName());
                continue;
            }
            CredentialsConfig.Account ec2Account = makeEC2Account(account);
            ec2Account.setLambdaEnabled(false);
            if (account.getEnabled() != null) {
                ec2Account.setEnabled(account.getEnabled());
            }
            for (String provider : account.getProviders()) {
                if ("lambda".equals(provider)) {
                    ec2Account.setLambdaEnabled(true);
                    continue;
                }
                if ("ecs".equals(provider)) {
                    ECSCredentialsConfig.Account ecsAccount = makeECSAccount(account);
                    ecsAccounts.put(ecsAccount.getName(), ecsAccount);
                }
            }
            ec2Accounts.put(ec2Account.getName(), ec2Account);
        }
        this.deletedAccounts = deletedAccounts;
        this.ec2Accounts = ec2Accounts;
        this.ecsAccounts = ecsAccounts;
    }
}
