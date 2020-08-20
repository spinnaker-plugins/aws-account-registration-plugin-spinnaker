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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Data
public class AccountsStatus {
    public HashMap<String, CredentialsConfig.Account> ec2Accounts;
    public HashMap<String, ECSCredentialsConfig.Account> ecsAccounts;
    public List<String> deletedAccounts;
    private String lastSyncTime;
    private String lastAttemptedTIme;
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
            RestTemplate restTemplate, CredentialsConfig credentialsConfig, ECSCredentialsConfig ecsCredentialsConfig,
            @Value("${accountProvision.url:http://localhost:8080}") String url
    ) {
        this.restTemplate = restTemplate;
        this.credentialsConfig = credentialsConfig;
        this.ecsCredentialsConfig = ecsCredentialsConfig;
        this.remoteHostUrl = buildSig4LibURL(url);
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
        String nextUrl = response.getPagination().getNextUrl();
        if (!"".equals(nextUrl)) {
            List<Account> accounts = response.getAccounts();
            while (nextUrl != null && !"".equals(nextUrl)) {
                String formattedURL = buildSig4LibURL(nextUrl);
                log.info("Calling next URL, {}", formattedURL);
                Response nextResponse = getResourceFromRemoteHost(formattedURL);
                if (nextResponse != null) {
                    accounts.addAll(nextResponse.getAccounts());
                    nextUrl = nextResponse.getPagination().getNextUrl();
                    continue;
                }
                nextUrl = null;
            }
            response.setAccounts(accounts);
        }
        if (response.getAccounts().isEmpty()) {
            return false;
        }
        log.info("Finished gathering accounts from remote host. Processing {} accounts.", response.getAccounts().size());
        log.debug(response.getAccounts().toString());
        String mostRecentTime = findMostRecentTime(response);
        if (mostRecentTime == null) {
            return false;
        }
        this.lastAttemptedTIme = mostRecentTime;
        response.convertCredentials();
        buildDesiredAccountConfig(response.getEc2Accounts(), response.getEcsAccounts(), response.getDeletedAccounts());
        return true;
    }

    private void buildDesiredAccountConfig(HashMap<String, CredentialsConfig.Account> ec2Accounts,
                                           HashMap<String, ECSCredentialsConfig.Account> ecsAccounts,
                                           List<String> deletedAccounts) {
        // Always use external source as credentials repo's correct state.
        // TODO: need a better way to check for account existence in current credentials repo.

        if (credentialsConfig.getAccounts() == null) {
            return;
        }
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
        log.debug("Accounts to be updated: {}", ec2Accounts);
        log.debug("ECS accounts to be updated: {}", ecsAccounts);
        this.setEc2Accounts(ec2Accounts);
        this.setEcsAccounts(ecsAccounts);
    }

    private Response getResourceFromRemoteHost(String url) {
        log.info("Getting account information from {}.", url);
        Response response;
        if (iamAuth) {
            response = getResourceFromApiGateway(url);
        } else {
            response = getResources(url);
        }

        if (response == null) {
            log.error("Response from remote host was invalid.");
            return null;
        }
        if (response.accounts == null || response.accounts.isEmpty()) {
            log.debug("No accounts returned from remote host.");
            return null;
        }
        return response;
    }

    public void markSynced() {
        this.lastSyncTime = this.lastAttemptedTIme;
    }

    private Response getResourceFromApiGateway(String url) {
        if (this.headerGenerator == null) {
            makeHeaderGenerator(url);
            if (this.headerGenerator == null) {
                return null;
            }
        }
        try {
            return callApiGateway(url);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.FORBIDDEN == e.getStatusCode()) {
                log.info("Received 403 from API Gateway. Retrying..");
                makeHeaderGenerator(url);
                if (this.headerGenerator == null) {
                    return null;
                }
                return callApiGateway(url);
            }
        }
        return null;
    }

    private void makeHeaderGenerator(String url) {
        log.debug("Generating AWS signature version 4 headers.");
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
            log.debug("Attempting to obtain AWS credentials from default chain.");
            awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        }
        this.headerGenerator = new HeaderGenerator(
                "execute-api", region, awsCredentialsProvider, uri
        );
    }

    private Response getResources(String url) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        if (lastSyncTime != null) {
            builder.queryParam("UpdatedAt.gt", lastSyncTime);
        }
        return restTemplate.getForObject(builder.toUriString(), Response.class);
    }

    private Response callApiGateway(String url) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        HashMap<String, String> queryStrings = new HashMap<>();
        if (lastSyncTime != null) {
            queryStrings.put("UpdatedAt.gt", lastSyncTime);
            builder.queryParam("UpdatedAt.gt", lastSyncTime);
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

    private String findMostRecentTime(Response response) {
        List<Instant> instants = new ArrayList();
        for (Account account : response.getAccounts()) {
            try {
                instants.add(Instant.parse(account.getUpdatedAt()));
            } catch (DateTimeParseException e) {
                log.error(String.format("Unable to parse date string, %s.", account.getUpdatedAt()));
            }
        }
        if (instants.isEmpty()) {
            log.debug("No valid timestamps found.");
            return null;
        }
        log.debug("Finding most recent timestamp, {}", instants);
        Instant oldest = Collections.max(instants);
        log.debug("Most recent timestamp is {}", oldest.toString());
        return oldest.toString();
    }

    private String buildSig4LibURL(String url) {
        log.debug("Given url: {}", url);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        UriComponents components = builder.build();
        String path = components.getPath();
        if (path != null && !path.endsWith("/")) {
            builder.replacePath(path + "/");
        }
        log.debug("Final url: {}", builder.toUriString());
        return builder.toUriString();
    }
}
