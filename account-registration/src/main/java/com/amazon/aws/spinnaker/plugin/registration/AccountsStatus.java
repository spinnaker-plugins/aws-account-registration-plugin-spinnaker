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

import com.amazon.aws.spinnaker.plugin.registration.auth.iam.HeaderGenerator;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
    @Value("${accountProvision.iamAuth:false}")
    private boolean iamAuth;
    @Value("${accountProvision.iamAuthRegion:us-west-2}")
    private String region;
    private final RestTemplate restTemplate;
    private final CredentialsConfig credentialsConfig;
    private final ECSCredentialsConfig ecsCredentialsConfig;
    private HeaderGenerator headerGenerator;

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

    public boolean getDesiredAccounts() {
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
        Response response;
        if (iamAuth) {
            response = getResourceFromApiGateway(url);
        } else {
            response = getResources(url);
        }

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

    private Response getResourceFromApiGateway(String url) {
        if (!url.endsWith("/")) {
            remoteHostUrl = String.format("%s/", url);
        }
        if (this.headerGenerator == null) {
            makeHeaderGenerator(url);
            if (this.headerGenerator == null) {
                return null;
            }
        }
        try {
            return callApiGateway();
        } catch (HttpClientErrorException e) {
            if (HttpStatus.FORBIDDEN == e.getStatusCode()) {
                makeHeaderGenerator(url);
                if (this.headerGenerator == null) {
                    return null;
                }
                return callApiGateway();
            }
        }
        return null;
    }

    private void makeHeaderGenerator(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        AWSCredentialsProvider awsCredentialsProvider;
        if (credentialsConfig.getAccessKeyId() != null && credentialsConfig.getSecretAccessKey() != null) {
            awsCredentialsProvider = new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(credentialsConfig.getAccessKeyId(), credentialsConfig.getSecretAccessKey())
            );
        } else {
            awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        }
        this.headerGenerator = new HeaderGenerator(
                "execute-api", region, awsCredentialsProvider, uri
        );
    }

    private Response getResources(String url) {
        if (lastSyncTime != null) {
            url = String.format("%s?after=%s", url, lastSyncTime.toString());
        }
        return restTemplate.getForObject(url, Response.class);
    }

    private Response callApiGateway() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(remoteHostUrl);
        HashMap<String, String> queryStrings = new HashMap<>();
        if (lastSyncTime != null) {
            queryStrings.put("after", lastSyncTime.toString());
            builder.queryParam("after", lastSyncTime.toString());
        }
        TreeMap<String, String> generatedHeaders = headerGenerator.generateHeaders(queryStrings);
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> entry : generatedHeaders.entrySet()) {
            headers.add(entry.getKey(), entry.getValue());
        }
        HttpEntity entity = new HttpEntity<>(headers);
        HttpEntity<Response> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                Response.class
        );
        return response.getBody();
    }

}
