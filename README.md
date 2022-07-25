## Spinnaker Plugin for Dynamic Account Registration
![Build](https://github.com/awslabs/aws-account-registration-plugin-spinnaker/workflows/Build/badge.svg)

This plugin queries a remote endpoint for AWS account information in JSON format. Accounts are added and/or removed without restarting clouddriver service.

### What this plugin does
1. Periodically syncs with a configured remote host to update Spinnaker AWS and ECS accounts. Supports account addition, removal, and update. 
2. On-demand account loading. If a AWS account is not found in the local repository at the time of pipeline execution, 
the plugin will perform a sync with remote host to provide needed account (if found in remote host).
3. Supports IAM authentication when used with API Gateway. The Spinnaker managing account role must have the permission to invoke configured API gateway.   

### Requirements
1. Must be used with Spinnaker version 1.28 or higher.
2. Must enable [AWS support](https://docs.armory.io/docs/armory-admin/aws/add-aws-account/)
3. Must enable [Lambda support](https://kb.armory.io/s/article/AWS-Lambda-Custom-Webhook-Stages).
4. Must enable [ECS support](https://spinnaker.io/setup/install/providers/aws/aws-ecs/#clouddriver-yaml-properties)
5. Must have a HTTP endpoint which provides [JSON payload](#Expected-JSON-payload) when invoked with `GET` (Example available at `.github/integration-testing/mock-server/`)

### Expected JSON payload
This plugin expects the following JSON payload from the configured remote host, configured with the `url` property.


```json
{
  "SpinnakerAccounts": [
    {
      "AccountId": "12345678901",
      "SpinnakerAccountName": "test-3",
      "Regions": [
        "us-west-2"
      ],
      "SpinnakerStatus": "ACTIVE | SUSPENDED",
      "SpinnakerAssumeRole": "role/spinnakerManaged",
      "SpinnakerProviders": [
        "ecs", "lambda", "ec2"
      ],
      "UpdatedAt": "2020-08-27T16:52:59.026696+00:00"
    }
  ],
  "Pagination": {
    "NextUrl": "http://some/next/url"
  }
}
```

### Note
1. Plugin performs `GET` with query string field `UpdatedAt.gt=<TIME>` after the initial sync.
Expectation is that the remote host will return accounts that were updated after the specified time by the field.
This is done to avoid returning and processing all accounts every time sync occurs. 
2. The `UpdatedAt.gt` field value is determined using the most recent time value provided in the `UpdatedAt` JSON field.
E.g. if two accounts were retruned with timestamps `2020-08-27T16:52:59.026696+00:00` and `2030-12-27T16:52:59.026696+00:00`, 
next request will have a query string field `UpdatedAt.gt=2030-12-27T16:52:59.026696+00:00`. 
3. The `SpinnakerProviders` JSON field means the following:
    - If empty, AWS and ECS accounts are removed.
    - If only `ec2` is specified, Spinnaker AWS account is created.
    - If only `lambda` is specified, Spinnaker AWS account with Lambda support is created.
    - If `ecs` is specified, Spinnaker AWS and ECS accounts are created.
4. ECS accounts are named with corresponding AWS account's name with "-ecs" suffix. 
E.g. ECS account is named `account1-ecs` if its corresponding AWS account name is `account1`
5. If the `SpinnakerProviders` field is set to `SUSPENDED`,  AWS and ECS accounts are removed.
6. If the `NextUrl` field is present, plugin will perform a `GET` request against the URL specified by the field. Returned accounts are aggregated, then processed.
7. Failure paths are [available here:](doc/failure_paths.md)


### Usage
1. Add the following to `clouddriver.yml` in the necessary [profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles) to load plugin.
```yaml
spinnaker:
    extensibility:
      plugins:
        AWS.AccountRegistration:
          id: AWS.AccountRegistration
          enabled: true
          version: <<plugin release version>>
          extensions: {}
      repositories:
        awsAccountRegistrationPluginRepo:
          id: awsAccountRegistrationPluginRepo
          url: https://raw.githubusercontent.com/spinnaker-plugins/aws-account-registration-plugin-spinnaker/master/plugins.json

accountProvision:
  url: 'http://localhost:8080' # Remote host address. Query string is supported but must not include space characters.
  iamAuth: false # Enable IAM authentication for API Gateway.
  iamAuthRegion: 'us-west-2' # Specify which region API Gateway is deployed. Required if `iamAuth` is enabled.
  connectionTimeout: 2000 # How long to wait before initial connection timeouts
  readTimeout: 6000 # How long to wait for remote server to return results.
  maxBackoffTime: 3600000 # How long, in milli seconds, maximum backoff time should be.

credentials:
  poller:
    enabled: true
    types:
        aws:
          reloadFrequencyMs: 20000 # Specify how often in milliseconds credentials should be synced.
        ecs:
          reloadFrequencyMs: 20000 # Specify how often in milliseconds credentials should be synced.
```

### Manually Build and Load Plugin
1. Run `./gradlew releaseBundle` in the root of this project. 
2. The above command will create a zip file, `build/distributions/spinnaker-aws-account-registration*.zip`.
3. Copy the zip file to Clouddriver plugin directory. Defaults to `/opt/clouddriver/plugins`. This directory can be specified by the `plugins-root-path` configuration property.
4. Enable the plugin by placing the following in [Clouddriver profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles)


```yaml
spinnaker:
  extensibility:
    plugins-root-path: /opt/clouddriver/plugins # Specify plugin directory if necessary.
    plugins:
      AWS.AccountRegistration:
        enabled: true
    repositories: {}
    strict-plugin-loading: false
# Available Plugin configuration properties:
accountProvision:
  url: 'http://localhost:8080' # Remote host address. Query string is supported but must not include space characters.
  iamAuth: false # Enable IAM authentication for API Gateway.
  iamAuthRegion: 'us-west-2' # Specify which region API Gateway is deployed. Required if `iamAuth` is enabled.
  connectionTimeout: 2000 # How long to wait before initial connection timeouts
  readTimeout: 6000 # How long to wait for remote server to return results.
  maxBackoffTime: 3600000 # How long, in milli seconds, maximum backoff time should be.
  
credentials:
  poller:
    enabled: true
    types:
        aws:
          reloadFrequencyMs: 20000 # Specify how often in milliseconds credentials should be synced.
        ecs:
          reloadFrequencyMs: 20000 # Specify how often in milliseconds credentials should be synced.
```
### Developer guide
Developer guide for this plugin is [available here](doc/developer_guide.md): 
 
## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

