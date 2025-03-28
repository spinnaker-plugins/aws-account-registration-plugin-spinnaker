package com.amazon.aws.spinnaker.plugin.registration;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Collections;

@Slf4j
public class AwsCredentialsDefinitionSource implements CredentialsDefinitionSource<AccountsConfiguration.Account> {
    private final AccountsStatus accountsStatus;
    private final AccountsConfiguration accountsConfiguration;
    private List<AccountsConfiguration.Account> awsCredentialsDefinitions;

    @Autowired
    AwsCredentialsDefinitionSource(AccountsStatus accountsStatus, AccountsConfiguration accountsConfiguration) {
        this.accountsStatus = accountsStatus;
        this.accountsConfiguration = accountsConfiguration;
    }

    @Override
    public List<AccountsConfiguration.Account> getCredentialsDefinitions() {
        try {
            if (awsCredentialsDefinitions == null) {
                // Initialize with accounts from config if available
                if (accountsConfiguration != null && accountsConfiguration.getAccounts() != null) {
                    log.debug("Initializing with {} accounts from configuration", accountsConfiguration.getAccounts().size());
                    awsCredentialsDefinitions = accountsConfiguration.getAccounts();
                } else {
                    // Provide empty list rather than null
                    log.warn("No accounts available in configuration, using empty list");
                    awsCredentialsDefinitions = ImmutableList.of();
                }
            }
            
            // Try to get updated accounts but don't fail if it doesn't work
            try {
                log.debug("Attempting to retrieve accounts from remote source");
                if (accountsStatus.getDesiredAccounts()) {
                    List<AccountsConfiguration.Account> updatedAccounts = accountsStatus.getEC2AccountsAsList();
                    if (updatedAccounts != null && !updatedAccounts.isEmpty()) {
                        log.info("Successfully updated accounts from remote source, found {} accounts", updatedAccounts.size());
                        awsCredentialsDefinitions = updatedAccounts;
                    } else {
                        log.warn("Remote source returned empty accounts list");
                    }
                } else {
                    log.debug("No accounts retrieved from remote source");
                }
            } catch (Exception e) {
                log.error("Error retrieving accounts from remote source, continuing with existing accounts: {}", e.getMessage(), e);
                awsCredentialsDefinitions = awsCredentialsDefinitions == null ? 
                    ImmutableList.of() : awsCredentialsDefinitions;
            }
            
            return ImmutableList.copyOf(awsCredentialsDefinitions);
        } catch (Exception e) {
            // Return empty list instead of null to prevent NPEs
            log.error("Unexpected error in getCredentialsDefinitions, returning empty accounts list: {}", e.getMessage(), e);
            return ImmutableList.of();
        }
    }
}
