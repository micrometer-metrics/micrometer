#!/usr/bin/env bash
docker run --link my_cassandra:cassandra -e "CASSANDRA_HOST_LIST=cassandra:9160" -p 8083:8083 jimtonic/kairosdb
