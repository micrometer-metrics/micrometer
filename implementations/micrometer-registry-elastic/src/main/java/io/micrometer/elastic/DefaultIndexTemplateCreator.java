/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.elastic;

import io.micrometer.core.ipc.http.HttpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link IndexTemplateCreator}, compatible with Elasticsearch 7.8+.
 *
 * @author Brian Clozel
 * @see <a href=
 * "https://www.elastic.co/guide/en/elasticsearch/reference/7.8/index-templates.html">Index
 * Templates documentation</a>.
 */
class DefaultIndexTemplateCreator implements IndexTemplateCreator {

    private static final String INDEX_TEMPLATE_PATH = "/_index_template/metrics_template";

    private final Logger logger = LoggerFactory.getLogger(DefaultIndexTemplateCreator.class);

    private final String indexTemplateRequest = "{\n" + "  \"index_patterns\": [\"%s*\"],\n" + "  \"template\": {\n"
            + "    \"mappings\": {\n" + "      \"_source\": {\n" + "        \"enabled\": %b\n" + "      },\n"
            + "      \"properties\": {\n" + "        \"name\": { \"type\": \"keyword\" },\n"
            + "        \"count\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"value\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"sum\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"mean\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"duration\": { \"type\": \"double\", \"index\": false},\n"
            + "        \"max\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"total\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"unknown\": { \"type\": \"double\", \"index\": false },\n"
            + "        \"active\": { \"type\": \"double\", \"index\": false }\n" + "      }\n" + "    }\n" + "  }\n"
            + "}";

    private final HttpSender httpClient;

    DefaultIndexTemplateCreator(HttpSender httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public IndexTemplateStatus fetchIndexTemplateStatus(ElasticConfig configuration) {
        HttpSender.Request.Builder request = this.httpClient.head(configuration.host() + INDEX_TEMPLATE_PATH);
        configureAuthentication(configuration, request);
        try {
            HttpSender.Response response = request.send();
            switch (response.code()) {
                case 200:
                    logger.debug("Metrics index template already exists at '{}'", INDEX_TEMPLATE_PATH);
                    return IndexTemplateStatus.EXISTS;
                case 404:
                    logger.debug("Metrics index template is missing from '{}'", INDEX_TEMPLATE_PATH);
                    return IndexTemplateStatus.MISSING;
                default:
                    logger.error("Could not create index template in Elastic (HTTP {}): {}", response.code(),
                            response.body());
                    return IndexTemplateStatus.NOT_SUPPORTED;
            }
        }
        catch (Throwable exc) {
            logger.error("Could not create index template in Elastic", exc);
        }
        return IndexTemplateStatus.NOT_SUPPORTED;
    }

    @Override
    public void createIndexTemplate(ElasticConfig configuration) throws Throwable {
        String indexPattern = configuration.index() + configuration.indexDateSeparator();
        boolean enableSource = configuration.enableSource();
        HttpSender.Request.Builder request = this.httpClient.put(configuration.host() + INDEX_TEMPLATE_PATH);
        configureAuthentication(configuration, request);
        request.withJsonContent(String.format(indexTemplateRequest, indexPattern, enableSource))
            .send()
            .onError(response -> logger.error("Failed to create index template in Elastic: {}", response.body()));
    }

}
