/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.tomcat;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.then;
import static org.assertj.core.api.BDDAssertions.then;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.binder.http.DefaultHttpServerRequestObservationConvention;
import io.micrometer.core.instrument.binder.http.HttpServerRequestObservationContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert.TestObservationRegistryAssertReturningObservationContextAssert;

class ObservedValveTest {

    TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    ObservedValve observedValve = new ObservedValve(observationRegistry);

    @Test
    void shouldPopulateObservationAttributeForObservationFilterToReuse() throws ServletException, IOException {
        Request request = request();

        this.observedValve.invoke(request, response());

        then(request.getAttribute(Observation.class.getName())).isNotNull();
        thenObservationIsStartedAndStopped().hasHighCardinalityKeyValue("http.url", "UNKNOWN")
            .hasLowCardinalityKeyValue("exception", "none");
        then(observationRegistry).doesNotHaveAnyRemainingCurrentObservation();
    }

    @Test
    void shouldOverrideDefaultObservationConvention() throws ServletException, IOException {
        Request request = request();

        new ObservedValve(observationRegistry, new DefaultHttpServerRequestObservationConvention() {
            @Override
            public String getContextualName(HttpServerRequestObservationContext context) {
                return "HELLO";
            }
        }).invoke(request, response());

        thenObservationIsStartedAndStopped().hasContextualNameEqualTo("HELLO");
    }

    @Test
    void shouldHaveAsyncSupportedByDefault() {
        ObservedValve observedValve = new ObservedValve(observationRegistry);

        BDDAssertions.then(observedValve.isAsyncSupported()).isTrue();
    }

    private TestObservationRegistryAssertReturningObservationContextAssert thenObservationIsStartedAndStopped() {
        return then(observationRegistry).hasSingleObservationThat().hasBeenStarted().hasBeenStopped();
    }

    @Test
    void shouldPopulateObservationAttributeForObservationFilterToReuseWhenThereIsAnotherValveInChain()
            throws ServletException, IOException {
        Request request = request();

        new ObservedValve(observationRegistry) {
            @Override
            public Valve getNext() {
                return new MyValve();
            }
        }.invoke(request, response());

        then(request.getAttribute(Observation.class.getName())).isNotNull();
        thenObservationIsStartedAndStopped();
        then(observationRegistry).doesNotHaveAnyRemainingCurrentObservation();
    }

    @Test
    void shouldNotGenerateANewObservationWhenOneAlreadyPresent() throws ServletException, IOException {
        Request request = request();

        new ObservedValve(observationRegistry) {
            @Override
            public Valve getNext() {
                return new ObservedValve(observationRegistry);
            }
        }.invoke(request, response());

        then(request.getAttribute(Observation.class.getName())).isNotNull();
        thenObservationIsStartedAndStopped();
        then(observationRegistry).doesNotHaveAnyRemainingCurrentObservation();
    }

    private Request request() {
        Request request = new Request();
        request.setConnector(new Connector());
        request.setCoyoteRequest(new org.apache.coyote.Request());
        return request;
    }

    private Response response() {
        Response response = new Response();
        response.setConnector(new Connector());
        response.setCoyoteResponse(new org.apache.coyote.Response());
        return response;
    }

    static class MyValve extends ValveBase {

        @Override
        public void invoke(Request request, Response response) {

        }

    }

}
