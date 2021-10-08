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
