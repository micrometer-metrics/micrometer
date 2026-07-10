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

package io.micrometer.jakarta9.instrument.jms;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import io.micrometer.jakarta9.instrument.jms.JmsObservationDocumentation.*;

/**
 * Default implementation for {@link JmsPublishObservationConvention}.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class DefaultJmsPublishObservationConvention implements JmsPublishObservationConvention {

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue OPERATION_PUBLISH = KeyValue.of(LowCardinalityKeyNames.OPERATION, "publish");

    @Override
    public String getName() {
        return "jms.message.publish";
    }

    @Override
    public String getContextualName(JmsPublishObservationContext context) {
        return destinationName(context).getValue() + " publish";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(JmsPublishObservationContext context) {
        return KeyValues.of(exception(context), OPERATION_PUBLISH, temporaryDestination(context));
    }

    private KeyValue exception(JmsPublishObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(LowCardinalityKeyNames.EXCEPTION,
                    !simpleName.isEmpty() ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    protected KeyValue temporaryDestination(JmsPublishObservationContext context) {
        return JmsKeyValues.temporaryDestination(context.getCarrier());
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(JmsPublishObservationContext context) {
        return KeyValues.of(correlationId(context), destinationName(context), messageId(context));
    }

    protected KeyValue correlationId(JmsPublishObservationContext context) {
        return JmsKeyValues.conversationId(context.getCarrier());
    }

    protected KeyValue destinationName(JmsPublishObservationContext context) {
        return JmsKeyValues.destinationName(context.getCarrier());
    }

    protected KeyValue messageId(JmsPublishObservationContext context) {
        return JmsKeyValues.messageId(context.getCarrier());
    }

}
