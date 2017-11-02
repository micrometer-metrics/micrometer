package io.micrometer.spring.samples.components.okhttp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.function.Function;

import static io.micrometer.spring.samples.components.okhttp.OkHttpMicrometerInterceptor.MICROMETER_URI_HEADER;

@Configuration
public class OkHttpConfiguration {

	@Bean
	public OkHttpClient okHttpClient(MeterRegistry meterRegistry, MetricsProperties metricsProperties) {

		Function<Request, String> urlMapper = request -> {
			if (request.url().toString().endsWith("ip")) {
				return request.url().host();
			}
			return Optional.ofNullable(request.header(MICROMETER_URI_HEADER)).orElse("none");
		};


		OkHttpClient client = new OkHttpClient.Builder()
				.addInterceptor(new OkHttpMicrometerInterceptor.Builder(meterRegistry)
						.metricsName(metricsProperties.getWeb().getClient().getRequestsMetricName())
						.recordRequestPercentiles(metricsProperties.getWeb().getClient().isRecordRequestPercentiles())
						.uriMapper(urlMapper)
						.build())
				.build();
		return client;
	}
}

