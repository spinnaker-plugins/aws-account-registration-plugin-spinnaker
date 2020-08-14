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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AccountsStatusTest {

    @Test
    public void testGetDesiredAccounts() {
        CredentialsConfig.Account ec2Account = new CredentialsConfig.Account() {{
            setName("test1");
            setAccountId("1");
            setAssumeRole("role/role1");
            setRegions(new ArrayList(Arrays.asList(new CredentialsConfig.Region() {{
                setName("us-west-2");
            }})));
            setLambdaEnabled(false);
            setEnabled(true);
        }};
        CredentialsConfig.Account removeAccount = new CredentialsConfig.Account() {{
            setName("test9");
            setAccountId("9");
            setAssumeRole("role/role9");
            setRegions(new ArrayList(Arrays.asList(new CredentialsConfig.Region() {{
                setName("us-west-2");
            }})));
            setLambdaEnabled(true);
            setEnabled(true);
        }};
        CredentialsConfig credentialsConfig = new CredentialsConfig() {{
            setAccounts(new ArrayList<>(Arrays.asList(
                    new CredentialsConfig.Account() {{
                        setName("test1");
                        setAccountId("1");
                        setAssumeRole("role/role1");
                        setRegions(new ArrayList(Arrays.asList(new CredentialsConfig.Region() {{
                            setName("us-west-2");
                        }})));
                        setLambdaEnabled(false);
                        setEnabled(true);
                    }},
                    new CredentialsConfig.Account() {{
                        setName("test9");
                        setAccountId("9");
                        setAssumeRole("role/role9");
                        setRegions(new ArrayList(Arrays.asList(new CredentialsConfig.Region() {{
                            setName("us-west-2");
                        }})));
                        setLambdaEnabled(true);
                        setEnabled(true);
                    }}
            )));
        }};
        ECSCredentialsConfig ecsCredentialsConfig = new ECSCredentialsConfig() {{
            setAccounts(new ArrayList<>(Arrays.asList(new ECSCredentialsConfig.Account() {{
                setName("test9-ecs");
                setAwsAccount("test9");
            }})));
        }};

        List<Account> correctAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test1");
                    setAccountId("1");
                    setAssumeRole("role/role1-1");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setEnabled(true);
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                }},
                new Account() {{
                    setName("test9");
                    setAccountId("9");
                    setAssumeRole("role/role9");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setEnabled(true);
                    setProviders(new ArrayList(Arrays.asList("ec2")));
                    setStatus("SUSPENDED");
                }}
        ));

        Response response = new Response() {{
            setAccounts(correctAccounts);
            setBookmark(1234567890L);
        }};
        Response noBookmark = new Response() {{
            setAccounts(correctAccounts);
        }};
        RestTemplate mockRest = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRest.getForObject(Mockito.anyString(), Mockito.eq(Response.class)))
                .thenReturn(noBookmark);
        AccountsStatus status = new AccountsStatus(mockRest, credentialsConfig, ecsCredentialsConfig) {{
            setRemoteHostUrl("http://localhost:8080/hello");
        }};

        assertFalse(status.getDesiredAccounts());

        Mockito.when(mockRest.getForObject(Mockito.anyString(), Mockito.eq(Response.class)))
                .thenReturn(response);
        assertTrue(status.getDesiredAccounts());
        assertAll("Account should be overwriten by remote accounts",
                () -> assertEquals(status.getEc2Accounts().get("test1").getAssumeRole(), "role/role1-1"),
                () -> assertTrue(status.getEcsAccounts().containsKey("test1-ecs")),
                () -> assertTrue(status.getEc2Accounts().get("test1").getLambdaEnabled())
        );
        assertAll("Account should be removed",
                () -> assertFalse(status.getEc2Accounts().containsKey("test9")),
                () -> assertFalse(status.getEcsAccounts().containsKey("test9-ecs"))
        );
    }
}
