Statistics
==========

Statistics are collected overnight in a 2 step process, an initial call is made to generate the values and a second call is made some time later to collect the generated statistics.

| Path                        |  Method | Description                                     |
| --------------------------- |---------| ------------------------------------------------|
|```/preferences/stats```     |  GET    | Collect previously generated statistics         | 
|```/preferences/stats```     |  POST   | Generate statistics                             |


# Generated Statistic Counts 
    
| Name                                                          | Description                                                                   |
|---------------------------------------------------------------|-------------------------------------------------------------------------------|
| delinquentForMoreThan28Days                                   | Preferences with just a pending email with verification sent >28 days ago     |
| delinquentForMoreThan2DaysUpTo7DaysIncl                       | Preferences with just a pending email with verification sent 2-7 days ago     |
| delinquentForMoreThan7DaysUpTo28DaysIncl                      | Preferences with just a pending email with verification sent 7-28 days ago    |
| generic.optedIn                                               | Generic T&C defined and accepted                                              |
| generic.optedInAndVerified                                    | Generic T&C defined, accepted and contactable                                 |
| generic.optedInAndVerifiedAndWelsh                            | Generic T&C defined, accepted, contactable and has selected Welsh             |
| generic.optedOut                                              | Generic T&C defined and not accepted                                          |
| pendingVerification                                           | Preferences without a verified email                                          |
| pendingVerificationAndBounced                                 | Preferences without a verified email and with a pending and bounced email     |
| totalOfAllPreferences                                         | All stored preferences                                                        |
| verifiedButBounced                                            | Preferences with a verified but bounced email and no pending email            |
| verifiedButBouncedPendingVerificationOfChangedEmail           | Preferences with a verified but bounced email and a pending email not bounced |
| verifiedButBouncedPendingVerificationOfChangedEmailAndBounced | Preferences with both verified and pending emails and both are bounced        |
| verifiedPendingVerificationOfChangedEmailAndBounced           | Preferences with a verified and pending email that is bounced                 |

Exact SQL count query for each statistics can be found [here](app/uk/gov/hmrc/preferences/repository/CollectStatsRepository.scala)
For example: 

``` scala
 def runStatsQuery(): Seq[Unit] =
    Seq(
      countPreferencesWhere(
        "pendingVerification",
        doesNotHaveVerifiedEmail,
        hasPendingEmail,
        doesNotHaveBouncedPendingEmail
      ),
      ....

```


## Example of output of GET to /preferences/stats

```javascript
{  
   "totalOfAllPreferences":{  
      "count":14,
      "date":"2016-06-22"
   },
   "verifiedButBouncedPendingVerificationOfChangedEmail":{  
      "count":1,
      "date":"2016-06-22"
   },
   "delinquentForMoreThan28Days":{  
      "count":1,
      "date":"2016-06-22"
   },
   "pendingVerification":{  
      "count":4,
      "date":"2016-06-22"
   },
   "totalOfAllOptedIn":{  
      "count":13,
      "date":"2016-06-22"
   },
   "delinquentForMoreThan2DaysUpTo7DaysIncl":{  
      "count":1,
      "date":"2016-06-22"
   },
   "verifiedButBouncedPendingVerificationOfChangedEmailAndBounced":{  
      "count":1,
      "date":"2016-06-22"
   },
   "verifiedNotBounced":{  
      "count":3,
      "date":"2016-06-22"
   },
   "optedOut":{  
      "count":1,
      "date":"2016-06-22"
   },
   "pendingVerificationOfChangedEmail":{  
      "count":1,
      "date":"2016-06-22"
   },
   "verifiedButBounced":{  
      "count":1,
      "date":"2016-06-22"
   },
   "verifiedPendingVerificationOfChangedEmailAndBounced":{  
      "count":1,
      "date":"2016-06-22"
   },
   "pendingVerificationAndBounced":{  
      "count":1,
      "date":"2016-06-22"
   },
   "delinquentForMoreThan7DaysUpTo28DaysIncl":{  
      "count":1,
      "date":"2016-06-22"
   }
}
```

## Collection workflow

1. Daily at 2:30 GMT `stats-collector` hits POST /preferences/stats to generate statistics
2. `preferences` runs count queries on `saIndividualPreferences` collection and updates `metrics` for the previous day.
3. Daily at 3:00 GMT `stats-collector` hits  GET /preferences/stats to get updated statistics and then sends splunk event. 


