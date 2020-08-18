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

import com.amazonaws.DefaultRequest;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.http.HttpMethodName;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.util.*;

public class headerGenerator {
    private String targetServiceName;
    private final AWSCredentialsProvider aWSCredentialsProvider;
    private final AWS4Signer aws4Signer;
    private final URI endpoint;

    public headerGenerator(String targetServiceName, String region,
                           AWSCredentialsProvider aWSCredentialsProvider, URI endpoint) {
        this.aWSCredentialsProvider = aWSCredentialsProvider;
        this.endpoint = endpoint;
        this.targetServiceName = targetServiceName;
        this.aws4Signer = new AWS4Signer(){{
            setServiceName(targetServiceName);
            setRegionName(region);
        }};
    }

    public TreeMap<String, String> generateHeaders(HttpMethodName method, String resourcePath, HashMap<String, String> params) {
        DefaultRequest request = new DefaultRequest(targetServiceName);
        request.setHttpMethod(method);
        request.setEndpoint(this.endpoint);
        request.setResourcePath(resourcePath);
        if (params != null) {
            HashMap<String, List<String>> newParams = new HashMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                newParams.put(entry.getKey(), new ArrayList<String>(Arrays.asList(entry.getValue())));
            }
            request.setParameters(newParams);
        }
        request.setHeaders(Collections.singletonMap("Content-type", "application/json"));
        aws4Signer.sign(request, aWSCredentialsProvider.getCredentials());

        TreeMap<String, String > map = (TreeMap<String, String>) request.getHeaders();
        return map;
    }

}
