Example API

```
openapi: 3.0.0
info:
  title: API
  description: REST endpoint that the plugin will call.  
  version: 0.0.1
servers:
  - url: http://localhost:8080
    description: mock server endpoint
paths:
  /hello:
    get:
      summary: Returns a list accounts.
      parameters:
        - name: UpdatedAt.gt
          in: query
          required: false # return all accounts if missing.
          schema:
            type: string 
            maximum: 1
          description: |
            When specified, returns accounts that were updated since specified time in this parameter.
      responses:
        '200': 
          description: A list of accounts 
          content:
            application/json:
              schema: 
                type: array
                items: 
                  type: string
---
components:
  schemas:
    response:
      properties:
        SpinnakerAccounts:
            type: [$ref: #/components/schemas/account]
        Pagination: $ref: #/components/schemas/pagination
    pagination:
      properties:
        NextUrl:
          type: string
    account:
      properties:
        AccountId:
          type: string
        SpinnakerAccountName:
          type: string
        Regions:
          type: [string]
        SpinnkaerStatus:
          type: string
        SpinnkaerAssumeRole:
          type: string
        SpinnakerProviders:
          type: [string]
        UpdatedAt:
          type: string
```