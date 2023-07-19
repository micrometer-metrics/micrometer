/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.statsd;

public enum StatsdFlavor {

    /**
     * https://github.com/etsy/statsd/blob/master/docs/metric_types.md
     */
    ETSY,

    /**
     * https://docs.datadoghq.com/guides/dogstatsd/#datagram-format
     */
    DATADOG,

    /**
     * https://www.influxdata.com/blog/getting-started-with-sending-statsd-metrics-to-telegraf-influxdb/
     * <p>
     * For gauges to work as expected, you should set {@code delete_gauges = false} in
     * your input options as documented here:
     * https://github.com/influxdata/telegraf/tree/master/plugins/inputs/statsd
     */
    TELEGRAF,

    /**
     * https://support.sysdig.com/hc/en-us/articles/204376099-Metrics-integrations-StatsD
     */
    SYSDIG

}
