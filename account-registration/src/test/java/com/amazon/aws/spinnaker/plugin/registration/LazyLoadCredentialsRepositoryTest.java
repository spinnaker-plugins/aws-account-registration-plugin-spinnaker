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

package com.amazon.aws.spinnaker.plugin.registration;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LazyLoadCredentialsRepositoryTest {
    @Test
    void testGetOne() throws Throwable {
        AmazonPollingSynchronizer amazonPollingSynchronizer = Mockito.mock(AmazonPollingSynchronizer.class);
        LazyLoadCredentialsRepository lazyLoadCredentialsRepository = new LazyLoadCredentialsRepository() {{
            setSynchronizer(amazonPollingSynchronizer);
        }};

        NetflixAmazonCredentials cred1 = new NetflixAmazonCredentials(
                "test1", "test1", "test1", "1", "1", true, null, null, null, null, null, false, "1", false, "1", false, "1", true, "",
                false, false, false
        );
        lazyLoadCredentialsRepository.save("test1", cred1);

        AccountCredentials creds = lazyLoadCredentialsRepository.getOne("test2");
        assertNull(creds);
        assertEquals("test1", lazyLoadCredentialsRepository.getOne("test1").getName());

        Mockito.doAnswer(i -> {
                    NetflixAmazonCredentials cred2 = new NetflixAmazonCredentials(
                            "test2", "test1", "test1", "1", "1", true, null, null, null, null, null, false, "1", false, "1", false, "1", true, "",
                            false, false, false
                    );
                    lazyLoadCredentialsRepository.save("test2", cred2);
                    return null;
                }
        ).when(amazonPollingSynchronizer).sync();
        assertEquals("test2", lazyLoadCredentialsRepository.getOne("test2").getName());

    }
}
