package io.micrometer.spring.autoconfigure.export.prometheus;

import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.web.bind.annotation.GetMapping;

public class PrometheusScrapeMvcEndpoint extends EndpointMvcAdapter {

    PrometheusScrapeMvcEndpoint(PrometheusScrapeEndpoint delegate) {
        super(delegate);
    }

    @GetMapping
    @Override
    public Object invoke() {
        return super.invoke();
    }
}
