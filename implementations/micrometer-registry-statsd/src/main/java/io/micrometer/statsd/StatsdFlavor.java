package io.micrometer.statsd;

public enum StatsdFlavor {
    /**
     * https://github.com/etsy/statsd/blob/master/docs/metric_types.md
     */
    Etsy,

    /**
     * https://docs.datadoghq.com/guides/dogstatsd/#datagram-format
     */
    Datadog,

    /**
     * https://www.influxdata.com/blog/getting-started-with-sending-statsd-metrics-to-telegraf-influxdb/
     */
    Telegraf
}