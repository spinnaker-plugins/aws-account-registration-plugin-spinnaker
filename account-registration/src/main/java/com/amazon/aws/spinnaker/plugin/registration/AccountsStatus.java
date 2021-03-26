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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Data
public class AccountsStatus {
    public HashMap<String, CredentialsConfig.Account> ec2Accounts;
    public HashMap<String, ECSCredentialsConfig.Account> ecsAccounts;
    private String lastSyncTime;
    private String lastAttemptedTIme;
    private String remoteHostUrl;
    @Value("${accountProvision.iamAuth:false}")
    private boolean iamAuth;
    @Value("${accountProvision.iamAuthRegion:us-west-2}")
    private String region;
    @Value("${accountProvision.maxBackoffTime:3600000}")
    private long maxBackoffTime;
    private AtomicInteger retryCount = new AtomicInteger(0);
    private Instant nextTry;
    private RestTemplate restTemplate;
    private final CredentialsConfig credentialsConfig;
    private ECSCredentialsConfig ecsCredentialsConfig;
    private HeaderGenerator headerGenerator;
    private boolean initialSync;

    @Autowired
    AccountsStatus(
            CredentialsConfig credentialsConfig,
            @Value("${accountProvision.url:http://localhost:8080}") String url,
            @Value("${accountProvision.connectionTimeout:2000}") Long connectionTimeout,
            @Value("${accountProvision.readTimeout:6000}") Long readTimeout
    ) {
        this.credentialsConfig = credentialsConfig;
        this.remoteHostUrl = url;
        this.restTemplate = new RestTemplateBuilder()
                .interceptors(new PlusEncoderInterceptor())
                .setConnectTimeout(Duration.ofMillis(connectionTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
        this.ec2Accounts = new HashMap<>();
        this.ecsAccounts = new HashMap<>();
        this.initialSync = true;
    }

    @Autowired(required = false)
    void setECSCredentialsConfig(ECSCredentialsConfig ecsCredentialsConfig) {
        this.ecsCredentialsConfig = ecsCredentialsConfig;
    }

    public List<CredentialsConfig.Account> getEC2AccountsAsList() {
        return ImmutableList.copyOf(ec2Accounts.values());
    }

    public List<ECSCredentialsConfig.Account> getECSAccountsAsList() {
        return ImmutableList.copyOf(ecsAccounts.values());
    }

    public synchronized boolean getDesiredAccounts() {
        if (nextTry != null && Instant.now().isBefore(nextTry)) {
            log.debug("In backoff time. Will not attempt to retrieve accounts.");
            return false;
        }
        if (lastSyncTime != null) {
            log.info("Last time synced with remote host is: {}", lastSyncTime);
        } else {
            log.info("Last sync time is not set. Will perform a full sync.");
        }
        Response response;
        try {
            response = getResourceFromRemoteHost(remoteHostUrl);
        } catch (Exception e) {
            log.error("Could not get account information from remote host.", e);
            setBackoffTime();
            return false;
        }
        if (response == null) {
            setBackoffTime();
            return false;
        }
        if (response.getPagination() != null && !"".equals(response.getPagination().getNextUrl())) {
            String nextUrl = response.getPagination().getNextUrl();
            List<Account> accounts = response.getAccounts();
            while (nextUrl != null && !"".equals(nextUrl)) {
                log.info("Calling next URL, {}", nextUrl);
                Response nextResponse = getResourceFromRemoteHost(nextUrl);
                if (nextResponse != null) {
                    accounts.addAll(nextResponse.getAccounts());
                    if (nextResponse.getPagination() == null) {
                        nextUrl = null;
                        continue;
                    }
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
        log.info("Setting last sync attempt time to {}", mostRecentTime);
        this.lastAttemptedTIme = mostRecentTime;
        log.info("Converting {} accounts to Spinnaker account types.", response.getAccounts().size());
        if (response.convertCredentials(credentialsConfig)) {
            buildDesiredAccountConfig(response.getEc2Accounts(), response.getEcsAccounts(), response.getDeletedAccounts(),
                    response.getAccountsToCheck());
            markSynced();
            return true;
        }
        log.info("No valid accounts to process.");
        return false;
    }

    private void buildDesiredAccountConfig(HashMap<String, CredentialsConfig.Account> ec2AccountsFromRemote,
                                           HashMap<String, ECSCredentialsConfig.Account> ecsAccountsFromRemote,
                                           List<String> deletedAccounts, List<String> accountsToCheck) {
        // Always use external source as credentials repo's correct state.
        // CredentialsConfig should be considered on initial sync only since it contains accounts from local file only.
        if (credentialsConfig.getAccounts() == null) {
            log.error("Current configured accounts is null. Very likely this is a configuration issue.");
            return;
        }
        log.debug("Current configured EC2 accounts: {}", getEC2AccountsAsList().stream()
                .map(CredentialsConfig.Account::getName).collect(Collectors.toList()));
        log.debug("Current configured ECS accounts: {}", getECSAccountsAsList().stream()
                .map(ECSCredentialsConfig.Account::getName).collect(Collectors.toList()));
        if (initialSync) {
            log.debug("Initial sync. EC2 Accounts from file {}", credentialsConfig.getAccounts().stream()
                    .map(CredentialsConfig.Account::getName).collect(Collectors.toList()));
            log.debug("Initial sync. ECS Accounts from file {}", ecsCredentialsConfig.getAccounts().stream()
                    .map(ECSCredentialsConfig.Account::getName).collect(Collectors.toList()));
            resolveAccounts(ec2AccountsFromRemote, credentialsConfig.getAccounts(), deletedAccounts);
            resolveAccounts(ecsAccountsFromRemote, ecsCredentialsConfig.getAccounts(), deletedAccounts);
        } else {
            resolveAccounts(ec2AccountsFromRemote, getEC2AccountsAsList(), deletedAccounts);
            resolveAccounts(ecsAccountsFromRemote, getECSAccountsAsList(), deletedAccounts);
        }

        // Ensure ECS account, which did not contain "ecs" in providers field in payload but ec2 account is to be kept, are removed.
        for (String ecsAccountsToRemove : accountsToCheck) {
            String ecsAccountName = ecsAccountsToRemove + "-ecs";
            log.info("ECS account, {}, will be removed.", ecsAccountName);
            ecsAccountsFromRemote.remove(ecsAccountName);
        }
        log.debug("Accounts to be in credentials source: {}", ec2AccountsFromRemote.keySet());
        log.debug("ECS accounts to be in ECS credential source: {}", ecsAccountsFromRemote.keySet());
        log.info("EC2 accounts to be removed: {}. To be added {}", Sets.difference(ec2Accounts.keySet(), ec2AccountsFromRemote.keySet()), Sets.difference(ec2AccountsFromRemote.keySet(), ec2Accounts.keySet()));
        log.info("ECS accounts to be removed: {}. To be added {}", Sets.difference(ecsAccounts.keySet(), ecsAccountsFromRemote.keySet()), Sets.difference(ecsAccountsFromRemote.keySet(), ecsAccounts.keySet()));
        this.setEc2Accounts(ec2AccountsFromRemote);
        this.setEcsAccounts(ecsAccountsFromRemote);
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
            response.setAccounts(Collections.emptyList());
            return response;
        }
        log.info("Received a valid response from remote host.");
        return response;
    }

    public void markSynced() {
        this.retryCount.set(0);
        this.nextTry = null;
        this.lastSyncTime = this.lastAttemptedTIme;
        this.initialSync = false;
    }

    private Response getResourceFromApiGateway(String url) {
        if (this.headerGenerator == null) {
            makeHeaderGenerator(url);
            if (this.headerGenerator == null) {
                log.error("Failed to generate resources required for AWS Signature V4 to authenticate with API Gateway.");
                return null;
            }
        }
        return callApiGateway(url);
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
        int retry = 0;
        while (retry <= 1) {
            try {
                return doCallApiGateway(url);
            } catch (Exception e) {
                if (e instanceof HttpClientErrorException) {
                    HttpClientErrorException ex = (HttpClientErrorException) e;
                    if (HttpStatus.FORBIDDEN == ex.getStatusCode()) {
                        log.info("Received 403 from API Gateway: {}", e.getMessage());
                        makeHeaderGenerator(url);
                        if (this.headerGenerator == null) {
                            log.error("Failed to generate resources required for AWS Signature V4 to authenticate with API Gateway.");
                            break;
                        }
                        retry += 1;
                        continue;
                    }
                }
                log.error("Error encountered while calling remote host: {}", e.getMessage());
                break;
            }
        }
        return null;
    }

    private Response doCallApiGateway(String url) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        HashMap<String, List<String>> queryStrings = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : builder.build().getQueryParams().entrySet()) {
            queryStrings.put(entry.getKey(), entry.getValue());
        }
        if (lastSyncTime != null) {
            log.debug("Setting UpdatedAt.gt query string to {}", lastSyncTime);
            queryStrings.put("UpdatedAt.gt", new ArrayList<String>(Collections.singletonList(lastSyncTime)));
            builder.queryParam("UpdatedAt.gt", lastSyncTime);
        }
        TreeMap<String, String> generatedHeaders = headerGenerator.generateHeaders(queryStrings);
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> entry : generatedHeaders.entrySet()) {
            log.trace("Generated Auth header: {}", entry.getValue());
            headers.add(entry.getKey(), entry.getValue());
        }

        HttpEntity entity = new HttpEntity<>(headers);
        log.debug("calling API Gateway: {}", builder.toUriString());
        HttpEntity<Response> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                Response.class
        );
        return response.getBody();
    }

