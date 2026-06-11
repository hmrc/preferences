# preferences Event: Email Verified

Customer clicked verification link and preferences successfully marked email as verified.


```javascript
{
    transactionName: "Email Verified",
    detail: {
        "entityId"         -> entityId,
        "emailAddress"     -> emailAddress,
        "verificationLink" -> externalVerificationLink
        }
}
```


# preferences Event: Print Suppression Off

Customer's preference record is marked as bounced.

It can be done automatically when email microservice receive a bounce
from imi, or manually via admin interface.

```javascript
{
    transactionName: "Print Suppression Off",
    detail: {
        "entityId"         -> entityId,
        "emailAddress"     -> emailAddress
        },
    tags:  {
        "reason"   -> "Bounced Message detected"),
       }
}
```


# preferences Event: Email Verification Link Sent, type OptedIn

Customer opted in and verification link sent.
Event is not sent if customer has either verified or pending email already

```javascript
{
     transactionName: "Email Verification Link Sent",
     detail: {
       "entityId"         -> entityId,
       "emailAddress"     -> email,
       "verificationLink" -> link,
       "verificationType" -> "optedIn"
       }
}
```

# preferences Event: Email Verification Link Sent, type emailAddressChanged

Customer changed email address and verification link was sent.

```javascript
{
     transactionName: "Email Verification Link Sent",
     detail: {
       "entityId"         -> entityId,
       "emailAddress"     -> email,
       "verificationLink" -> link,
       "verificationType" -> "emailAddressChanged"
       }
}
```

# preferences Event: Opt In Email Reminders

Customer accepted generic T&C.
If customer has accepted T&C already we don't send this event.

```javascript
{
     transactionName: "Opt In Email Reminders",
     tags: "reason" -> "User Selected to Opt In"
     detail:  {
       "entityId"           -> entityId,
       "emailAddress"       -> email,
       "termsAndConditions" -> terms
     }
}
```


# preferences Event: Opt Out Email Reminders

Customer opted out.

```javascript
{
     transactionName: "Opt Out Email Reminders",
     tags: reason" -> "User Selected to Opt Out",
     detail: {
       "entityId"           -> entityId,
       "wasVerified"        -> previousPreferences.exists(_.email.exists(_.isVerified),
       "wasDigital"         -> previousPreferences.exists(!_.isOptedOut(terms)),
       "wasBounced"         -> previousPreferences.exists(_.email.exists(_.isBounced)),
       "termsAndConditions" -> terms
       }
}
```

# preferences-frontend Event: Set Print Preference

Customer used opt-in/out form to submit a POST to /paperless/choose.

```javascript
{
     auditSource: preferences-frontend,
     auditType: Failed or Succeeded,
     request: {
         tags: transactionName -> "Set Print Preference", path -> request.path,
         detail: {
             "client"                    -> "YTA",
             "utr"                       -> request.saUtr.getOrElse("N/A"),
             "nino"                      -> request.nino.getOrElse("N/A"),
             "journey"                   -> journey,
             "digital"                   -> true or false,
             "cohort"                    -> cohort,
             "TandCsScope"               -> genericTerms,
             "userConfirmedReadTandCs"   -> true or false,
             "email"                     -> email,
             "newUserPreferencesCreated" -> true if preference was created
         },
         generatedAt = time
     },
     response: {
         tags: transactionName -> "Set Print Preference", path -> request.path,
         detail: {
             "client"                    -> "YTA",
             "utr"                       -> request.saUtr.getOrElse("N/A"),
             "nino"                      -> request.nino.getOrElse("N/A"),
             "journey"                   -> journey,
             "digital"                   -> true or false,
             "cohort"                    -> cohort,
             "TandCsScope"               -> genericTerms,
             "userConfirmedReadTandCs"   -> true or false,
             "email"                     -> email,
             "newUserPreferencesCreated" -> true if preference was created
         },
         generatedAt = time
     }
}
```

# preferences-frontend Event: Show Print Preference Option

Customer requested opt-in/out form (GET to /paperless/choose)

