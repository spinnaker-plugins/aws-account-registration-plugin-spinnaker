package com.amazon.aws.spinnaker.plugin.registration

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Specification
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler
import com.fasterxml.jackson.databind.ObjectMapper

class LazyLoadCredentialsRepositorySpec extends Specification{
    def loader = Mock(AbstractCredentialsLoader)
    def handler = Mock(CredentialsLifecycleHandler)

    def 'should not check remote repository'() {
        given:
        def repo = new LazyLoadCredentialsRepository(handler, loader)
        def credJson = [
                name: "test1",
                environment: "test1",
                accountType: "test1",
                accountId: "123456789012" + "test1",
                defaultKeyPair: 'default-keypair',
                regions: [[name: 'us-east-1', availabilityZones: ['us-east-1b', 'us-east-1c', 'us-east-1d']],
                          [name: 'us-west-1', availabilityZones: ["us-west-1a", "us-west-1b"]]],
        ]
        def cred = new ObjectMapper().convertValue(credJson, NetflixAmazonCredentials)
        repo.save(cred)

        when:
        def retrievedCred = repo.getOne("test1")

        then:
        retrievedCred.getName() == "test1"
    }

    def 'should check remote repository'() {
        given:
        def repo = new LazyLoadCredentialsRepository(handler, loader)
        when:
        def retrievedCred = repo.getOne("test1")

        then:
        retrievedCred == null
        1 * loader.load()
    }
}
