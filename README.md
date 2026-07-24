Preferences Microservice
========================

## overview
Preferences microservice provides the ability for user to set preferences for receiving digital or paper communications from HMRC. User is authenticated via the TaxIds that is in authorisation token.

### Features

- Opt in to digital communications
- Opt out of digital communications
- Change language
- Verify email address
- Retrieve preferences
 ......

### useful links

- [Preferences overview](https://confluence.tools.tax.service.gov.uk/display/DCT/Paperless+Preferences)
- [Preferences flow ](https://confluence.tools.tax.service.gov.uk/display/DCT/Digital+Contact+Service+Interaction+Scenarios#DigitalContactServiceInteractionScenarios-UserSetsOwnPaperlessPreference)
- [Paperless journey](https://confluence.tools.tax.service.gov.uk/display/DCT/New+Architecture+diagrams+and+documents) 


### Developer Information

### SBT tasks
```shell
# Format the code
sbt fmt

# Clean, build test and integration test
sbt clean test it/test

# Run a coverage report
sbt clean coverage test coverageReport
```

### 1.Service Manager

```shell
sm2 -start DC_PREFERENCES_IT

# If you want to see what is running, use 
sm2 -s

# To stop them
sm2 --stop DC_PREFERENCES_IT
```


### 2. API
#### 2.1 List
| Path                                                           | Method | Description                                                                                      |
|----------------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------|
| **Retrieve Preferences**                                       
| ```/preferences```                                             | `GET`  | Retrieve preference, given a query string                                                        |
| ```/preferences/:entityId```                                   | `GET`  | Retrieve preference for a given entity id                                                        | 
| **Retrieve Preferences by email address**                      
| ```/preferences/email/:emailId```                              | `GET`  | DEPRECATED: See POST /preferences/find-by-email                                                  |
| ```/preferences/find-by-email```                               | `POST` | Retrieve preference for a given entity id                                                        | 
| **Retrieve verified email address**                            
| ```/preferences/verify/email-address```                        | `GET`  | DEPRECATED: See /preferences/verified-email                                                      |
| ```/preferences/:entityId/verified-email-address```            | `GET`  | Retrieve given an entity id                                                                      |
| ```/preferences/verified-email```                              | `GET`  | Retrieve email address, if available and valid for use in print suppression given a query string |
| **Retrieve email language**                                    
| ```/preferences/language/:emailId```                           | `GET`  | Retrieve langauge associated with the email, note email is encrypted                             |
| **Verify email**                                               
| ```/preferences/email```                                       | `PUT`  | Marks a customers email address as verified if a valid token is provided                         |
| **Update pending email**                                       
| ```/preferences/:entityId/pending-email```                     | `PUT`  | DEPRECATED: See PUT /preferences/pending-email                                                   |
| ```/preferences/pending-email```                               | `PUT`  | Change email or resend verification link                                                         | 
| **Admin only - Get list of events**                            
| ```/preferences-admin/events/:entityId```                      | `GET`  | Retrieve events for a given eventId for admin user only                                          |
| **Updated**                                                    
| ```/preferences/:entityId/updated```                           | `PUT`  | purpose? Update the print suppression status                                                     |
| **Optin, Optout, Change email language**                       
| ```/preferences/:entityId/terms-and-conditions```              | `POST` | DEPRECATED: See optin/optout/email-language instead of terms-and-conditions                      |
| ```/preferences/:entityId/optin```                             | `POST` | REMOVE ROUTE: no longer used                                                                     |
| ``````                            | `POST` | REMOVE ROUTE: no longer used                                                                     |
| ```/preferences/:entityId/email-language```                    | `POST` | REMOVE ROUTE: no longer used                                                                     |
| ```/preferences/optin```                                       | `POST` | Opt in to paperless                                                                              |
| ```/preferences/regime/optin```                                | `POST` | Optin - built to support ITSA                                                                    |
| ```/preferences/optout```                                      | `POST` | Opt out of paperless                                                                             |
| ```/preferences/regime/optout```                               | `POST` | Optout - built to support ITSA                                                                   |
| ```/preferences/email-language```                              | `POST` | Change email language                                                                            |
| ```/preferences/regime/email-language```                       | `POST` | Change email language - built to support ITSA                                                    |
| **Admin only - optin/optout/email-address**                      
| ```/preferences-admin/:entityId/terms-and-conditions```        | `POST` | DEPRECATED - used for opting out                                                                 |
| ```/preferences-admin/:entityId/optout```                      | `POST` | DEPRECATED - used for opting out                                                                 |
| ```/preferences-admin/optout```                                | `POST` | Change email language - built to support ITSA                                                    |
| **Process Bounced Email**                                      
| ```/preferences/email/bounce```                                | `POST` | Process bounced emails                                                                           |
| **Enrolment**                                                  
| ```/preferences/:entityId/unset-de-enrolment```                | `PUT`  | DEPRECATED: See /preferences/unset-de-enrolment                                                  |
| ```/preferences/mark-for-de-enrolment```                       | `PUT`  |                                                                                                  |
| ```/preferences/unset-de-enrolment```                          | `PUT`  |                                                                                                  |


### 2.2 Endpoints

#### 2.2.1 Retrieve Preferences
> #### 2.2.1.1 ```GET /preferences``` - given a query string  
> If `taxRegime` is present, `taxId` must also be present.  
Resolve flag indicates that the resolver will attempt to resolve multiple matching entities for this preference.
>
> | Parameter    | Type     | Description                                     |
> |--------------|----------|-------------------------------------------------|
> | `taxRegime`  | String   | Type of the tax ID, one of `paye`, `sa`, `itsa` |
> | `taxId`      | String   | Tax ID for specific user e.g `AB112233A`        |
> | `resolve`    | Boolean  | Entity should be resolved `true` or `false`     


> #### 2.2.1.2 ```GET /preferences/:entityId``` - given path based entity id
> Returns the preference of a user with entityId.
> Responds with status:

* `200` with a response body if a matching preference found
* `404` if a preference could not be found

Example response without status:

```json
{
  "termsAndConditions": {
    "generic":{
      "accepted":true,
      "updatedAt":1521110510782
    }
  },
  "email": {
    "email":"36c6ba82-97dc-4619-a7fb-67d534df1e5c@TEST.com",  
    "isVerified":true,
    "hasBounces":false,
    "mailboxFull":false,
    "status":"verified",
    "language":"en"
  },
  "digital":true,
  "entityId":"e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
}
```

All possible responses with status can be found in resources [status](test/resources/status) folder.

### 2.2.2 Retrieve list of preferences by email address
> #### 2.2.2.1 ```GET /preferences/email/:emailId```
> DEPRECATED: See `POST /preferences/find-by-email`            

> #### 2.2.2.2 ```POST /preferences/find-by-email```
> Returns the list of preferences which are associated with an Email Id.

Responds with status:
* 200 with a response body if one or more matching preference(s) were found
* 404 if a preference could not be found
  Example response:

```json
[
  {
    "termsAndConditions":{
      "generic":{
         "accepted":true,
         "updatedAt":1521110510782
      }
    },
    "email":{
      "email":"36c6ba82-97dc-4619-a7fb-67d534df1e5c@TEST.com",  
      "isVerified":true,
      "hasBounces":false,
      "mailboxFull":false,
      "status":"verified",
      "language": "en"
    },
    "digital":true,
    "entityId":"e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
  }
]
```



### 2.2.3 Retrieve verified email address                            
> #### ```GET /preferences/verify/email-address```
> DEPRECATED: See `/preferences/verified-email`

> #### ```GET /preferences/:entityId/verified-email-address```
> Retrieve given an entity id

> #### ```GET /preferences/verified-email```
> Retrieve email address, if available and valid for use in print suppression given a query string

Responds with status `200` with response body:

```json
{
  "email":"EXAMPLE@TEST.com"
}
```
when the user is opted-in and has a healthy verified email address.
Otherwise responds with the status `404` with response body:

```json
{
  "reason":"<reason-code>"
}
```

where `<reason-code>` is:

* `EMAIL_ADDRESS_NOT_VERIFIED` the user's email is bounced or pending.
* `NOT_OPTED_IN` the user is not opted in.
* `PREFERENCES_NOT_FOUND` the user has never set a preference.
* `OTHER_EXCEPTION` none of the above conditions have been met.


### Admin API

| Path                                              | Supported Methods | Description                                                                                                                                                                                   |  
| ------------------------------------------------- | ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/preferences-admin/remove-bounces`               | POST              | ??? [More...](#post-preferences-adminremove-bounces)                                                                                      |

### POST /preferences-admin/remove-bounces

### Example request - list of entityIds to remove the email bounce from:

```json
{
  [ "1234567890", "2345678901" ]
}
```


### 2.2.4 Retrieve email language                                    
> #### ```GET /preferences/language/:emailId```
> Retrieve langauge associated with the email, note email is encrypted


### 2.2.5 Verify email                                               
> #### ```PUT /preferences/email```
> Marks a customers email address as verified if a valid token is provided.

Example request:

```json
{
  "token":"1234567890987654"
}
```

Responds with status:

* `204` if the preference matching the token was successfully marked as verified.
* `410` if the token provided has expired
* `409` if the token has already been used to verify the current email address and no change of email is in progress
* `400` if the token is for an old email address or is otherwise invalid

Note: If the email is verified a welcome message is sent to the inbox relevant inbox for SAUTR, NINO and ITSA users.

### 2.2.6 Update pending email                                       
> #### 2.2.6.1 ```PUT /preferences/:entityId/pending-email```
> DEPRECATED: See `PUT /preferences/pending-email`

> #### 2.2.6.2 ```PUT /preferences/pending-email```
> Change email or resend verification link
> Sets the pending email address.
> 
> - If the body contains the same email, address that is already pending or verified, then this is 'resend' and the verification link will be sent to the address.  
> - If the body contains a different email, then this is 'change of address' and a change mail will be sent to the old address (if verified) and the new address.

This will result in the  

Responds with status:

* `200` if successfully updated
* `404` if a preference could not be found
* `409` if a preference exists but is in an illegal state (i.e. - neither pending nor verified email available)

Example request :

```json
{
  "email":"EXAMPLE@TEST.com"
}
```


### 2.2.7 Admin only - Get list of events                            
> #### ```GET /preferences-admin/events/:entityId```
> Retrieve events for a given eventId for admin user only

### 2.2.8 Updated                                                    
> #### ```PUT /preferences/:entityId/updated```
> purpose? Update the print suppression status
Update the print suppression status of the preference for the given entityId
Responds with status:

* `204` if work item status updated successfully
* `404` if a preference could not be found

Example request :

```json
{
  "entityId":"Entity identifier"
}
```


### 2.2.9 Optin, Optout, Change email language                       
> #### 2.2.9.1 ```POST /preferences/:entityId/terms-and-conditions```
> DEPRECATED: See optin/optout/email-language instead of terms-and-conditions

> #### 2.2.9.2 ```POST /preferences/:entityId/optin```
> REMOVE ROUTE: no longer used

> #### 2.2.9.4 ```POST /preferences/:entityId/email-language```
> REMOVE ROUTE: no longer used

> #### 2.2.9.5 ```POST /preferences/optin```
> Opt in to paperless

> #### 2.2.9.6 ```POST /preferences/regime/optin```
> Optin - built to support ITSA

> #### 2.2.9.7 ```POST /preferences/optout```
> Opt out of paperless

> #### 2.2.9.8 ```POST /preferences/regime/optout```
> Optout - built to support ITSA

> #### 2.2.9.9 ```POST /preferences/email-language```
> Change email language
```json
{
  "generic": {
    "accepted":true
  },
  "email":"EXAMPLE@TEST.com",
  "language": "en"
}
```
The meaning of language field.
1. For requests that produce OptIn event the languge refers to the
   languge of the page and the language of the future emails to the
   customer.
2. For CYSConfirmPage requests and requests with only languge field,
   the language is the language of the email only.
3. For all other requests the language is the language of the page.

Responds with status:

* `201` if a preference has successfully been created.
* `200` if an existing preference has been updated.
* `400` when a user opts in for paperless with a missing email address:
```json
{ "reason": "No email provided for user opting in for paperless" }
```
* `400` when an email address provided is an empty string:
```json
{ "reason": "Email cannot be empty" }
```
* `400` when a user's language preference could not be updated:
```json
{ "reason": "Unable to update language" }
```
* `400` when language is missing for requests generatiting OptIn, ReOptIn, CustomerOptOut and CustomerReOptOut events.
  Please refer to TermsAndConditionRequest.eventType method for acceptance in the request producing such events.
```json
"Could not parse body due to missing language in OptIn request"
"Could not parse body due to missing language in ReOptIn request"
"Could not parse body due to missing language in CustomerOptOut request"
"Could not parse body due to missing language in CustomerReOptOut request"
```

> #### 2.2.9.10 ```POST /preferences/regime/email-language```
> Change email language - built to support ITSA

### 2.2.10 Admin only - optout                      
> #### 2.2.10.1 ```POST /preferences-admin/:entityId/terms-and-conditions```
> DEPRECATED - used for opting out. If only consumed by admin frontend, this can be removed.

> #### 2.2.10.2 ```POST /preferences-admin/:entityId/optout```
> DEPRECATED - used for opting out. If only consumed by admin frontend, this can be removed.

> #### 2.2.10.3 ```POST /preferences-admin/optout```
> Optout of paperless - go back to paper.

### 2.2.11 Process Bounced Email                                      
> #### ```POST /preferences/email/bounce```
> Process bounced emails

### 2.2.12 Enrolment                                                  
> ```/preferences/mark-for-de-enrolment```

> #### ```PUT /preferences/mark-for-de-enrolment```

> #### ```PUT /preferences/unset-de-enrolment```

### 3. Debugging
Note that logging has been supressed in `test/resources/logback-test.xml` by an empty `<configuration/>` tag.
To restore logs to aid debugging, copy the contents of the main `logback.xml` file in the `conf` directory.
