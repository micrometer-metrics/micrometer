package io.micrometer.spring.samples.components.okhttp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
		OkHttpClient client = new OkHttpClient.Builder()
				.addInterceptor(new OkHttpMicrometerInterceptor(meterRegistry,
						metricsProperties.getWeb().getClient().getRequestsMetricName(),
						metricsProperties.getWeb().getClient().isRecordRequestPercentiles()))
				.build();
		return client;
	}
}

