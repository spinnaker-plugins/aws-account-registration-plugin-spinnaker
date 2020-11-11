package com.amazon.aws.spinnaker.plugin.registration;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;

import java.util.List;

public class EcsCredentialsDefinitionSource implements CredentialsDefinitionSource<ECSCredentialsConfig.Account> {
    private final AccountsStatus accountsStatus;
    private final ECSCredentialsConfig ecsCredentialsConfig;

    public EcsCredentialsDefinitionSource(AccountsStatus accountsStatus, ECSCredentialsConfig ecsCredentialsConfig) {
        this.accountsStatus = accountsStatus;
        this.ecsCredentialsConfig = ecsCredentialsConfig;
    }

    @Override
    public List<ECSCredentialsConfig.Account> getCredentialsDefinitions() {
        List<ECSCredentialsConfig.Account> remoteList = accountsStatus.getECSAccountsAsList();
        List<ECSCredentialsConfig.Account> ecsCredentialsDefinitions;
        if (!remoteList.isEmpty()) {
            ecsCredentialsDefinitions = remoteList;
        } else {
            ecsCredentialsDefinitions = ecsCredentialsConfig.getAccounts();
        }
        return ImmutableList.copyOf(ecsCredentialsDefinitions);
    }
}
