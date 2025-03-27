package com.amazon.aws.spinnaker.plugin.registration

import spock.lang.Specification
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration

class AwsCredentialsDefinitionSourceSpec extends Specification {
    def accountsStatus = Mock(AccountsStatus)
    def accountsConfiguration = Mock(AccountsConfiguration)

    def 'should check remote'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)
        def mockAccount1 = Mock(AccountsConfiguration.Account)
        def mockAccount2 = Mock(AccountsConfiguration.Account)
        def originalAccounts = [mockAccount1]
        def updatedAccounts = [mockAccount1, mockAccount2]

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        (1.._) * accountsConfiguration.getAccounts() >> originalAccounts  
        1 * accountsStatus.getDesiredAccounts() >> true
        1 * accountsStatus.getEC2AccountsAsList() >> updatedAccounts
        definitions.size() == 2
    }

    def 'should not check remote'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)
        def mockAccount = Mock(AccountsConfiguration.Account)
        def originalAccounts = [mockAccount]

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        (1.._) * accountsConfiguration.getAccounts() >> originalAccounts  
        1 * accountsStatus.getDesiredAccounts() >> false
        0 * accountsStatus.getEC2AccountsAsList()
        definitions.size() == 1
    }
    
    def 'should handle null config accounts gracefully'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        (1.._) * accountsConfiguration.getAccounts() >> null  
        1 * accountsStatus.getDesiredAccounts() >> false
        0 * accountsStatus.getEC2AccountsAsList()
        definitions.size() == 0  
    }
    
    def 'should handle remote exceptions gracefully'() {
        given:
        def definitionSource = new AwsCredentialsDefinitionSource(accountsStatus, accountsConfiguration)
        def mockAccount = Mock(AccountsConfiguration.Account)
        def originalAccounts = [mockAccount]

        when:
        def definitions = definitionSource.getCredentialsDefinitions()

        then:
        (1.._) * accountsConfiguration.getAccounts() >> originalAccounts
        1 * accountsStatus.getDesiredAccounts() >> true
        1 * accountsStatus.getEC2AccountsAsList() >> { throw new RuntimeException("Simulated remote failure") }
        definitions.size() == 1  
    }
}
