package io.micrometer.spring.samples.components.okhttp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class OkHttpMicrometerInterceptor implements Interceptor {

	public static final String MICROMETER_URI_HEADER = "MICROMETER_URI_HEADER";
	private final String requestsMetricName;
	private final boolean recordRequestPercentiles;
	private final Function<Request, Optional<String>> urlMapper;
	private final boolean emptyUriIfNoMatch;

	private MeterRegistry meterRegistry;


	public OkHttpMicrometerInterceptor(MeterRegistry meterRegistry, String requestsMetricName, boolean recordRequestPercentiles) {

		this.meterRegistry = meterRegistry;
		this.requestsMetricName = requestsMetricName;
		this.recordRequestPercentiles = recordRequestPercentiles;
		this.urlMapper = (request) -> Optional.empty();
		this.emptyUriIfNoMatch = true;
	}

	public OkHttpMicrometerInterceptor(MeterRegistry meterRegistry, String requestsMetricName, boolean recordRequestPercentiles, Function<Request, Optional<String>> urlMapper) {
		this.meterRegistry = meterRegistry;
		this.requestsMetricName = requestsMetricName;
		this.recordRequestPercentiles = recordRequestPercentiles;
		this.urlMapper = urlMapper;
		this.emptyUriIfNoMatch = true;
	}

	public OkHttpMicrometerInterceptor(MeterRegistry meterRegistry, String requestsMetricName, boolean recordRequestPercentiles, Function<Request, Optional<String>> urlMapper, boolean emptyUriIfNoMatch) {
		this.meterRegistry = meterRegistry;
		this.requestsMetricName = requestsMetricName;
		this.recordRequestPercentiles = recordRequestPercentiles;
		this.urlMapper = urlMapper;
		this.emptyUriIfNoMatch = emptyUriIfNoMatch;
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

		String defaultUri = "";
		if(emptyUriIfNoMatch) {
			defaultUri = request.url().toString();
		}

		Optional<String> interceptorUri = urlMapper.apply(request);
		Optional<String> headerUri = Optional.ofNullable(request.header(MICROMETER_URI_HEADER));


		String uri = interceptorUri.orElse(headerUri.orElse(defaultUri));

		List<Tag> tags = Arrays.asList(
				Tag.of("method", request.method()),
				Tag.of("uri", uri),
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
