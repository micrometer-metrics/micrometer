#!/usr/bin/env bash
docker run --name my_cassandra -e "CASSANDRA_START_RPC=true" cassandra