    private String findMostRecentTime(Response response) {
        List<Instant> instants = new ArrayList<>();
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

    private void setBackoffTime() {
        int count = retryCount.getAndIncrement();
        long waitTime = (long) Math.pow(2, count) * 1000L;
        if (waitTime > maxBackoffTime) {
            waitTime = maxBackoffTime;
        }
        Random random = new Random();
        long randWait = random.nextInt(10) * 100L;
        nextTry = Instant.now().plusMillis(waitTime - randWait);
        log.info("Next try: {}", nextTry.toString());
    }

    private <T extends CredentialsDefinition> void resolveAccounts(HashMap<String, T> changedAccounts,
                                    List<T> currentAccounts,
                                    List<String> deletedAccounts) {
        if (currentAccounts == null) {
            log.debug("ECS is not configured. Nothing to do.");
            return;
        }
        for (T currentAccount : currentAccounts) {
            if (currentAccount instanceof ECSCredentialsConfig.Account) {
                if (deletedAccounts.contains(((ECSCredentialsConfig.Account) currentAccount).getAwsAccount())) {
                    log.debug("ECS account \"{}\"'s corresponding EC2 account is in deleted account list. ECS account will be removed.", currentAccount.getName());
                    continue;
                } else if (changedAccounts.containsKey(currentAccount.getName())){
                    log.info("ECS account \"{}\" will be updated.", currentAccount.getName());
                    continue;
                }
            } else {
                if (deletedAccounts.contains(currentAccount.getName())) {
                    log.debug("EC2 account \"{}\" is in deleted account list and will be removed.", currentAccount.getName());
                    continue;
                } else if (changedAccounts.containsKey(currentAccount.getName())) {
                    log.info("EC2 account \"{}\" will be updated.", currentAccount.getName());
                    continue;
                }
            }
            changedAccounts.put(currentAccount.getName(), currentAccount);
        }
    }
}
