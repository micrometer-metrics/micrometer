/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * {@link StepMeterRegistry} for Dynatrace metric API v2
 * https://dev-wiki.dynatrace.org/display/MET/MINT+Specification#MINTSpecification-IngestFormat
 *
 * @author Oriol Barcelona
 * @author David Mass
 * @see <a href="https://www.dynatrace.com/support/help/dynatrace-api/environment-api/metric-v2/post-ingest-metrics/">Dynatrace metric ingestion v2</a>
 * @since ?
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dynatrace2-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistry.class);
    private final DynatraceConfig config;
    private final HttpSender httpClient;

    private DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        this.config = config;
        this.httpClient = httpClient;
        addCommonTags();

        config().namingConvention(new LineProtocolNamingConvention());
        start(threadFactory);
    }

    @Override
    protected void publish() {
        MetricLineFactory metricLineFactory = new MetricLineFactory(clock, config);

        Map<Boolean, List<String>> metricLines = getMeters()
                .stream()
                .flatMap(metricLineFactory::toMetricLines)
                .collect(Collectors.partitioningBy(this::lineLengthGreaterThanLimit));

        List<String> metricLinesToSkip = metricLines.get(true);
        if (!metricLinesToSkip.isEmpty()) {
            logger.warn(
                    "Dropping {} metric lines because are greater than line protocol max length limit ({}).",
                    metricLinesToSkip.size(),
                    LineProtocolIngestionLimits.METRIC_LINE_MAX_LENGTH);
        }

        List<String> metricLinesToSend = metricLines.get(false);
        new MetricsApiIngestion(httpClient, config)
                .sendInBatches(metricLinesToSend);
    }

    private boolean lineLengthGreaterThanLimit(String line) {
        return line.length() > LineProtocolIngestionLimits.METRIC_LINE_MAX_LENGTH;
    }

    private void addCommonTags() {
        addCommonTags_entityId();
        addCommonTags_deviceName();
        addCommonTags_groupName();
    }

    private void addCommonTags_groupName(){
        if (!config.deviceName().equals("")){
            config().commonTags("group-name", config.groupName());
        }
    }

    private void addCommonTags_deviceName(){
        if (!config.deviceName().equals("")){
            config().commonTags("device-name", config.deviceName());
        }
    }

    private void addCommonTags_entityId(){
        String[] cases = {"HOST","PROCESS_GROUP_INSTANCE","PROCESS_GROUP","CUSTOM_DEVICE_GROUP","CUSTOM_DEVICE"};
        if (!config.entityId().equals("")) {
            int index;
            for(index=0;index<cases.length; index++){
                if(config.entityId().startsWith(cases[index])) break;
            }
            switch(index){
                case 0:
                    config().commonTags("dt.entity.host", config.entityId());
                    break;
                case 1:
                    config().commonTags("dt.entity.process_group_instance", config.entityId());
                    break;
                case 2:
                    config().commonTags("dt.entity.process_group", config.entityId());
                    break;
                case 3:
                    config().commonTags("dt.entity.custom_device_group", config.entityId());
                    break;
                case 4:
                    config().commonTags("dt.entity.custom_device", config.entityId());
                    break;
                default:
                    logger.debug("Entity ID Not Available");
            }
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static Builder builder(DynatraceConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final DynatraceConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(DynatraceConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public DynatraceMeterRegistry build() {
            return new DynatraceMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}