```javascript
{
    auditSource: preferences-frontend,
    auditType = Succeeded,
    request: {
        tags: transactionName -> "Show Print Preference Option", path -> request.path,
        detail: {
            "utr"     -> saUtr.getOrElse("N/A"),
            "nino"    -> nino.getOrElse("N/A"),
            "journey" -> journey
            "cohort"  -> cohort
        }
        generatedAt: time,
    },
    response: {
        tags = transactionName -> "Show Print Preference Option", path -> request.path,
        detail: {
            "utr"     -> saUtr.getOrElse("N/A"),
            "nino"    -> nino.getOrElse("N/A"),
            "journey" -> journey
            "cohort"  -> cohort
        }
        generatedAt:  time
    }
}
```

# entity-resolver Event: Manual Opt-Out: Opted out

Manual opt-out request. entity-resolver received `OK` from POST to preferences:/preferences/{entity.id}/optout


```javascript
{
        auditSource: entity-resolver,
        auditType: Succeeded,
        tags: "transactionName" -> "Manual Opt-Out: Opted out",
        detail: taxId.name      -> taxId
}
```

# entity-resolver Event: Manual Opt-Out: Already opted out

Manual opt-out request. entity-resolver received `CONFLICT` from POST to preferences:/preferences/{entity.id}/optout

```javascript
{
   auditSource: entity-resolver,
   auditType: Succeeded,
   tags: "transactionName" -> "Manual Opt-Out: Already opted out",
   detail: taxId.name      -> taxId
}

```

# entity-resolver Event: Manual Opt-Out: No preference

Manual opt-out request. entity-resolver received `NOT_FOUND` from POST to preferences:/preferences/{entity.id}/optout


```javascript
{
    auditSource: entity-resolver,
    auditType: Succeeded,
    tags: "transactionName" -> "Manual Opt-Out: No preference",
    detail: taxId.name      -> taxId
}

```

# entity-resolver Event: Manual Opt-Out: No EntityId

Manual opt-out request. entity-resolver received `PRECONDITION_FAILED` from POST to preferences:/preferences/{entity.id}/optout

```javascript
{
   auditSource: entity-resolver,
   auditType: Failed,
   tags: "transactionName" -> "Manual Opt-Out: No EntityId",
   detail: taxId.name      -> taxId
}

```

# entity-resolver Event:  conflict

IllegalStateException: 2 entities found having at least one of sautr or nino

Please see DC-324 for the CASENAMEs definitions

CASENAME ranges from 1 to 8

```javascript
{
    auditSource: entity-resolver,
    auditType:   Succeeded,
    tags: transactionName -> "conflict", path -> CASENAME,
    detail: {
        "case"                -> CASENAME,
        "userLoggedInAs"      -> authTaxIds,
        "conflictingEntities" -> entities
    },
   "generatedAt": "YYYY-MM-DDThh:mm:ss.zzz"
}

```
# entity-resolver Event: EntityId Created

It is most likely broken. Event is sent while attempting to resolve taxIds to entityId.

Review required.

```javascript
{
  auditSource: entity-resolver,
  auditType:   Succeeded,
  tags: transactionName -> EntityId Created,
  detail: {
      taxId => 'taxId.name,  taxId.value'
      ...,
      entityId -> entityId.value
  },
  "generatedAt": "YYYY-MM-DDThh:mm:ss.zzz"
}
```

# stats-collector Event:  stats-collector

Sends json from stats collect GET call on preferences:/preferences/stats.
Please see https://github.com/hmrc/preferences/blob/master/stats.md. 

```javascript
{
   "auditSource":"stats-collector",
   "eventId": [Unique generated id],
   "tags":{
      "transactionName": stats-collector,
   },
   "detail":{
      [json from stats collect call on service]

   },
   "generatedAt": "YYYY-MM-DDThh:mm:ss.zzz"
}
```

# stats-collector Event: stats-generator

Sends json from stats collect POST call on preferences:/preferences/stats.

```javascript
{
   "auditSource":"stats-collector",
   "eventId": [Unique generated id],
   "tags":{
      "transactionName": "stats-generator",
   },
   "detail":{
      [json from stats collect call on service]

   },
   "generatedAt": "YYYY-MM-DDThh:mm:ss.zzz"
}
```
