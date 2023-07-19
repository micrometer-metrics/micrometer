/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.stackdriver.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.api.MetricDescriptor;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.ListMetricDescriptorsRequest;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * When running this as a main method, supply an environment variable
 * GOOGLE_APPLICATION_CREDENTIALS with a service account JSON. Your service account must
 * have the "monitoring.editor" role at least.
 *
 * @see <a href=
 * "https://cloud.google.com/monitoring/docs/reference/libraries#setting_up_authentication">Google
 * Cloud authentication</a>
 * @see <a href="https://cloud.google.com/monitoring/access-control">Google Cloud access
 * control</a>
 * @author Jon Schneider
 * @since 1.1.0
 */
public class ClearCustomMetricDescriptors {

    public static void clearCustomMetricDescriptors(MetricServiceSettings settings, String projectId) {
        try {
            MetricServiceClient client = MetricServiceClient.create(settings);

            Iterable<MetricServiceClient.ListMetricDescriptorsPage> listMetricDescriptorsPages = client
                .listMetricDescriptors(ListMetricDescriptorsRequest.newBuilder()
                    .setName("projects/" + projectId)
                    .setFilter("metric.type = starts_with(\"custom.googleapis.com/\")")
                    .build())
                .iteratePages();

            int deleted = 0;
            for (MetricServiceClient.ListMetricDescriptorsPage page : listMetricDescriptorsPages) {
                for (MetricDescriptor metricDescriptor : page.getValues()) {
                    System.out.println("deleting " + metricDescriptor.getName());
                    client.deleteMetricDescriptor(metricDescriptor.getName());
                    deleted++;
                }
            }

            System.out.println("Deleted " + deleted + " custom metric descriptors");
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);

        if (args.length == 0) {
            throw new IllegalArgumentException("Must provide a project id");
        }

        ClearCustomMetricDescriptors.clearCustomMetricDescriptors(MetricServiceSettings.newBuilder().build(), args[0]);
    }

}
