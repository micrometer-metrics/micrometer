package io.micrometer.spring.samples.components.okhttp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OkHttpMicrometerInterceptor implements Interceptor {

	private final String requestsMetricName;
	private final boolean recordRequestPercentiles;
	Logger logger = LoggerFactory.getLogger(OkHttpMicrometerInterceptor.class);
	private MeterRegistry meterRegistry;


	public OkHttpMicrometerInterceptor(MeterRegistry meterRegistry, String requestsMetricName, boolean recordRequestPercentiles) {

		this.meterRegistry = meterRegistry;
		this.requestsMetricName = requestsMetricName;
		this.recordRequestPercentiles = recordRequestPercentiles;
	}


	@Override
	public Response intercept(Chain chain) throws IOException {
		Request request = chain.request();

		long startTime = System.nanoTime();
		Response response = null;
		try {
			response = chain.proceed(request);
			return response;
		} finally {
			getTimeBuilder(request, response).register(this.meterRegistry)
					.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

		}
	}

	private Timer.Builder getTimeBuilder(Request request,
										 Response response) {

		List<Tag> tags = Arrays.asList(
				Tag.of("method", request.method()),
				Tag.of("uri", request.url().toString()), //make it configurable
				Tag.of("status", "" + response.code()),
				Tag.of("clientName", request.url().host())
		);
		Timer.Builder builder = Timer.builder(this.requestsMetricName)
				.tags(tags)
				.description("Timer of OkHttp operation");
		if (this.recordRequestPercentiles) {
			builder = builder.publishPercentileHistogram();
		}
		return builder;
	}
}
