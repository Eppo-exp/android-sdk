# Eppo Android SDK

[![Test and lint SDK](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml/badge.svg)](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml)

[Eppo](https://www.geteppo.com/) is a modular flagging and experimentation analysis tool. Eppo's Android SDK is built to make assignments for single user client applications. Before proceeding you'll need an Eppo account.

## Features

- Feature gates
- Kill switches
- Progressive rollouts
- A/B/n experiments
- Mutually exclusive experiments (Layers)
- Dynamic configuration

## Installation

Install the SDK using Gradle:

```java
implementation 'cloud.eppo:android-sdk:1.0.2'
```

## Quick start

Begin by initializing a singleton instance of Eppo's client. Once initialized, the client can be used to make assignments anywhere in your app.

#### Initialize once

```java
import cloud.eppo.android.EppoClient;

EppoClient.init("SDK-KEY-FROM-DASHBOARD");
```


#### Assign anywhere

```java
import cloud.eppo.android.EppoClient;

EppoClient eppoClient = EppoClient.getInstance(); 
User user = getCurrentUser();

Boolean variant = eppoClient.getBooleanAssignment(
  'new-user-onboarding', 
  user.id, 
  user.attributes, 
  false
);
```

## Assignment functions

Every Eppo flag has a return type that is set once on creation in the dashboard. Once a flag is created, assignments in code should be made using the corresponding typed function: 

```java
getBooleanAssignment(...)
getNumericAssignment(...)
getIntegerAssignment(...)
getStringAssignment(...)
getJSONAssignment(...)
```

Each function has the same signature, but returns the type in the function name. For booleans use `getBooleanAssignment`, which has the following signature:

```java
public boolean getBooleanAssignment(
  String flagKey, 
  String subjectKey, 
  Map<String, Object> subjectAttributes, 
  String defaultValue
)
  ```

## Assignment logger 

To use the Eppo SDK for experiments that require analysis, pass in a callback logging function to the `init` function on SDK initialization. The SDK invokes the callback to capture assignment data whenever a variation is assigned. The assignment data is needed in the warehouse to perform analysis.

The code below illustrates an example implementation of a logging callback using [Segment](https://segment.com/), but you can use any system you'd like. The only requirement is that the SDK receives a `logAssignment` callback function. Here we define an implementation of the Eppo `IAssignmentLogger` interface containing a single function named `logAssignment`:

```java
AssignmentLogger logger = new AssignmentLogger() {
    @Override
    public void logAssignment(Assignment assignment) {
        analytics.enqueue(TrackMessage.builder("Eppo Randomized Assignment")
                .userId(assignment.getSubject())
                .properties(ImmutableMap.builder()
                        .put("timestamp", assignment.getTimestamp())
                        .put("experiment", assignment.getExperiment())
                        .put("variation", assignment.getVariation())
                        .build()
                );
        );
    }
};

EppoClient eppoClient = new EppoClient.Builder()
    .apiKey("SDK-KEY-FROM-DASHBOARD")
    .assignmentLogger(assignmentLogger)
    .application(application)
    .buildAndInit();
```

## Philosophy

Eppo's SDKs are built for simplicity, speed and reliability. Flag configurations are compressed and distributed over a global CDN (Fastly), typically reaching your servers in under 15ms. Server SDKs continue polling Eppo’s API at 30-second intervals. Configurations are then cached locally, ensuring that each assignment is made instantly. Evaluation logic within each SDK consists of a few lines of simple numeric and string comparisons. The typed functions listed above are all developers need to understand, abstracting away the complexity of the Eppo's underlying (and expanding) feature set.


