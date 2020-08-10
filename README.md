## Spinnaker Plugin for Dynamic Account Registration

Available configuration properties:
```yaml
account-provision:
  url: 'http://localhost:8080/hello' # Remote host address. 
  pullFrequencyInMilliSeconds: 10000 # How often this plugin should query the remote host.
  syncAgentFrequencyInMilliSeconds: 10000 # How often agent scheduler should run.
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

