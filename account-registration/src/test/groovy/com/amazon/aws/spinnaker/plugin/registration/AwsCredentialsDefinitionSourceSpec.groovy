package com.amazon.aws.spinnaker.plugin.registration

import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import spock.lang.Specification

class AwsCredentialsDefinitionSourceSpec extends Specification {
    def accountsStatus = Mock(AccountsStatus)
    def credentialsConfig = Mock(CredentialsConfig)

    def 'should check remote'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, credentialsConfig)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * credentialsConfig.getAccounts() >> [Mock(CredentialsConfig.Account)]
        1 * accountsStatus.getDesiredAccounts() >> true
        1 * accountsStatus.getEC2AccountsAsList() >> [Mock(CredentialsConfig.Account), Mock(CredentialsConfig.Account)]
        definitions.size() == 2
    }

    def 'should not check remote'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, credentialsConfig)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * credentialsConfig.getAccounts() >> [Mock(CredentialsConfig.Account)]
        1 * accountsStatus.getDesiredAccounts() >> false
        0 * accountsStatus.getEC2AccountsAsList() >> [Mock(CredentialsConfig.Account), Mock(CredentialsConfig.Account)]
        definitions.size() == 1
    }
}
