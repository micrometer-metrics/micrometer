#!/usr/bin/env bash

if [ ! -f .telegraf/tele.conf ]; then
    echo "Initializing the telegraf config. If you need to point to an influx instance not on localhost, modify ./telegraf/tele.conf"
    mkdir .telegraf
    telegraf --input-filter statsd --output-filter influxdb config > ./.telegraf/tele.conf
fi

telegraf -config ./.telegraf/tele.conf