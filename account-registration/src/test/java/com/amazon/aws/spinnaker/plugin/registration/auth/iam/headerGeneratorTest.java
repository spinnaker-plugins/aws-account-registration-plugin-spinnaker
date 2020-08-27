/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.amazon.aws.spinnaker.plugin.registration.auth.iam;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class headerGeneratorTest {

    @Test
    public void TestGenerateHeaders() {
        AWSCredentials credentials = new BasicAWSCredentials("access", "secret");
        String expectedAuth = "AWS4-HMAC-SHA256 Credential=access/20200908/us-west-2/execute-api/aws4_request, SignedHeaders=content-type;host;x-amz-date, Signature=ea7e3e82a74af8bfc7d6412b332c3d2622e91e0855699f31819a67e5c23cdeeb";
        String expectedHost = "test.execute-api.us-west-2.amazonaws.com";

        try {
            HeaderGenerator headerGenerator = new HeaderGenerator(
                    "execute-api", "us-west-2", new AWSStaticCredentialsProvider(credentials),
                    new URI("https://test.execute-api.us-west-2.amazonaws.com/test/accounts/"));
            // Need to override time to generate testable outputs.
            Calendar calender = new GregorianCalendar();
            calender.set(2020, 8, 8, 8, 8, 8);
            calender.setTimeZone(TimeZone.getTimeZone("UTC"));
            headerGenerator.aws4Signer.setOverrideDate(calender.getTime());
            HashMap<String, List<String>> queryStrings = new HashMap<>();
            queryStrings.put("after", new ArrayList<String>(Collections.singletonList("123")));
            TreeMap<String, String> headers = headerGenerator.generateHeaders(queryStrings);
            assertAll("Headers should return expected values.",
                    () -> assertEquals(headers.get("Host"), expectedHost),
                    () -> assertEquals(headers.get("Authorization"), expectedAuth)
            );
            assertEquals(headers.get("Host"), expectedHost);
            assertEquals(headers.get("Authorization"), expectedAuth);

            headerGenerator.setURI(new URI("https://test.execute-api.us-west-2.amazonaws.com/test/accounts"));
            TreeMap<String, String> headersWithoutSlash = headerGenerator.generateHeaders(queryStrings);
            assertAll("Headers should return expected values.",
                    () -> assertEquals(headers.get("Host"), expectedHost),
                    () -> assertEquals(headers.get("Authorization"), expectedAuth)
            );

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}