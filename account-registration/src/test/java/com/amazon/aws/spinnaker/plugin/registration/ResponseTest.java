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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseTest {
    @Test
    void TestGetAccountStatus() {
        HashMap<String, Account> receivedAccounts = new HashMap<>();
        receivedAccounts.put("test1", new Account() {{
            setName("test1");
            setAccountId("1");
            setAssumeRole("role/role1");
            setStatus("ACTIVE");
            setRegions(new ArrayList(Arrays.asList("us-west-2")));
            setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
        }});
        receivedAccounts.put("test2", new Account() {{
            setName("test2");
            setAccountId("2");
            setAssumeRole("role2");
            setStatus("ACTIVE");
            setRegions(new ArrayList(Arrays.asList("us-west-2")));
            setProviders(new ArrayList());
        }});
        receivedAccounts.put("test3", new Account() {{
            setName("test3");
            setAccountId("3");
            setAssumeRole("role/role3");
            setStatus("ACTIVE");
            setRegions(new ArrayList<>(Arrays.asList("us-west-2")));
            setProviders(new ArrayList(Arrays.asList("lambda", "ec2")));
        }});
        receivedAccounts.put("test4", new Account() {{
            setName("test4");
            setAccountId("4");
            setAssumeRole("role4");
            setStatus("ACTIVE");
            setRegions(new ArrayList<>(Arrays.asList("us-west-2")));
            setProviders(new ArrayList(Arrays.asList("ecs")));
        }});
        receivedAccounts.put("test5", new Account() {{
            setName("test5");
            setAccountId("5");
            setAssumeRole("role/role5");
            setRegions(new ArrayList<>(Arrays.asList("us-west-2")));
            setStatus("SUSPENDED");
            setProviders(new ArrayList(Arrays.asList("lambda", "ec2")));
        }});

        HashMap<String, Account> invalidAccounts = new HashMap<>();
        invalidAccounts.put("missingRequiredAttribute1", new Account() {{
            setName("missingRequiredAttribute1");
            setAccountId("5");
            setAssumeRole("role/role5");
            setRegions(new ArrayList<>(Arrays.asList("us-west-2")));
            setProviders(new ArrayList(Arrays.asList("lambda", "ec2")));
        }});
        invalidAccounts.put("missingRegion", new Account() {{
            setName("missingRegion");
            setAccountId("5");
            setAssumeRole("role/role5");
            setStatus("ACTIVE");
            setRegions(new ArrayList<>());
            setProviders(new ArrayList(Arrays.asList("lambda", "ec2")));
        }});
        invalidAccounts.put("invalidRegion", new Account() {{
            setName("invalidRegion");
            setAccountId("5");
            setAssumeRole("role/role5");
            setStatus("ACTIVE");
            setRegions(new ArrayList<>(Arrays.asList("invalidregion")));
            setProviders(new ArrayList(Arrays.asList("lambda", "ec2")));
        }});

        Response response = new Response(){{
            setAccounts(new ArrayList<>(receivedAccounts.values()));
        }};
        response.convertCredentials();
        for (Map.Entry<String, Account> entry : receivedAccounts.entrySet()) {
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
                    () -> assertEquals(sourceInfo.getAccountId(), ec2Account.getAccountId())
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
        Response invalidResponse = new Response(){{
           setAccounts(new ArrayList<>(invalidAccounts.values()));
        }};
        assertFalse(invalidResponse.convertCredentials());
        assertAll("Should return empty lists.",
                () -> assertTrue(invalidResponse.getEc2Accounts().isEmpty()),
                () -> assertTrue(invalidResponse.getEcsAccounts().isEmpty()),
                () -> assertTrue(invalidResponse.getDeletedAccounts().isEmpty())
        );
    }
}
