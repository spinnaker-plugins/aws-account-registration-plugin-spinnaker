## Spinnaker Plugin for Dynamic Account Registration

### Requirements
1. Must be used with Spinnaker version 1.22 or higher.
2. Must enable [Lambda support](https://kb.armory.io/s/article/AWS-Lambda-Custom-Webhook-Stages).
3. Must enable [ECS support](https://spinnaker.io/setup/install/providers/aws/aws-ecs/#clouddriver-yaml-properties)

### Usage
1. Add the following to `clouddriver.yml` in the necessary [profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles) to load plugin
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
          url: https://raw.githubusercontent.com/awslabs/aws-account-registration-plugin-spinnaker/master/plugins.json

accountProvision:
  url: <<account provider remote address>>
  pullFrequencyInMilliSeconds: <<plugin pull frequency>>
  syncAgentFrequencyInMilliSeconds: <<plugin agent scheduler frequency>>
  iamAuth: <<IAM Authentication for API Gateway>>
  iamAuthRegion: <<AWS Region for API Gateway. Required if iamAuth is true>>
```

### Manually Build and Load Plugin
1. Run `./gradlew releaseBundle` in the root of this project. 
2. The above command will create a zip file, `build/distributions/spinnaker-aws-account-registration*.zip`.
3. Copy the zip file to Clouddriver plugin directory. Defaults to `/opt/clouddriver/plugins`. This directory can be specified by the `plugins-root-path` configuration property.
4. Enable the plugin by placing the following in [Clouddriver profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles)


```yaml
spinnaker:
  extensibility:
    plugins-root-path: /opt/clouddriver/plugins
    plugins:
      AWS.AccountRegistration:
        enabled: true
    repositories: {}
    strict-plugin-loading: false
# Available Plugin configuration properties:
accountProvision:
  url: 'http://localhost:8080' # Remote host address. 
  pullFrequencyInMilliSeconds: 10000 # How often this plugin should query the remote host.
  syncAgentFrequencyInMilliSeconds: 10000 # How often agent scheduler should run.
  iamAuth: false # Enable IAM authentication for API Gateway.
  iamAuthRegion: 'us-west-2' # Specify which region API Gateway is deployed. Required if `iamAuth` is enabled.
```


### Expected JSON payload
This plugin expects the following JSON payload from the configured remote host, configured with the `url` property.


```json
{
  "SpinnakerAccounts": [
    {
    "AccountId": "259950518779",
    "SpinnakerAccountName": "mccloman-3",
    "Regions": [
      "us-west-2"
    ],
    "SpinnakerStatus": "ACTIVE",
    "SpinnakerAssumeRole": "role/spinnakerManaged",
    "SpinnakerProviders": [
      "ecs", "lambda", "ec2"
    ],
    "SpinnakerId": "spinnaker1",
    "CreatedAt": "2020-08-21T16:32:26.352337694Z",
    "UpdatedAt": "2020-08-21T16:32:26.352337694Z"
    }
  ],
  "Pagination": {
    "NextUrl": ""
  }
}
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

