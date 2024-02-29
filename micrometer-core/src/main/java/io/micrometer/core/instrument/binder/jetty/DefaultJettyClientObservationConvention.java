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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.common.KeyValues;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;

/**
 * Default implementation of {@link JettyClientObservationConvention}.
 *
 * @since 1.11.0
 * @deprecated since 1.13.0 in favor of the micrometer-jetty12 module
 */
@Deprecated
public class DefaultJettyClientObservationConvention implements JettyClientObservationConvention {

    public static DefaultJettyClientObservationConvention INSTANCE = new DefaultJettyClientObservationConvention();

    @Override
    public KeyValues getLowCardinalityKeyValues(JettyClientContext context) {
        Request request = context.getCarrier();
        Result result = context.getResponse();
        return KeyValues.of(JettyClientKeyValues.method(request), JettyClientKeyValues.host(request),
                JettyClientKeyValues.uri(request, result, context.getUriPatternFunction()),
                JettyClientKeyValues.exception(result), JettyClientKeyValues.status(result),
                JettyClientKeyValues.outcome(result));
    }

    @Override
    public String getName() {
        return JettyClientMetrics.DEFAULT_JETTY_CLIENT_REQUESTS_TIMER_NAME;
    }

}
