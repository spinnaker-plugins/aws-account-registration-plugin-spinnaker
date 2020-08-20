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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AccountsStatusTest {

    @Test
    public void testGetDesiredAccounts() throws URISyntaxException {
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
                    setUpdatedAt("2020-08-15T15:17:48Z");
                }},
                new Account() {{
                    setName("test9");
                    setAccountId("9");
                    setAssumeRole("role/role9");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setEnabled(true);
                    setProviders(new ArrayList(Arrays.asList("ec2")));
                    setStatus("SUSPENDED");
                    setUpdatedAt("2020-08-14T15:17:48Z");
                }}
        ));

        List<Account> nextAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test8");
                    setAccountId("8");
                    setAssumeRole("role/role1-8");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setEnabled(true);
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-17T15:17:48Z");
                }}
        ));

        Response nullResponse = new Response() {{
        }};
        Response response = new Response() {{
            setAccounts(correctAccounts);
            setPagination(new AccountPagination() {{
                setNextUrl("http://localhost:8080/next");
            }});
        }};
        Response nextResponse = new Response() {{
            setAccounts(nextAccounts);
            setPagination(new AccountPagination() {{
                setNextUrl("");
            }});
        }};

        RestTemplate mockRest = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRest.getForObject(Mockito.anyString(), Mockito.eq(Response.class)))
                .thenReturn(nullResponse);

        AccountsStatus status = new AccountsStatus(mockRest, credentialsConfig, ecsCredentialsConfig, "http://localhost:8080/hello/");
        assertFalse(status.getDesiredAccounts());

        Mockito.when(mockRest.getForObject(Mockito.eq("http://localhost:8080/hello/"), Mockito.eq(Response.class)))
                .thenReturn(response);
        Mockito.when(mockRest.getForObject(Mockito.eq("http://localhost:8080/next/"), Mockito.eq(Response.class)))
                .thenReturn(nextResponse);
        assertTrue(status.getDesiredAccounts());
        assertEquals("2020-08-17T15:17:48Z", status.getLastAttemptedTIme());
        assertAll("Account should be overwritten by remote accounts",
                () -> assertEquals(status.getEc2Accounts().get("test1").getAssumeRole(), "role/role1-1"),
                () -> assertTrue(status.getEcsAccounts().containsKey("test1-ecs")),
                () -> assertTrue(status.getEc2Accounts().get("test1").getLambdaEnabled())
        );
        assertAll("Account should be removed",
                () -> assertFalse(status.getEc2Accounts().containsKey("test9")),
                () -> assertFalse(status.getEcsAccounts().containsKey("test9-ecs"))
        );
        assertAll("Account from next URL should be added",
                () -> assertTrue(status.getEc2Accounts().containsKey("test8")),
                () -> assertTrue(status.getEcsAccounts().containsKey("test8-ecs"))
        );


        Response emptyResponse = new Response() {{
            setAccounts(new ArrayList<>());
        }};
        Mockito.when(mockRest.getForObject(Mockito.matches("http://localhost:8080/hello/.*"), Mockito.eq(Response.class)))
                .thenReturn(emptyResponse);
        AccountsStatus statusQueryString = new AccountsStatus(mockRest, credentialsConfig, ecsCredentialsConfig,
                "http://localhost:8080/hello?env=test");
        statusQueryString.setLastSyncTime("2020-08-17T15:17:48Z");
        assertFalse(statusQueryString.getDesiredAccounts());

        CredentialsConfig cc = new CredentialsConfig() {{
            setAccessKeyId("access");
            setSecretAccessKey("secret");
        }};

        AccountsStatus statusAPIGateway = new AccountsStatus(mockRest, cc, ecsCredentialsConfig, "http://localhost:8080/apigateway?env=test") {{
            setIamAuth(true);
            setRegion("us-west-2");
            setLastSyncTime("2010-08-10T15:17:48Z");
        }};
        ResponseEntity<Response> responseEntity = new ResponseEntity<Response>(response, HttpStatus.ACCEPTED);
        ResponseEntity<Response> responseEntityNext = new ResponseEntity<Response>(nextResponse, HttpStatus.ACCEPTED);

        Mockito.when(mockRest.exchange(Mockito.matches("http://localhost:8080/apigateway/.*"),
                Mockito.eq(HttpMethod.GET), Mockito.any(), Mockito.eq(Response.class))).thenReturn(responseEntity);
        Mockito.when(mockRest.exchange(Mockito.matches("http://localhost:8080/next/.*"),
                Mockito.eq(HttpMethod.GET), Mockito.any(), Mockito.eq(Response.class))).thenReturn(responseEntityNext);

        assertTrue(statusAPIGateway.getDesiredAccounts());

    }

    @Test
    public void TestMarkSynced() throws URISyntaxException {
        AccountsStatus status = new AccountsStatus(null, null, null, "http://localhost/") {{
            setLastAttemptedTIme("now");
        }};
        status.markSynced();
        assertEquals("now", status.getLastSyncTime());
    }

    @Test
    public void TestGetEC2AccountsAsList() throws URISyntaxException {
        HashMap<String, CredentialsConfig.Account> map = new HashMap<>();
        map.put("test1", new CredentialsConfig.Account() {{
            setName("test1");
            setAccountId("1");
        }});
        map.put("test2", new CredentialsConfig.Account() {{
            setName("test2");
            setAccountId("2");
        }});
        AccountsStatus status = new AccountsStatus(null, null, null, "http://localhost/") {{
            setEc2Accounts(map);
        }};
        assertEquals(2, status.getEC2AccountsAsList().size());

    }

    @Test
    public void TestGetECSAccountsAsList() throws URISyntaxException {
        HashMap<String, CredentialsConfig.Account> map = new HashMap<>();
        map.put("test1", new CredentialsConfig.Account() {{
            setName("test1");
            setAccountId("1");
        }});
        map.put("test2", new CredentialsConfig.Account() {{
            setName("test2");
            setAccountId("2");
        }});
        HashMap<String, ECSCredentialsConfig.Account> mapECS = new HashMap<>();
        mapECS.put("test1-ecs", new ECSCredentialsConfig.Account() {{
            setName("test1-ecs");
            setAwsAccount("test-1");
        }});
        AccountsStatus status = new AccountsStatus(null, null, null, "http://localhost/") {{
            setEcsAccounts(mapECS);
            setEc2Accounts(map);
        }};
        assertEquals(1, status.getECSAccountsAsList().size());
    }
}
