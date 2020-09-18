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
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
    private String remoteHostUrl;
    @Value("${accountProvision.iamAuth:false}")
    private boolean iamAuth;
    @Value("${accountProvision.iamAuthRegion:us-west-2}")
    private String region;
    private RestTemplate restTemplate;
    private final CredentialsConfig credentialsConfig;
    private ECSCredentialsConfig ecsCredentialsConfig;
    private HeaderGenerator headerGenerator;

    @Autowired
    AccountsStatus(
            CredentialsConfig credentialsConfig,
            @Value("${accountProvision.url:http://localhost:8080}") String url
    ) {
        this.credentialsConfig = credentialsConfig;
        this.remoteHostUrl = url;
        this.restTemplate = new RestTemplateBuilder().interceptors(new PlusEncoderInterceptor()).build();
    }

    @Autowired(required = false)
    void setECSCredentialsConfig(ECSCredentialsConfig ecsCredentialsConfig) {
        this.ecsCredentialsConfig = ecsCredentialsConfig;
    }

    public List<CredentialsConfig.Account> getEC2AccountsAsList() {
        return new ArrayList<>(ec2Accounts.values());
    }

    public List<ECSCredentialsConfig.Account> getECSAccountsAsList() {
        return new ArrayList<>(ecsAccounts.values());
    }

    public boolean getDesiredAccounts() {
        if (lastSyncTime != null) {
            log.info("Last time synced with remote host is: {}", lastSyncTime);
        }
        Response response = getResourceFromRemoteHost(remoteHostUrl);
        if (response == null) {
            return false;
        }
        String nextUrl = response.getPagination().getNextUrl();
        if (!"".equals(nextUrl)) {
            List<Account> accounts = response.getAccounts();
            while (nextUrl != null && !"".equals(nextUrl)) {
                log.info("Calling next URL, {}", nextUrl);
                Response nextResponse = getResourceFromRemoteHost(nextUrl);
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
            log.info("Returned response contained empty accounts.");
            return false;
        }
        log.info("Finished gathering accounts from remote host. Processing {} accounts.", response.getAccounts().size());
        log.debug(response.getAccounts().toString());
        String mostRecentTime = findMostRecentTime(response);
        if (mostRecentTime == null) {
            log.error("Failed to find most recent timestamp in payload.");
            return false;
        }
        log.debug("Setting last sync attempt time to {}", mostRecentTime);
        this.lastAttemptedTIme = mostRecentTime;
        if (response.convertCredentials()) {
            buildDesiredAccountConfig(response.getEc2Accounts(), response.getEcsAccounts(), response.getDeletedAccounts(),
                    response.getAccountsToCheck());
            return true;
        }
        log.info("No valid accounts to process.");
        return false;
    }

    private void buildDesiredAccountConfig(HashMap<String, CredentialsConfig.Account> ec2Accounts,
                                           HashMap<String, ECSCredentialsConfig.Account> ecsAccounts,
                                           List<String> deletedAccounts, List<String> accountsToCheck) {
        // Always use external source as credentials repo's correct state.
        if (credentialsConfig.getAccounts() == null) {
            log.error("Current configured accounts is null. Very likely this is a configuration issue.");
            return;
        }
        for (CredentialsConfig.Account currentAccount : credentialsConfig.getAccounts()) {
            for (CredentialsConfig.Account sourceAccount : ec2Accounts.values()) {
                if (currentAccount.getName().equals(sourceAccount.getName()) || deletedAccounts.contains(currentAccount.getName())) {
                    log.debug("Existing EC2 account, {}, will be updated with updated account information.", sourceAccount.getName());
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
                    log.debug("Existing ECS account, {}, will be updated with updated account information.", sourceAccount.getName());
                    currentECSAccount = null;
                    break;
                }
            }
            if (currentECSAccount != null) {
                ecsAccounts.put(currentECSAccount.getName(), currentECSAccount);
            }
        }
        for (String deletedAccount : deletedAccounts) {
            ec2Accounts.remove(deletedAccount);
            ecsAccounts.remove(deletedAccount + "-ecs");
        }
        for (String ecsAccountsToRemove : accountsToCheck) {
            String ecsAccountName = ecsAccountsToRemove + "-ecs";
            log.debug("ECS account, {}, will be removed.", ecsAccountName);
            ecsAccounts.remove(ecsAccountName);
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
        if (response.getAccounts() == null || response.getAccounts().isEmpty()) {
            log.info("No accounts returned from remote host.");
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
                log.error("Failed to generate resources required for AWS Signature V4 to authenticate with API Gateway.");
                return null;
            }
        }
        try {
            return callApiGateway(url);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.FORBIDDEN == e.getStatusCode()) {
                log.error(e.getMessage());
                log.info("Received 403 from API Gateway. Retrying..");
                makeHeaderGenerator(url);
                if (this.headerGenerator == null) {
                    return null;
                }
                return callApiGateway(url);
            }
            throw e;
        }
    }

    private void makeHeaderGenerator(String url) {
        log.debug("Generating AWS signature version 4 headers.");
        AWSCredentialsProvider awsCredentialsProvider;
        if (credentialsConfig.getAccessKeyId() != null && credentialsConfig.getSecretAccessKey() != null) {
            awsCredentialsProvider = new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(credentialsConfig.getAccessKeyId(), credentialsConfig.getSecretAccessKey())
            );
        } else {
            log.info("Attempting to obtain AWS credentials from default chain.");
            awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        }
        this.headerGenerator = new HeaderGenerator(
                "execute-api", region, awsCredentialsProvider, url
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
        HashMap<String, List<String>> queryStrings = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : builder.build().getQueryParams().entrySet()) {
            queryStrings.put(entry.getKey(), entry.getValue());
        }
        if (lastSyncTime != null) {
            queryStrings.put("UpdatedAt.gt", new ArrayList<String>(Collections.singletonList(lastSyncTime)));
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
        HashMap<Instant, String> map = new HashMap<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
        for (Account account : response.getAccounts()) {
            try {
                String updatedString = account.getUpdatedAt();
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(updatedString, timeFormatter);
                Instant instant = Instant.from(offsetDateTime);
                instants.add(instant);
                map.put(instant, updatedString);
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
        return map.get(oldest);
    }
}
