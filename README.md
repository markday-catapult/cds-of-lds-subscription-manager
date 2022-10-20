# Subscription Manager

Subscription manager will accept websocket connect and subscription requests from Live Data Service clients via an AWS
API Gateway.

## Overview

The subscription manager is an application that has the responsibility of managing Live Data subscriptions. Clients that
wish to receive streaming live data must first create a subscription to the data that it wants, after which they will
receive that data as it becomes available. When the client no longer wants to receive that data, it may cancel the
corresponding subscription.

## Implementation

### Architecture

The subscription manager is an application that is comprised of a collection
of [AWS Lambdas](https://docs.aws.amazon.com/lambda/index.html) and accessed via a websocket connection. It uses a
[Redis Elasticache](https://aws.amazon.com/elasticache/) instance for all data persistence.

### Data structures

The subscription manager stores two conceptually distinct caches in a single shared **redis** logical database - the
**Normalized Cache** which is designed to be accessed via a connection key, and the **Denormalized Cache** which is
designed to be accessed by a resource ID. Both caches use a namespacing technique to avoid possible key collisions
between the two. Both caches are updated during subscribe/unsubscribe actions.

#### The Normalized Cache

Redis key Namespace: `$connection-id`

The normalized cache utilizes a [redis string](https://redis.io/docs/data-types/strings/) data type to persist
information about a websocket's subscriptions. The value is a stringified json object with the following keys:

| Key             | Required | Value                                                        |
|-----------------|----------|--------------------------------------------------------------|
| `id`            | Yes      | The connection ID                                            |
| `createdAt`     | Yes      | Stringified timestamp in ms                                  |
| `subscriberId`  | Yes      | The user key of the subscriber (the owner of the connection) |
| `subscriptions` | Yes      | A list of Subscription objects as defined below              |

Subscription object:

| Key            | Required | Value                                                                                                                                                |
|----------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`           | Yes      | The subscription id                                                                                                                                  |
| `connectionId` | Yes      | The id of the connection that the subscription is associated with                                                                                    | 
| `resources`    | Yes      | An array where each value is a key into the denormalized cache (see the namespacing description for those keys)                                      |
| `sample_rate`  | No       | If present, the sample rate in Hz of the data subscribed to. If not present, no downsampling is applied to data. Valid values for this are 1,2,5,10. | 

Example:

```json

{
  "id": "CON1",
  "createdAt": 1666211855095,
  "subscriberId": "subscriber-id",
  "subscriptions": [
    {
      "id": "5f369f08-db7e-4e48-91aa-dfd0cd8a6b85",
      "connectionId": "CON1",
      "resources": [
        "athlete-id-2",
        "athlete-id-1"
      ],
      "sampleRate": null
    },
    {
      "id": "12196176-f32b-40ac-a1d6-efe94cfe8e8e",
      "connectionId": "CON1",
      "resources": [
        "device-id-1",
        "athlete-id-1",
        "device-id-2"
      ],
      "sampleRate": 1
    },
    {
      "id": "4010523e-3e6f-493e-8757-b5504a098d4f",
      "connectionId": "CON1",
      "resources": [
        "device-id-1",
        "athlete-id-1"
      ],
      "sampleRate": 2
    }
  ]
}
```

#### The Denormalized Cache

The denormalized cache utilizes a [redis string](https://redis.io/docs/data-types/strings/) data type to persist the
information needed by the demuxer to efficiently route messages to connections. The key is the resource id and the
value is a stringified JSON list of Denormalized Cache Connection objects.

| Key             | Required | Value                                                        |
|-----------------|----------|--------------------------------------------------------------|
| `id`            | Yes      | The id of the connection that the resource should be sent to | 
| `subscriptions` | Yes      | An array where each value is a Simplified Subscription       |  

Simplified Subscription Object

| Key          | Required | Value                       |
|--------------|----------|-----------------------------|
| `id`         | Yes      | The id of the subscription  | 
| `sampleRate` | Yes      | The sample rare of the data |  

Example:

```json 
{

[
  {
    "connectionId": "connection-id-abc",
    "subscriptions": [
      {
        "id": "subscription-id-123",
        "sampleRate": 5
      },
      {
        "id": "subscription-id-123",
        "sampleRate": 2
      }
    ]
  },
  {
    "connectionId": "connection-id-def",
    "subscriptionIds": [
      {
        "id": "subscription-id-777"
      }
    ]
  },
  {
    "connectionId": "connection-id-ghi",
    "subscriptionIds": [
      {
        "id": "subscription-id-123",
        "sampleRate": 1
      }
    ]
  }
]
```

The Resource Namespace consists of three parts - a data class (used to disambiguate a resource that may appear in
multiple data classes), a resource type, and a unique resource id. The key is a concatenation of all three parts in the
listed order. All three parts must be present.

- Data Class
    - `ts` - time series
    - `ad` - aggregate data
- Resource Type
    - `user`
    - `device`
    - `athlete`
    - `field`
    - `period`
    - `tag`

Example:

```json

{
  "ts:device:dev_id_123": "[{\"connectionId\":\"connection-id-abc\",\"subscriptionIds\":[\"subscription-id-123\",\"subscription-id-456\"]},{\"connectionId\":\"connection-id-lmn\",\"subscriptionIds\":[\"subscription-id-545\",\"subscription-id-767\"]}]",
  "ts:athlete:ath_id_783": "[{\"connectionId\":\"connection-id-lmn\",\"subscriptionIds\":[\"subscription-id-988\",\"subscription-id-767\"]}]"
}

```

## Deployment

The subscription service may be deployed
using [AWS SAM](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html).

### Prerequisites

- SAM client

#### Stacks

- API Gateway
- Redis
- VPC

### Configuration

samconfig.toml

### Running Locally

TODO

### Sam Deployment

To make sure we don't clobber other's stacks while testing, please set up a namespace for your template to run:

```shell
export STACK_NAMESPACE=<YOUR INITIALS HERE>
export STACK_ENV=dev
export STACK_REGION=<YOUR REGION HERE>
```

```shell
$ sam build -t sam.yml 
$ sam package \
   --output-template-file packaged.yaml \
   --s3-bucket "lambda-deploy-lds-${STACK_REGION}" \
   --s3-prefix subscription-manager  \
   --force-upload 
$ sam deploy \
    --template-file packaged.yaml  \
    --s3-bucket "lambda-deploy-lds-${STACK_REGION}" \
    --s3-prefix subscription-manager  \
    --capabilities CAPABILITY_NAMED_IAM               \
    --stack-name "${STACK_NAMESPACE}-${STACK_ENV}-subscription-manager" \
    --region ${STACK_REGION}                           \
    --parameter-overrides                                 \
       StackNamespace=${STACK_NAMESPACE}                      \
       VersionId=`git rev-parse --short HEAD`           \
       StackEnv=${STACK_ENV}                            \
       VpcStack=${STACK_NAMESPACE}-${STACK_ENV}-lds-vpc       \
       ApiGatewayStack=${STACK_NAMESPACE}-${STACK_ENV}-lds-api-gateway       \
       ApiGatewayAuthorizerStack=${STACK_NAMESPACE}-${STACK_ENV}-lds-authorizer       \
       RedisStack=${STACK_NAMESPACE}-${STACK_ENV}-lds-elasticache-redis             
```