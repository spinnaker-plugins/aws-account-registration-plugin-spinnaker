package com.amazon.aws.spinnaker.plugin.registration

import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import spock.lang.Specification

class EcsCredentialsDefinitionSourceSpec extends Specification {
    def accountsStatus = Mock(AccountsStatus)
    def accountsConfiguration = Mock(ECSCredentialsConfig)

    def 'should return definitions'() {
        given:
        def definitionSource = new EcsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        0 * accountsConfiguration.getAccounts() >> [Mock(ECSCredentialsConfig.Account)]
        0 * accountsStatus.getDesiredAccounts() >> true
        1 * accountsStatus.getECSAccountsAsList() >> [Mock(ECSCredentialsConfig.Account), Mock(ECSCredentialsConfig.Account)]
        definitions.size() == 2
    }

    def 'should return definitions when remote host is down on startup' () {
        given:
        def definitionSource = new EcsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * accountsConfiguration.getAccounts() >> [Mock(ECSCredentialsConfig.Account)]
        1 * accountsStatus.getECSAccountsAsList() >> []
        definitions.size() == 1
    }

    def 'should return definitions when remote host is down on startup but second call succeeds' () {
        given:
        def definitionSource = new EcsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)

        when:
        def definitionStartup = definitionSource.getCredentialsDefinitions()
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * accountsConfiguration.getAccounts() >> [Mock(ECSCredentialsConfig.Account)]
        2 * accountsStatus.getECSAccountsAsList() >>> [ [], [Mock(ECSCredentialsConfig.Account), Mock(ECSCredentialsConfig.Account)]]
        definitionStartup.size() == 1
        definitions.size() == 2
    }
}
