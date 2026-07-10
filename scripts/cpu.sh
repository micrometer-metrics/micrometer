#!/usr/bin/env bash
htop -p $(ps ax | grep '[m]icrometer-core' | awk '{print $1}')