package com.amazon.aws.spinnaker.plugin.registration

import spock.lang.Specification
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration

class AwsCredentialsDefinitionSourceSpec extends Specification {
    def accountsStatus = Mock(AccountsStatus)
    def accountsConfiguration = Mock(AccountsConfiguration)

    def 'should check remote'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * accountsConfiguration.getAccounts() >> [Mock(AccountsConfiguration.Account)]
        1 * accountsStatus.getDesiredAccounts() >> true
        1 * accountsStatus.getEC2AccountsAsList() >> [Mock(AccountsConfiguration.Account), Mock(AccountsConfiguration.Account)]
        definitions.size() == 2
    }

    def 'should not check remote'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        1 * accountsConfiguration.getAccounts() >> [Mock(AccountsConfiguration.Account)]
        1 * accountsStatus.getDesiredAccounts() >> false
        0 * accountsStatus.getEC2AccountsAsList() >> [Mock(AccountsConfiguration.Account), Mock(AccountsConfiguration.Account)]
        definitions.size() == 1
    }
}
