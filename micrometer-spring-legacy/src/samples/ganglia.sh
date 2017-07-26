#!/usr/bin/env bash

if [ ! -f .ganglia ]; then
    mkdir -p .ganglia/etc
    mkdir -p .ganglia/data
fi

docker run \
  -d \
  -v $(pwd)/.ganglia/etc:/etc/ganglia \
  -v $(pwd)/.ganglia/data:/var/lib/ganglia \
  -p 127.0.0.1:80:80 \
  -p 8649:8649 \
  -p 8649:8649/udp \
  kurthuwig/ganglia:latest