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

The subscription manager stores two conceptually distinct caches in a single shared redis logical database - the
**Normalized Cache** which is designed to be accessed via a connection key, and the **Denormalized Cache** which is
designed to be accessed by a resource ID. Both caches use a namespacing technique to avoid possible key collisions
between the two. Both caches are updated during subscribe/unsubscribe actions.

#### The Normalized Cache

Redis key Namespace: `$connection-id`

The normalized cache utilizes a [redis hash](https://redis.io/docs/manual/data-types/#hashes) data type to persist
information about a websocket's subscriptions. Valid key/value pairs are in this hash are as follows:

| Key                | Value                                                                                                                               |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `created-at`       | Stringified timestamp in ms                                                                                                         |
| `<subscription id>` | Stringified JSON array with each value being a key into the denormalized cache<br/>(see the namespacing description for those keys) |

Example:

```json

{
  "$connection-id:unique-connection-id-1": {
    "created_at": "1658858886356",
    "<SUBSCRIPTION_ID_1>": "[\"ts:device:unique-dev-id-1\",\"ts:device:unique-dev-id-2\"]",
    "<SUBSCRIPTION_ID_2>": "[\"ts:athlete:unique-ath-id-1\",\"ts:user:unique-user-id-1\"]"
  }
}

```

#### The Denormalized Cache

The denormalized cache consists of key/value pairs of strings with the key being a namespaced resource id, and the value
being a stringified json list of strings where each string is a comma separated list with the first value being a
connection id, and the remaining values being a list of subscription id for that connection that are subscribed to that
resource.

The Resource Namespace consists of three parts - a data class (used to disambiguate a resource that may appear in
multiple data classes), a resource type, and a unique resource id. The key is a concatenation of all three parts in the
listed order. All three parts must be present.

- Data Class
    - `ts` - time series
    - `su` - summary data
- Resource Type
    - `user`
    - `devicde`
    - `athlete`
    - `field`
    - `period`
    - `tag`

Example:

```json

{
  "ts:device-id:unique-device-id-1": "[\"unique-connection-id-1,unique-sub-1,unique-sub-2\",\"unique-connection-id-2,unique-sub-3,unique-sub-4\"]",
  "ts:device-id:unique-athlete-id-1": "[\"unique-connection-id-1,unique-sub-2\",\"unique-connection-id-3,unique-sub-5,unique-sub-6\"]"
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
       RedisStack=${STACK_NAMESPACE}-${STACK_ENV}-lds-elasticache-redis             
```