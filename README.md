## Spinnaker Plugin for Dynamic Account Registration

### Usage
1. Run `./gradlew releaseBundle` in the root of this project. 
2. The above command will create a zip file, `build/distributions/spinnaker-aws-account-registration*.zip`.
3. Copy the zip file to Clouddriver plugin directory. Defaults to `/opt/clouddriver/plugins`. This directory can be 
specified by the `plugins-root-path` configuration property.
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
```

### Available configuration properties:
```yaml
accountProvision:
  url: 'http://localhost:8080' # Remote host address. 
  pullFrequencyInMilliSeconds: 10000 # How often this plugin should query the remote host.
  syncAgentFrequencyInMilliSeconds: 10000 # How often agent scheduler should run.
  iamAuth: false # Enable IAM authentication for API Gateway.
  iamAuthRegion: 'us-west-2' # Specify which region API Gateway is deployed. Required if `iamAuth` is enabled.
```

### Known issues
1. When creating lambda functions, on-demand cache update may fail. This seems to be a bug in caching agent in `clouddriver-lambda`.
This is fixed in [this PR](https://github.com/spinnaker/clouddriver/pull/4802). 

### Expected JSON payload
This plugin expects the following JSON payload.
```json
{
  "Accounts": [
    {
      "AccountId": "123",
      "AccountName": "account1",
      "Regions": [
        "us-west-2"
      ],
      "Status": "ACTIVE|SUSPENDED",
      "SpinnakerAssumeRole": "role/role1",
      "SpinnakerProviders": [
        "ECS", "Lambda"
      ],
      "SpinnakerEnabled": true,
      "UpdatedAt": "2020-08-17T15:17:48Z"
    }

  ],
  "Pagination": {
    "NextUrl": "localhost:8080/accounts/next"
  }

}
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

