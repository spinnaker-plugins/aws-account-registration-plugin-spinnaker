package com.amazon.aws.spinnaker.plugin.registration.auth.iam

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import spock.lang.Specification

class HeaderGeneratorSpec extends Specification {

    def 'should generate expected header'() {
        given:
        AWSCredentials credentials = new BasicAWSCredentials("access", "secret")
        String expectedAuth = "AWS4-HMAC-SHA256 Credential=access/20200908/us-west-2/execute-api/aws4_request, SignedHeaders=content-type;host;x-amz-date, Signature=ea7e3e82a74af8bfc7d6412b332c3d2622e91e0855699f31819a67e5c23cdeeb"
        String expectedHost = "test.execute-api.us-west-2.amazonaws.com"
        HeaderGenerator headerGenerator = new HeaderGenerator(
            "execute-api", "us-west-2", new AWSStaticCredentialsProvider(credentials),
            "https://test.execute-api.us-west-2.amazonaws.com/test/accounts/")
        Calendar calender = new GregorianCalendar()
        calender.set(2020, 8, 8, 8, 8, 8)
        calender.setTimeZone(TimeZone.getTimeZone("UTC"))
        headerGenerator.aws4Signer.setOverrideDate(calender.getTime())
        HashMap<String, List<String>> queryStrings = new HashMap<>()
        queryStrings.put("after", new ArrayList<String>(Collections.singletonList("123")))

        when:
        TreeMap<String, String> headers = headerGenerator.generateHeaders(queryStrings)

        then:
        headers.get("Host") == expectedHost
        headers.get("Authorization") == expectedAuth
    }
}
