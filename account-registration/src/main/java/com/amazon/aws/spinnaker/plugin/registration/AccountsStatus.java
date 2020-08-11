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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class AccountsStatus {
    public HashMap<String, CredentialsConfig.Account> ec2Accounts;
    public HashMap<String, ECSCredentialsConfig.Account> ecsAccounts;
    public List<String> deletedAccounts;

    public List<CredentialsConfig.Account> getEC2AccountsAsList() {
        List<CredentialsConfig.Account> desiredEc2Accounts = new ArrayList<>();
        desiredEc2Accounts.addAll(ec2Accounts.values());
        return desiredEc2Accounts;
    }

    public List<ECSCredentialsConfig.Account> getECSAccountsAsList() {
        List<ECSCredentialsConfig.Account> desiredEcsAccounts = new ArrayList<>();
        desiredEcsAccounts.addAll(ecsAccounts.values());
        return desiredEcsAccounts;
    }
}
