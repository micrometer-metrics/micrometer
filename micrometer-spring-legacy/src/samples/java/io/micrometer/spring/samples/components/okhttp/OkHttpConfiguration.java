package io.micrometer.spring.samples.components.okhttp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.function.Function;

@Configuration
public class OkHttpConfiguration {


	private final MeterRegistry meterRegistry;
	private final MetricsProperties metricsProperties;

	public OkHttpConfiguration(MeterRegistry meterRegistry, MetricsProperties metricsProperties) {

		this.meterRegistry = meterRegistry;
		this.metricsProperties = metricsProperties;
	}

	@Bean
	public OkHttpClient okHttpClient() {

		Function<Request, Optional<String>> urlMapper = request -> {
			if (request.url().toString().endsWith("ip")) {
				return Optional.of(request.url().host());
			}
			return Optional.empty();
		};


		OkHttpClient client = new OkHttpClient.Builder()
				.addInterceptor(new OkHttpMicrometerInterceptor.Builder()
						.meterRegistry(meterRegistry)
						.emptyUriIfNoMatch(true)
						.metricsName(metricsProperties.getWeb().getClient().getRequestsMetricName())
						.recordRequestPercentiles(metricsProperties.getWeb().getClient().isRecordRequestPercentiles())
						.uriMapper(urlMapper)
						.build())
				.build();
		return client;
	}
}

