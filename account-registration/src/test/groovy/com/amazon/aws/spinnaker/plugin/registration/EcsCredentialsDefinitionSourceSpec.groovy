package com.amazon.aws.spinnaker.plugin.registration

import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import spock.lang.Specification

class EcsCredentialsDefinitionSourceSpec extends Specification {
    def accountsStatus = Mock(AccountsStatus)
    def credentialsConfig = Mock(ECSCredentialsConfig)

    def 'should return definitions'() {
        given:
        def definitionSource = new EcsCredentialsDefinitionSource(accountsStatus, credentialsConfig)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * credentialsConfig.getAccounts() >> [Mock(ECSCredentialsConfig.Account)]
        0 * accountsStatus.getDesiredAccounts() >> true
        1 * accountsStatus.getECSAccountsAsList() >> [Mock(ECSCredentialsConfig.Account), Mock(ECSCredentialsConfig.Account)]
        definitions.size() == 2
    }
}
