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
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseTest {
    @Test
    void TestGetAccountStatus() {
        HashMap<String, Account> accounts = new HashMap<>();
        accounts.put("test1", new Account("test1", "1", "role/role1",
                new ArrayList(Arrays.asList("us-west-2")), new ArrayList(Arrays.asList("ecs", "lambda", "ec2")), true));
        accounts.put("test2", new Account("test2", "2", "role/role2",
                new ArrayList(Arrays.asList("us-west-2")), new ArrayList<String>(), true));
        accounts.put("test3", new Account("test3", "3", "role/role3",
                new ArrayList(Arrays.asList("us-west-2")), new ArrayList<String>(Arrays.asList("lambda", "ec2")), true));
        accounts.put("test4", new Account("test4", "4", "role/role4",
                new ArrayList(Arrays.asList("us-west-2")), new ArrayList<String>(Arrays.asList("lambda")), false));

        Response response = new Response();
        List<Account> accountList = new ArrayList<>();
        accountList.addAll(accounts.values());
        response.setAccounts(accountList);
        AccountsStatus status = response.getAccountStatus();
        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            CredentialsConfig.Account ec2Account = status.getEc2Accounts().get(entry.getKey());
            assertNotNull(ec2Account);
            assertEquals(entry.getKey(), ec2Account.getName());
            assertEquals(entry.getValue().getAccountId(), ec2Account.getAccountId());
            assertEquals(entry.getValue().getAssumeRole(), ec2Account.getAssumeRole());
            assertSame(entry.getValue().getEnabled(), ec2Account.getEnabled());

            if (entry.getValue().getProviders().isEmpty()) {
                assertTrue(ec2Account.getLambdaEnabled());
                ECSCredentialsConfig.Account ecsAccount = status.getEcsAccounts().get(entry.getKey() + "-ecs");
                assertNotNull(ecsAccount);
                assertEquals(entry.getValue().getName(), ecsAccount.getAwsAccount());
            }

            if (entry.getValue().getProviders().contains("lambda")) {
                assertTrue(ec2Account.getLambdaEnabled());
            }
            if (entry.getValue().getProviders().contains("ecs")) {
                ECSCredentialsConfig.Account ecsAccount = status.getEcsAccounts().get(entry.getKey() + "-ecs");
                assertNotNull(ecsAccount);
                assertEquals(entry.getValue().getName(), ecsAccount.getAwsAccount());
            }
        }
    }
}
