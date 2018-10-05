#!/usr/bin/env bash

if [ ! -f .ganglia ]; then
    mkdir -p .ganglia/etc
    mkdir -p .ganglia/data
fi

docker run \
  -v $(pwd)/.ganglia/etc:/etc/ganglia \
  -v $(pwd)/.ganglia/data:/var/lib/ganglia \
  -p 8089:80 \
  -p 8649:8649 \
  -p 8649:8649/udp \
  kurthuwig/ganglia:latest