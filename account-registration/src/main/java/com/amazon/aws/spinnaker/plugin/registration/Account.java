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

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import lombok.Data;

import java.util.List;

@Data
public class Account {
    private String name;
    private String accountId;
    private String assumeRole;
    private List<String> regions;
    private List<String> providers;
    private Boolean enabled;
    private List<String> Regions;

    private String type;
    private Long deletedAt;
    private Permissions.Builder permissions;
    private String environment;
    private String defaultKeyPair;
    private List<String> defaultSecurityGroups;

}