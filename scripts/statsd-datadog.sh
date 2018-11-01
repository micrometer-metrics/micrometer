#!/usr/bin/env bash

if [ $# -eq 0 ]
  then
    echo "Supply a datadog API key as an argument to this script"
fi

docker run --name dd-agent \
    -v /var/run/docker.sock:/var/run/docker.sock:ro \
    -v /proc/:/host/proc/:ro \
    -v /sys/fs/cgroup/:/host/sys/fs/cgroup:ro \
    -e DD_API_KEY=$1 \
    -e DD_DOGSTATSD_NON_LOCAL_TRAFFIC=true \
    -p 8125:8125/udp \
    datadog/agent:latest