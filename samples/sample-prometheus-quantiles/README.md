## Running Prometheus/Grafana locally

Configure a loopback Alias so Prometheus can scrape a service running on localhost
and grafana can contact Prometheus (needs to be repeated after reboot):

`sudo ifconfig lo0 alias 10.200.10.1/24`

[Clock drift](https://github.com/docker/for-mac/issues/17) on Docker for Mac < 17.05 
is troublesome. If you are on Docker for Mac 17.03-ish, you will need to run something like the following 
before running any containers after a sleep:

`docker run -it --rm --privileged --pid=host debian -t 1 -m -u -n -i date -u $(date -u +%m%d%H%M%Y)`


### Start Prometheus

`docker run -p 9090:9090 -v ~/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus`

### Start Grafana

`docker run -i -p 3000:3000 grafana/grafana`
