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
of [AWS Lambdas](https://docs.aws.amazon.com/lambda/index.html) and accessed via a websocket connection.

### Data structures

Describe redis datastructures

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

```shell
$ sam build -t sam.yml 
$ sam package 
   --output-template-file packaged.yaml \
   --s3-bucket lambda-deployables-lds \
   --s3-prefix subscription-function  \
   --force-upload 
$ sam deploy 
    --template-file packaged.yaml  \
    --config-env jf-dev \
    --stack-name jf-dev-subscription-function-stack
```