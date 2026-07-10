#!/usr/bin/env bash

docker run -d\
 -p 2003-2004:2003-2004\
 -p 2003:2003/udp\
 -p 7002:7002\
 -p 7007:7007\
 bodsch/docker-go-carbon
