package io.micrometer.spring.autoconfigure.export.prometheus;

import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;

public class PrometheusScrapeMvcEndpoint extends EndpointMvcAdapter {

    PrometheusScrapeMvcEndpoint(PrometheusScrapeEndpoint delegate) {
        super(delegate);
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Override
    public Object invoke() {
        return super.invoke();
    }
}
