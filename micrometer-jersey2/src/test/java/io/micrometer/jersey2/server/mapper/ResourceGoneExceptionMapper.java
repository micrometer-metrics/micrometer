package io.micrometer.jersey2.server.mapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import io.micrometer.jersey2.server.exception.ResourceGoneException;

public class ResourceGoneExceptionMapper implements ExceptionMapper<ResourceGoneException> {

    @Override
    public Response toResponse(ResourceGoneException exception) {
        return Response.status(Status.GONE).build();
    }
}
