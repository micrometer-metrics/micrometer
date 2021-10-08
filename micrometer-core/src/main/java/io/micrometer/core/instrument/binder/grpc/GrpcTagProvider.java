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

public interface GrpcTagProvider {
    /**
     * Provides tags to be associated with metrics for the given {@code mothod}
     * @param method The method the tags will be created for.
     * @return tags to associate with metrics for the method
     */
    Iterable<Tag> getBaseTags(final MethodDescriptor<?, ?> method);
    /**
     * Provides tags to be associated with the result of the given {@code method} and {@code code}.
     * These will be applied in addition to the result of {@code getBaseTags}
     * @param method The method the tags will be created for.
     * @param code The status code of the result.
     * @return tags to associate with metrics for the method's response
     */
    Iterable<Tag> getTagsForResult(final MethodDescriptor<?, ?> method, Status.Code code);

}
