/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.stackdriver;

import com.google.api.gax.rpc.HeaderProvider;

import java.util.Collections;
import java.util.Map;

/**
 * Provides the User-Agent header to signal to the Google Cloud Client Libraries that
 * requests originate from a Micrometer Stackdriver Registry.
 *
 * @author João André Martins
 * @author Chengyuan Zhao
 * @author Mike Eltsufin
 * @author Ray Tsang
 */
class UserAgentHeaderProvider implements HeaderProvider {

    private final Map<String, String> headers;

    /**
     * Default constructor.
     * @param component The component
     */
    UserAgentHeaderProvider(String component) {
        String userAgent = computeUserAgent(component);
        this.headers = Collections.singletonMap("User-Agent", userAgent);
    }

    /**
     * Returns the "User-Agent" header whose value should be added to the
     * google-cloud-java REST API calls. e.g.,
     * {@code User-Agent: Micrometer/1.0.0.RELEASE micrometer-registry-stackdriver/1.0.0.RELEASE}.
     */
    @Override
    public Map<String, String> getHeaders() {
        return this.headers;
    }

    private String computeUserAgent(String component) {
        String library = "micrometer-registry-" + component;
        String version = getClass().getPackage().getImplementationVersion();

        return "Micrometer/" + version + " " + library + "/" + version;
    }

}
