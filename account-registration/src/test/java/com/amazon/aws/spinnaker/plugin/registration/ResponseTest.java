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
        accounts.put("test1", new Account() {{
            setName("test1");
            setAccountId("1");
            setAssumeRole("role/role1");
            setRegions(new ArrayList(Arrays.asList("us-west-2")));
            setEnabled(true);
            setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
        }});
        accounts.put("test2", new Account() {{
            setName("test2");
            setAccountId("2");
            setAssumeRole("role2");
            setRegions(new ArrayList(Arrays.asList("us-west-2")));
            setEnabled(true);
            setProviders(new ArrayList());
        }});
        accounts.put("test3", new Account() {{
            setName("test3");
            setAccountId("3");
            setAssumeRole("role/role3");
            setRegions(new ArrayList<>(Arrays.asList("lambda", "ec2")));
            setEnabled(true);
            setProviders(new ArrayList());
        }});
        accounts.put("test4", new Account() {{
            setName("test4");
            setAccountId("4");
            setAssumeRole("role/role4");
            setRegions(new ArrayList<>(Arrays.asList("lambda")));
            setEnabled(true);
            setProviders(new ArrayList());
        }});
        accounts.put("test5", new Account() {{
            setName("test5");
            setAccountId("5");
            setAssumeRole("role/role5");
            setRegions(new ArrayList<>(Arrays.asList("lambda")));
            setEnabled(true);
            setStatus("SUSPENDED");
            setProviders(new ArrayList());
        }});

        Response response = new Response();
        List<Account> accountList = new ArrayList<>();
        accountList.addAll(accounts.values());
        response.setAccounts(accountList);
        response.convertCredentials();
        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            Account sourceInfo = entry.getValue();
            String sourceAccountName = entry.getKey();
            if ("SUSPENDED".equals(sourceInfo.getStatus()) || sourceInfo.getProviders().isEmpty() || sourceInfo.getProviders() == null) {
                assertTrue(response.getDeletedAccounts().contains(sourceInfo.getName()));
                continue;
            }
            CredentialsConfig.Account ec2Account = response.getEc2Accounts().get(sourceAccountName);
            assertAll("Should return required account information",
                    () -> assertNotNull(ec2Account),
                    () -> assertEquals(sourceAccountName, ec2Account.getName()),
                    () -> assertEquals(sourceInfo.getAccountId(), ec2Account.getAccountId()),
                    () -> assertSame(sourceInfo.getEnabled(), ec2Account.getEnabled())
            );
            String assumeRoleString = sourceInfo.getAssumeRole();
            if (!assumeRoleString.startsWith("role/")) {
                sourceInfo.setAssumeRole(String.format("role/%s", assumeRoleString));
            }
            assertEquals(sourceInfo.getAssumeRole(), ec2Account.getAssumeRole());

            if (sourceInfo.getProviders().contains("lambda")) {
                assertTrue(ec2Account.getLambdaEnabled());
            }
            if (sourceInfo.getProviders().contains("ecs")) {
                ECSCredentialsConfig.Account ecsAccount = response.getEcsAccounts().get(sourceAccountName + "-ecs");
                assertNotNull(ecsAccount);
                assertEquals(sourceInfo.getName(), ecsAccount.getAwsAccount());
            }
        }
    }
}
