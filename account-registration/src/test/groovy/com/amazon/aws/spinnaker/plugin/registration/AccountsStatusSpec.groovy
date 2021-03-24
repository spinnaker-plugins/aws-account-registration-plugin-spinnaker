package com.amazon.aws.spinnaker.plugin.registration

import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import spock.lang.Specification

import java.time.Instant

class AccountsStatusSpec extends Specification {

    RestTemplate mockRest = Mock(RestTemplate)

    CredentialsConfig credentialsConfig = new CredentialsConfig() {{
        setAccounts([
            new CredentialsConfig.Account() {{
                name = "test1"
                accountId = "1"
                assumeRole = "role/role1"
                regions = [new CredentialsConfig.Region(){{name = "us-west-2"}}]
                lambdaEnabled = false
                enabled = true
            }},
            new CredentialsConfig.Account() {{
                name = "test9"
                accountId = "9"
                assumeRole = "role/role9"
                regions = [new CredentialsConfig.Region(){{name = "us-west-2"}}]
                lambdaEnabled = true
                enabled = true
            }},
            new CredentialsConfig.Account() {{
                name = "test20"
                accountId = "20"
                assumeRole = "role/role20"
                regions = [new CredentialsConfig.Region(){{name = "us-west-2"}}]
            }}
        ])
    }}

    ECSCredentialsConfig ecsConfig = new ECSCredentialsConfig() {{
        setAccounts([
            new ECSCredentialsConfig.Account() {{
                name = "test9-ecs"
                awsAccount = "test9"
            }},
            new ECSCredentialsConfig.Account() {{
                name = "test20-ecs"
                awsAccount = "test20"
            }}
        ])
    }}

