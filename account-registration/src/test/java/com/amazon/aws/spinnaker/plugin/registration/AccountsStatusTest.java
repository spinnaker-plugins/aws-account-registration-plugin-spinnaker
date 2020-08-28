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
import org.junit.jupiter.api.TestInstance;
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
    public void testGetDesiredAccounts() {
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
                    }},
                    new CredentialsConfig.Account() {{
                        setName("test20");
                        setAccountId("20");
                        setAssumeRole("role/role20");
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
            setAccounts(new ArrayList<>(Arrays.asList(new ECSCredentialsConfig.Account() {{
                setName("test20-ecs");
                setAwsAccount("test20");
            }})));
        }};

        List<Account> correctAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test1");
                    setAccountId("1");
                    setAssumeRole("role/role1-1");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-10T15:28:30.418433185Z");
                    setStatus("ACTIVE");
                }},
                new Account() {{
                    setName("test9");
                    setAccountId("9");
                    setAssumeRole("role/role9");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ec2")));
                    setStatus("SUSPENDED");
                    setUpdatedAt("2020-08-11T15:28:30.418433185Z");
                }},
                new Account() {{
                    setName("test20");
                    setAccountId("20");
                    setAssumeRole("role/role20");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ec2")));
                    setStatus("ACTIVE");
                    setUpdatedAt("2020-08-11T15:28:30.418433185Z");
                }}
        ));

        List<Account> nextAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test8");
                    setAccountId("8");
                    setAssumeRole("role/role1-8");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-12T15:28:30.418433185Z");
                    setStatus("ACTIVE");
                }}
        ));

        Response nullResponse = new Response() {{
        }};
        Response response = new Response() {{
            setAccounts(correctAccounts);
            setPagination(new AccountPagination() {{
                setNextUrl("http://localhost:8080/v/next");
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
        Mockito.when(mockRest.getForObject(Mockito.eq("http://localhost:8080/v/next"), Mockito.eq(Response.class)))
                .thenReturn(nextResponse);
        assertTrue(status.getDesiredAccounts());
        assertEquals("2020-08-12T15:28:30.418433185Z", status.getLastAttemptedTIme());
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
        assertAll("ECS account should be removed",
        () -> assertFalse(status.getEcsAccounts().containsKey("test20-ecs"))
        );

        AccountsStatus statusQueryString = new AccountsStatus(mockRest, credentialsConfig, ecsCredentialsConfig,
                "http://localhost:8080/hello?env=test");
        assertFalse(statusQueryString.getDesiredAccounts());

    }

    @Test
    public void TestAPIGateway() {
        RestTemplate mockRest = Mockito.mock(RestTemplate.class);
        CredentialsConfig cc = new CredentialsConfig() {{
            setAccessKeyId("access");
            setSecretAccessKey("secret");
        }};
        ECSCredentialsConfig ecsCredentialsConfig = new ECSCredentialsConfig() {{
            setAccounts(new ArrayList<>(Arrays.asList(new ECSCredentialsConfig.Account() {{
                setName("test9-ecs");
                setAwsAccount("test9");
            }})));
            setAccounts(new ArrayList<>(Arrays.asList(new ECSCredentialsConfig.Account() {{
                setName("test20-ecs");
                setAwsAccount("test20");
            }})));
        }};
        List<Account> correctAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test1");
                    setAccountId("1");
                    setAssumeRole("role/role1-1");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-10T15:28:30.418433185Z");
                    setStatus("ACTIVE");
                }}
        ));
        List<Account> nextAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test8");
                    setAccountId("8");
                    setAssumeRole("role/role1-8");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-12T15:28:30.418433185Z");
                    setStatus("ACTIVE");
                }}
        ));
        Response response = new Response() {{
            setAccounts(correctAccounts);
            setPagination(new AccountPagination() {{
                setNextUrl("");
            }});
        }};

        AccountsStatus statusAPIGateway = new AccountsStatus(mockRest, cc, ecsCredentialsConfig, "http://localhost:8080/apigateway?env=test") {{
            setIamAuth(true);
            setRegion("us-west-2");
        }};
        ResponseEntity<Response> responseEntity = new ResponseEntity<Response>(response, HttpStatus.ACCEPTED);

        Mockito.when(mockRest.exchange(Mockito.eq("http://localhost:8080/apigateway?env=test"),
                Mockito.eq(HttpMethod.GET), Mockito.any(), Mockito.eq(Response.class))).thenReturn(responseEntity);
        assertTrue(statusAPIGateway.getDesiredAccounts());
    }


    @Test
    public void TestMarkSynced() {
        AccountsStatus status = new AccountsStatus(null, null, null, "http://localhost/") {{
            setLastAttemptedTIme("now");
        }};
        status.markSynced();
        assertEquals("now", status.getLastSyncTime());
    }

    @Test
    public void TestGetEC2AccountsAsList() {
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
    public void TestGetECSAccountsAsList() {
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

    @Test
    public void TestExceptions() {
        CredentialsConfig credentialsConfig = new CredentialsConfig() {{
            setAccounts(new ArrayList());
        }};
        ECSCredentialsConfig ecsCredentialsConfig = new ECSCredentialsConfig() {{
            setAccounts(new ArrayList());
        }};

        List<Account> exceptionAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test1");
                    setAccountId("1");
                    setAssumeRole("role/role1-1");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-15T15:17:48Z");
                }},
                new Account() {{
                    setName("test9");
                    setAccountId("9");
                    setAssumeRole("role/role9");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ec2")));
                    setStatus("SUSPENDED");
                    setUpdatedAt("2020-08-14T15:17:48Z");
                }}
        ));

        Response exceptionResponse = new Response() {{
            setAccounts(exceptionAccounts);
            setPagination(new AccountPagination() {{
                setNextUrl("invalidURL");
            }});
        }};
        RestTemplate mockRest = Mockito.mock(RestTemplate.class);
        AccountsStatus exceptionStatus = new AccountsStatus(mockRest, credentialsConfig, ecsCredentialsConfig, "http://localhost:8080/hello/");
        Mockito.when(mockRest.getForObject(Mockito.anyString(), Mockito.eq(Response.class)))
                .thenReturn(exceptionResponse);
        assertThrows(IllegalArgumentException.class,
                () -> exceptionStatus.getDesiredAccounts());

    }

    @Test
    public void testAccountDeletion() {

        List<Account> deleteAccounts = new ArrayList<Account>(Arrays.asList(
                new Account() {{
                    setName("test1");
                    setAccountId("1");
                    setAssumeRole("role/role1-1");
                    setStatus("SUSPENDED");
                    setRegions(new ArrayList(Arrays.asList("us-west-2")));
                    setProviders(new ArrayList(Arrays.asList("ecs", "lambda", "ec2")));
                    setUpdatedAt("2020-08-15T15:17:48Z");
                }}
        ));

        Response deleteResponse = new Response() {{
            setAccounts(deleteAccounts);
            setPagination(new AccountPagination() {{
            }});
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
                setName("test1-ecs");
                setAwsAccount("test1");
            }})));
        }};

        RestTemplate mockRest = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRest.getForObject(Mockito.anyString(), Mockito.eq(Response.class)))
                .thenReturn(deleteResponse);

        AccountsStatus status = new AccountsStatus(mockRest, credentialsConfig, ecsCredentialsConfig, "http://localhost:8080/hello/");

        assertTrue(status.getDesiredAccounts());
        assertFalse(status.getEc2Accounts().containsKey("test1"));
        assertFalse(status.getEcsAccounts().containsKey("test1-ecs"));
    }
}
