/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.amazon.aws.spinnaker.plugin.registration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
public class AmazonPollingSynchronizerRESTErrorHandler
    extends DefaultResponseErrorHandler {
    @Override
    protected void handleError(ClientHttpResponse response, HttpStatus statusCode) throws IOException {
        String statusText = response.getStatusText();
        HttpHeaders headers = response.getHeaders();
        byte[] body = getResponseBody(response);
        Charset charset = getCharset(response);
        String message = getErrorMessage(statusCode.value(), statusText, body, charset);

        switch (statusCode.series()) {
            case CLIENT_ERROR:
                log.error("Server responded with an error. Likely due to client configuration. {} : {}", statusCode, statusText);
                break;
            case SERVER_ERROR:
                log.error("Server responded with an error. {} : {}", statusCode, statusText);
                break;
            default:
                throw new UnknownHttpStatusCodeException(message, statusCode.value(), statusText, headers, body, charset);
        }
    }

    private String getErrorMessage(
            int rawStatusCode, String statusText, @Nullable byte[] responseBody, @Nullable Charset charset) {

        String preface = rawStatusCode + " " + statusText + ": ";
        if (ObjectUtils.isEmpty(responseBody)) {
            return preface + "[no body]";
        }

        charset = charset == null ? StandardCharsets.UTF_8 : charset;
        int maxChars = 200;

        if (responseBody.length < maxChars * 2) {
            return preface + "[" + new String(responseBody, charset) + "]";
        }

        try {
            Reader reader = new InputStreamReader(new ByteArrayInputStream(responseBody), charset);
            CharBuffer buffer = CharBuffer.allocate(maxChars);
            reader.read(buffer);
            reader.close();
            buffer.flip();
            return preface + "[" + buffer.toString() + "... (" + responseBody.length + " bytes)]";
        }
        catch (IOException ex) {
            // should never happen
            throw new IllegalStateException(ex);
        }
    }
}
