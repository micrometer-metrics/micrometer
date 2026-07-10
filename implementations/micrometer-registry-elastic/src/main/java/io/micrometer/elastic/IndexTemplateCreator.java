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

import io.micrometer.common.util.StringUtils;
import io.micrometer.core.ipc.http.HttpSender;

/**
 * Internal strategy to create Elasticsearch index templates for metrics indices.
 *
 * @author Brian Clozel
 */
interface IndexTemplateCreator {

    /**
     * Checks whether this strategy is able to create an index template on the remote
     * Elasticsearch server.
     * @param configuration the elastic configuration
     * @return the index template status
     */
    IndexTemplateStatus fetchIndexTemplateStatus(ElasticConfig configuration);

    /**
     * Create the index template.
     * @param configuration the elastic configuration
     */
    void createIndexTemplate(ElasticConfig configuration) throws Throwable;

    /**
     * Configure the authentication information on the HTTP request.
     * @param configuration the Elasticsearch configuration
     * @param request the new HTTP request
     */
    default void configureAuthentication(ElasticConfig configuration, HttpSender.Request.Builder request) {
        if (StringUtils.isNotBlank(configuration.apiKeyCredentials())) {
            request.withAuthentication("ApiKey", configuration.apiKeyCredentials());
        }
        else {
            request.withBasicAuthentication(configuration.userName(), configuration.password());
        }
    }

    /**
     * The status of the metrics index template on the Elasticsearch instance.
     */
    enum IndexTemplateStatus {

        /**
         * This strategy cannot create an index template on the Elasticsearch instance.
         */
        NOT_SUPPORTED,
        /**
         * The index template does not exist but can be created by this strategy.
         */
        MISSING,
        /**
         * The index template already exists, nothing needs to be done.
         */
        EXISTS

    }

}
