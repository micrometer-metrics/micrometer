## Running Atlas locally
                           
Run `./atlas.sh`. To tweak the local server's settings, make modifications
to `.atlas/memory.conf`.

## Running Ganglia locally

Run `./ganglia.sh`.

## Running Prometheus/Grafana locally

Configure a loopback Alias so Prometheus can scrape a service running on localhost
and grafana can contact Prometheus (needs to be repeated after reboot):

`sudo ifconfig lo0 alias 10.200.10.1/24`

Start Prometheus:

`docker run -p 9090:9090 -v ~/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus`

Start Grafana:

`docker run -i -p 3000:3000 grafana/grafana`