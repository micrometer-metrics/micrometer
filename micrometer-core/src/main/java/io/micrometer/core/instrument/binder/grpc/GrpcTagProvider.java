package io.micrometer.core.instrument.binder.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Tag;

public interface GrpcTagProvider {
    /**
     * Provides tags to be associated with metrics for the given {@code mothod}
     * @param method The method the tags will be created for.
     * @return tags to associate with metrics for the request and response exchange
     */
    Iterable<Tag> getBaseTags(final MethodDescriptor<?, ?> method);
    Iterable<Tag> getTagsForResult(final MethodDescriptor<?, ?> method, Status.Code code);

}
