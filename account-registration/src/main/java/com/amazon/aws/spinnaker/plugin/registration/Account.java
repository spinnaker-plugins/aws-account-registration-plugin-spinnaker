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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Account {
    @JsonProperty("SpinnakerAccountName")
    private String name; // required. MUST be unique.
    @JsonProperty("AccountId")
    private String accountId;  // required.
    @JsonProperty("SpinnakerAssumeRole")
    private String assumeRole;  // required.
    @JsonProperty("Regions")
    private List<String> regions;  // required.
    @JsonProperty("SpinnakerProviders")
    private List<String> providers;  // required.
    @JsonProperty("SpinnakerStatus")
    private String status; // required.
    // Optional
    @JsonProperty("Permissions")
    private Permissions.Builder permissions;
    @JsonProperty("CreatedAt")
    private String createdAt;
    @JsonProperty("UpdatedAt")
    private String updatedAt;
    @JsonProperty("SpinnakerId")
    private String spinnakerId;
}
