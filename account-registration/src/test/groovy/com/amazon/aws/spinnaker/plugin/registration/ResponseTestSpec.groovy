package com.amazon.aws.spinnaker.plugin.registration

import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import spock.lang.Specification

class ResponseTestSpec extends Specification {
    CredentialsConfig credentialsConfig = new CredentialsConfig(){{
        setDefaultSecurityGroups(["sg1"])
        setDefaultKeyPairTemplate("{{name}}-keypair")
        setDefaultAssumeRole("defaultAssumeRole")
    }}

    def 'it should return ec2 and ecs accounts'() {
        given:
        Response response = new Response() {{
            accounts = [
                new Account() {{
                    name = "test1"
                    setAccountId("1")
                    setAssumeRole("role/role1")
                    setStatus("ACTIVE")
                    setRegions(["us-WEST-2"])
                    setProviders(["ECS", "lAmbda", "ec2", "invalidProvider"])
                }}
            ]
        }}

        when:
        response.convertCredentials(credentialsConfig)

        then:
        response.getEc2Accounts().values().size() == 1
        def ec2Account = response.getEc2Accounts().values()[0]
        ec2Account.environment == "test1"
        ec2Account.accountType == "test1"
        ec2Account.getDefaultKeyPair() == "test1-keypair"
        ec2Account.getDefaultSecurityGroups().size() == 1
        ec2Account.getAssumeRole() == "role/role1"
        ec2Account.getLambdaEnabled()
        def ecsAccount = response.getEcsAccounts().values()[0]
        ecsAccount.getName() == "test1-ecs"
        ecsAccount.getAwsAccount() == "test1"
    }

    def 'it should return ec2 accounts only'() {
        given:
        Response response = new Response() {{
            accounts = [
                new Account() {{
                    name = "test1"
                    setAccountId("1")
                    setAssumeRole("role/role1")
                    setStatus("ACTIVE")
                    setRegions(["us-WEST-2"])
                    setProviders(["ec2", "invalidProvider"])
                }}
            ]
        }}

        when:
        response.convertCredentials(credentialsConfig)

        then:
        response.getEc2Accounts().values().size() == 1
        response.getEcsAccounts().values().size() == 0
        !response.getEc2Accounts().values()[0].getLambdaEnabled()
    }

    def 'it should remove ec2 and ecs accounts'() {
        given:
        Response response = new Response() {{
            accounts = [
                new Account() {{
                    name = "test1"
                    setAccountId("1")
                    setAssumeRole("role/role1")
                    setStatus("SUSPENDED")
                    setRegions(["us-WEST-2"])
                    setProviders(["ec2", "invalidProvider"])
                }},
                new Account() {{
                    name = "test2"
                    providers = []
                    accountId = "2"
                    assumeRole = "role/role2"
                    status = "ACTIVE"
                    regions = ['us-west-2']
                }}
            ]
        }}

        when:
        response.convertCredentials(credentialsConfig)

        then:
        response.getEc2Accounts().values().size() == 0
        response.getEcsAccounts().values().size() == 0
        response.getDeletedAccounts().size() == 2
    }

    def 'it should not process accounts with missing fields'() {
        given:
        Response response = new Response() {{
            accounts = [
                new Account() {{
                    name = "test1"
                }}
            ]
        }}

        when:
        response.convertCredentials(credentialsConfig)

        then:
        response.getEc2Accounts().values().size() == 0
    }

    def 'it should not process accounts with missing regions'() {
        given:
        Response response = new Response() {{
            accounts = [
                new Account() {{
                    name = "test2"
                    providers = []
                    accountId = "2"
                    assumeRole = "role/role2"
                    status = "ACTIVE"
                    regions = []
                }}
            ]
        }}

        when:
        response.convertCredentials(credentialsConfig)

        then:
        response.getEc2Accounts().values().size() == 0
    }

    def 'it should not process accounts with invalid regions'() {
        given:
        Response response = new Response() {{
            accounts = [
                new Account() {{
                    name = "test2"
                    providers = []
                    accountId = "2"
                    assumeRole = "role/role2"
                    status = "ACTIVE"
                    regions = ["invalid"]
                }}
            ]
        }}

        when:
        response.convertCredentials(credentialsConfig)

        then:
        response.getEc2Accounts().values().size() == 0
    }
}
