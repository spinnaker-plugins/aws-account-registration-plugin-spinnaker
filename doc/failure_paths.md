## Failure paths

1. Problems while retrieving information from remote host.
    1. Connection Refused:
        - Logs will indicate initial connection was refused. A log message will be printed out every configured seconds to sync 
        AND everytime clouddriver attempts to use credentials not in local credentials repository. 
        - Results in increased backoff time every time this error occurs.
    2. Connection timeout:
        - Logs will indicate initial connection timed out. This message will appear in logs delayed by configured 
        milliseconds (`connectionTimeout` property) because it takes that time to determine if connection timed out.
        - This generally indicates there is a problem with networking between clouddriver and remote host. e.g firewall.
        - Results in increased backoff time every time this error occurs.
    3. Read timeout:
        - Logs will indicate read timeout has occurred. It could take `readTimeout` + `connectionTimeout` milliseconds 
        since initial attempt for the log message to appear. 
        - This generally indicates the remote host was unable to process requested information in time. 
        - Results in increased backoff time every time this error occurs.
    4. IAM authentication against API Gateway:
        - Logs will indicate what problem was encountered. 
        - If received error code is 403, the plugin will re-attempt `GET` once after it re-generates required headers.
        - Typically, this issue is due to IAM and API gateway resource policy misconfiguration.
        - Results in increased backoff time every time this error occurs. 
        
2. Problems while processing retrieved account information: 
    1. Processing pagination:
        - If initial call to remote host is successful but call to pagination (`NextUrl` field) fails, the entire operation
        is considered a failure. That is, accounts returned by first call are not loaded.
        - Results in increased backoff time every time this error occurs.
    2. Processing accounts' timestamps:
        - If all timestamp fields in returned accounts cannot be parsed to valid timestamps, the entire operation is considered a failure.
        - Results in increased backoff time every time this error occurs. 
    3. Processing accounts:
        - If an account does not provide essential information(name, account number, assume role, and status), the account is not provisioned.
        - If an account does not provide a valid AWS region, the account is not provisioned.