    def "no accounts should be returned"() {
        given:
        AccountsStatus status = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
        }}

        when:
        def proceed = status.getDesiredAccounts()

        then:
        1 * mockRest.getForObject(_, _) >> new Response()
        !proceed
        status.getRetryCount().get() == 0
    }

    def "accounts should be overwritten"() {
        given:
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
            nextTry = Instant.ofEpochSecond(0L)
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test1"
                        accountId = "1"
                        assumeRole = "role/role1-1"
                        regions = ["us-west-2"]
                        providers = ["ecs", "lambda", "ec2"]
                        updatedAt = "2020-08-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        1 * mockRest.getForObject("http://localhost:8080/hello/", _) >> response
        proceed
        accountsStatus.getLastAttemptedTIme() == "2020-08-25T16:52:59.026696+00:00"
        accountsStatus.getEcsAccounts().containsKey("test1-ecs")
        accountsStatus.getEc2Accounts().get("test1").getAssumeRole() == "role/role1-1"
        accountsStatus.getEc2Accounts().get("test1").getLambdaEnabled()
        accountsStatus.nextTry == null
    }
    def "it should call next URL"() {
        given:
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test1"
                        accountId = "1"
                        assumeRole = "role/role1"
                        regions = ["us-west-2"]
                        providers = ["ecs", "lambda", "ec2"]
                        updatedAt = "2020-08-31T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
            pagination = new AccountPagination() {{
                nextUrl = "http://localhost:8080/v/next"
            }}
        }}

        Response responseNext = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test8"
                        accountId = "8"
                        assumeRole = "role/role8"
                        regions = ["us-west-2"]
                        providers = ["ecs", "lambda", "ec2"]
                        updatedAt = "2020-09-20T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        1 * mockRest.getForObject("http://localhost:8080/hello/", _) >> response
        1 * mockRest.getForObject("http://localhost:8080/v/next", _) >> responseNext
        proceed
        accountsStatus.getEc2Accounts().containsKey("test8")
        accountsStatus.getEcsAccounts().containsKey("test8-ecs")
        accountsStatus.getLastSyncTime() == "2020-09-20T16:52:59.026696+00:00"
    }

    def "it should remove empty provider accounts"() {
        given:
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test20"
                        accountId = "=20"
                        assumeRole = "role/role20"
                        regions = ["us-west-2"]
                        providers = []
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        1 * mockRest.getForObject("http://localhost:8080/hello/", _) >> response
        proceed
        accountsStatus.getLastAttemptedTIme() == "2020-10-25T16:52:59.026696+00:00"
        accountsStatus.getLastSyncTime() == "2020-10-25T16:52:59.026696+00:00"
        !accountsStatus.getEc2Accounts().containsKey("test20")
        !accountsStatus.getEcsAccounts().containsKey("test20-ecs")
        accountsStatus.getEC2AccountsAsList().size() == 2
    }

    def "it should remove one account only after initial sync"() {
        given:
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test2"
                        accountId = "2"
                        assumeRole = "role/role2"
                        regions = ["us-west-2", "us-west-1"]
                        providers = ["ecs", "ec2"]
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }},
                    new Account(){{
                        name = "test3"
                        accountId = "3"
                        assumeRole = "role/role3"
                        regions = ["us-east-2" ]
                        providers = ["ecs", "ec2"]
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}
        Response response2 = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test2"
                        accountId = "2"
                        assumeRole = "role/role22"
                        regions = ["us-west-2", "us-west-1"]
                        providers = []
                        updatedAt = "2020-10-26T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}

        when:
        accountsStatus.getDesiredAccounts()
        accountsStatus.getDesiredAccounts()

        then:
        2 * mockRest.getForObject(_, _) >>> [response, response2]
        accountsStatus.getLastSyncTime() == "2020-10-26T16:52:59.026696+00:00"
        !accountsStatus.getEc2Accounts().containsKey("test2")
        accountsStatus.getEc2Accounts().containsKey("test3")
        !accountsStatus.getEcsAccounts().containsKey("test2-ecs")
        accountsStatus.getEC2AccountsAsList().size() == 4
        accountsStatus.getECSAccountsAsList().size() == 3
    }

    def "it should update one account only after initial sync"() {
        given:
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test2"
                        accountId = "2"
                        assumeRole = "role/role2"
                        regions = ["us-west-2", "us-west-1"]
                        providers = ["ecs", "ec2"]
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }},
                    new Account(){{
                        name = "test3"
                        accountId = "3"
                        assumeRole = "role/role3"
                        regions = ["us-east-2" ]
                        providers = ["ecs", "ec2"]
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}
        Response response2 = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test2"
                        accountId = "2"
                        assumeRole = "role/role22"
                        regions = ["us-west-2", "us-west-1"]
                        providers = ["ec2"]
                        updatedAt = "2020-10-26T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}

        when:
        accountsStatus.getDesiredAccounts()
        accountsStatus.getDesiredAccounts()

        then:
        2 * mockRest.getForObject(_, _) >>> [response, response2]
        accountsStatus.getLastSyncTime() == "2020-10-26T16:52:59.026696+00:00"
        accountsStatus.getEc2Accounts().containsKey("test2")
        accountsStatus.getEc2Accounts().containsKey("test3")
        !accountsStatus.getEcsAccounts().containsKey("test2-ecs")
        accountsStatus.getEC2AccountsAsList().size() == 5
        accountsStatus.getECSAccountsAsList().size() == 3
    }

    def "it should add one account only after initial sync"() {
        given:
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello/", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test2"
                        accountId = "2"
                        assumeRole = "role/role2"
                        regions = ["us-west-2", "us-west-1"]
                        providers = ["ecs", "ec2"]
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}
        Response response2 = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test3"
                        accountId = "3"
                        assumeRole = "role/role3"
                        regions = ["us-west-2", "us-west-1"]
                        providers = ["ec2", "ecs"]
                        updatedAt = "2020-10-26T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}

        when:
        accountsStatus.getDesiredAccounts()
        accountsStatus.getDesiredAccounts()

        then:
        2 * mockRest.getForObject(_, _) >>> [response, response2]
        accountsStatus.getLastSyncTime() == "2020-10-26T16:52:59.026696+00:00"
        accountsStatus.getEc2Accounts().containsKey("test2")
        accountsStatus.getEc2Accounts().containsKey("test3")
        accountsStatus.getEcsAccounts().containsKey("test2-ecs")
        accountsStatus.getEcsAccounts().containsKey("test3-ecs")
        accountsStatus.getEC2AccountsAsList().size() == 5
        accountsStatus.getECSAccountsAsList().size() == 4
    }

    def "it should support API gateway and query strings"() {
        given:
        credentialsConfig.setAccessKeyId("access")
        credentialsConfig.setSecretAccessKey("secret")
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello?env=test&tag=test", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
            iamAuth = true
        }}
        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test20"
                        accountId = "=20"
                        assumeRole = "role/role20"
                        regions = ["us-west-2"]
                        providers = []
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}
        ResponseEntity<Response> responseEntity = new ResponseEntity<Response>(response, HttpStatus.ACCEPTED);

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        1 * mockRest.exchange("http://localhost:8080/hello?env=test&tag=test", HttpMethod.GET, _, _) >> responseEntity
        proceed
        accountsStatus.getHeaderGenerator() != null
    }

    def "it should set backoff"() {
        given:
        credentialsConfig.setAccessKeyId("access")
        credentialsConfig.setSecretAccessKey("secret")
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello?env=test", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
            iamAuth = true
        }}

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        1 * mockRest.exchange("http://localhost:8080/hello?env=test", HttpMethod.GET, _, _) >> { throw new RuntimeException("oh no")}
        !proceed
        accountsStatus.getHeaderGenerator() != null
        accountsStatus.getNextTry() != null
    }

    def "it should retry on 403"() {
        given:
        credentialsConfig.setAccessKeyId("access")
        credentialsConfig.setSecretAccessKey("secret")
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello?env=test", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
            iamAuth = true
        }}

        Response response = new Response(){{
            accounts = [
                    new Account(){{
                        name = "test20"
                        accountId = "=20"
                        assumeRole = "role/role20"
                        regions = ["us-west-2"]
                        providers = []
                        updatedAt = "2020-10-25T16:52:59.026696+00:00"
                        status = "ACTIVE"
                    }}
            ]
        }}
        ResponseEntity<Response> responseEntity = new ResponseEntity<Response>(response, HttpStatus.ACCEPTED)

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        2 * mockRest.exchange("http://localhost:8080/hello?env=test", HttpMethod.GET, _, _) >>
                { throw HttpClientErrorException.Unauthorized.create(HttpStatus.FORBIDDEN, null, null, null, null) } >>
                responseEntity

        proceed
        accountsStatus.getHeaderGenerator() != null
    }

    def "it should not retry more than once on 403"() {
        given:
        credentialsConfig.setAccessKeyId("access")
        credentialsConfig.setSecretAccessKey("secret")
        AccountsStatus accountsStatus = new AccountsStatus(credentialsConfig, "http://localhost:8080/hello?env=test", 0L, 0L) {{
            restTemplate = mockRest
            setECSCredentialsConfig(ecsConfig)
            iamAuth = true
        }}

        when:
        def proceed = accountsStatus.getDesiredAccounts()

        then:
        2 * mockRest.exchange("http://localhost:8080/hello?env=test", HttpMethod.GET, _, _) >>
                { throw HttpClientErrorException.Unauthorized.create(HttpStatus.FORBIDDEN, null, null, null, null) }

        !proceed
        accountsStatus.getHeaderGenerator() != null
    }
}
