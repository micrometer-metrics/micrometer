#!/usr/bin/env bash

docker kill influxdb || true
docker rm influxdb || true
docker pull quay.io/influxdb/influx:nightly || true
docker run \
       --detach \
       --name influxdb \
       --publish 8086:9999 \
       quay.io/influxdb/influx:nightly

echo "Wait 5s to start InfluxDB 2.0"
sleep 5

echo "Post onboarding request, to setup initial user, org and bucket"
curl -i -X POST http://localhost:8086/api/v2/setup -H 'accept: application/json' \
    -d '{
            "username": "my-user",
            "password": "my-password",
            "org": "my-org",
            "bucket": "my-bucket",
            "token": "my-token"
        }'
