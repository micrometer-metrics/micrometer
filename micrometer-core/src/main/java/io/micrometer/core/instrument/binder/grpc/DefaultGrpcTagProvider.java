/*
 * Copyright 2021 VMware, Inc.
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

package io.micrometer.core.instrument.binder.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

public class DefaultGrpcTagProvider implements GrpcTagProvider {

    /**
     * The metrics tag key that belongs to the called service name.
     */
    private static final String TAG_SERVICE_NAME = "service";
    /**
     * The metrics tag key that belongs to the called method name.
     */
    private static final String TAG_METHOD_NAME = "method";
    /**
     * The metrics tag key that belongs to the type of the called method.
     */
    private static final String TAG_METHOD_TYPE = "methodType";
    /**
     * The metrics tag key that belongs to the result status code.
     */
    private static final String TAG_STATUS_CODE = "statusCode";

    @Override
    public Iterable<Tag> getBaseTags(MethodDescriptor<?, ?> method) {
        return Tags.of(
                TAG_SERVICE_NAME, method.getServiceName(),
                TAG_METHOD_NAME, method.getBareMethodName(),
                TAG_METHOD_TYPE, method.getType().name()
        );
    }

    @Override
    public Iterable<Tag> getTagsForResult(MethodDescriptor<?, ?> method, Status.Code code) {
        return Tags.of(TAG_STATUS_CODE, code.name());
    }
}
