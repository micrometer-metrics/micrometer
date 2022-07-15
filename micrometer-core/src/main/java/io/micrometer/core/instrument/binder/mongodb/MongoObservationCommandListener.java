/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.RequestContext;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Implement MongoDB's {@link CommandListener} using Micrometer's {@link Observation} API.
 *
 * @see https://github.com/openzipkin/brave/blob/release-5.13.0/instrumentation/mongodb/src/main/java/brave/mongodb/TraceMongoCommandListener.java
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @author Greg Turnquist
 * @since 1.10.0
 */
public final class MongoObservationCommandListener
        implements CommandListener, Observation.ObservationConventionAware<MongoHandlerObservationConvention> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(MongoObservationCommandListener.class);

    private static final MongoHandlerObservationConvention DEFAULT_CONVENTION = new DefaultMongoHandlerObservationConvention();

    private final ObservationRegistry observationRegistry;

    private MongoHandlerObservationConvention observationConvention;

    public MongoObservationCommandListener(ObservationRegistry observationRegistry) {

        this.observationRegistry = observationRegistry;
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {

        if (log.isDebugEnabled()) {
            log.debug("Instrumenting the command started event");
        }

        String databaseName = event.getDatabaseName();

        if ("admin".equals(databaseName)) {
            return; // don't instrument commands like "endSessions"
        }

        RequestContext requestContext = event.getRequestContext();

        if (requestContext == null) {
            return;
        }

        Observation parent = observationFromContext(requestContext);

        if (log.isDebugEnabled()) {
            log.debug("Found the following observation passed from the mongo context [" + parent + "]");
        }

        if (parent == null) {
            return;
        }

        setupObservability(event, requestContext);
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {

        if (event.getRequestContext() == null) {
            return;
        }

        Observation observation = event.getRequestContext().getOrDefault(Observation.class, null);
        if (observation == null) {
            return;
        }

        MongoHandlerContext context = event.getRequestContext().get(MongoHandlerContext.class);
        context.setCommandSucceededEvent(event);

        if (log.isDebugEnabled()) {
            log.debug("Command succeeded - will stop observation [" + observation + "]");
        }

        observation.stop();
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {

        if (event.getRequestContext() == null) {
            return;
        }

        Observation observation = event.getRequestContext().getOrDefault(Observation.class, null);
        if (observation == null) {
            return;
        }

        MongoHandlerContext context = event.getRequestContext().get(MongoHandlerContext.class);
        context.setCommandFailedEvent(event);

        if (log.isDebugEnabled()) {
            log.debug("Command failed - will stop observation [" + observation + "]");
        }

        observation.error(event.getThrowable());
        observation.stop();
    }

    /**
     * Extract the {@link Observation} from MongoDB's {@link RequestContext}.
     * @param context
     * @return
     */
    private static Observation observationFromContext(RequestContext context) {

        Observation observation = context.getOrDefault(Observation.class, null);

        if (observation != null) {

            if (log.isDebugEnabled()) {
                log.debug("Found a observation in mongo context [" + observation + "]");
            }
            return observation;
        }

        if (log.isDebugEnabled()) {
            log.debug("No observation was found - will not create any child spans");
        }

        return null;
    }

    private void setupObservability(CommandStartedEvent event, RequestContext requestContext) {

        MongoHandlerContext observationContext = new MongoHandlerContext(event, requestContext);

        Observation observation = MongoObservation.MONGODB_COMMAND_OBSERVATION
                .observation(this.observationConvention, DEFAULT_CONVENTION, observationContext,
                        this.observationRegistry) //
                .contextualName(observationContext.getContextualName()) //
                .start();

        requestContext.put(Observation.class, observation);
        requestContext.put(MongoHandlerContext.class, observationContext);

        if (log.isDebugEnabled()) {
            log.debug("Created a child observation  [" + observation
                    + "] for mongo instrumentation and put it in mongo context");
        }
    }

    @Override
    public void setObservationConvention(MongoHandlerObservationConvention mongoHandlerObservationConvention) {
        this.observationConvention = mongoHandlerObservationConvention;
    }

}
