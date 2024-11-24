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
 * Default implementation for {@link JmsProcessObservationConvention}.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class DefaultJmsProcessObservationConvention implements JmsProcessObservationConvention {

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

    private static final KeyValue OPERATION_PROCESS = KeyValue.of(LowCardinalityKeyNames.OPERATION, "process");

    @Override
    public String getName() {
        return "jms.message.process";
    }

    @Override
    public String getContextualName(JmsProcessObservationContext context) {
        return destinationName(context).getValue() + " process";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(JmsProcessObservationContext context) {
        return KeyValues.of(exception(context), OPERATION_PROCESS, temporaryDestination(context));
    }

    private KeyValue exception(JmsProcessObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            String simpleName = error.getClass().getSimpleName();
            return KeyValue.of(LowCardinalityKeyNames.EXCEPTION,
                    !simpleName.isEmpty() ? simpleName : error.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    protected KeyValue temporaryDestination(JmsProcessObservationContext context) {
        return JmsKeyValues.temporaryDestination(context.getCarrier());
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(JmsProcessObservationContext context) {
        return KeyValues.of(correlationId(context), destinationName(context), messageId(context));
    }

    protected KeyValue correlationId(JmsProcessObservationContext context) {
        return JmsKeyValues.conversationId(context.getCarrier());
    }

    protected KeyValue destinationName(JmsProcessObservationContext context) {
        return JmsKeyValues.destinationName(context.getCarrier());
    }

    protected KeyValue messageId(JmsProcessObservationContext context) {
        return JmsKeyValues.messageId(context.getCarrier());
    }

}
