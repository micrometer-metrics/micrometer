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
	private Function<Response, Optional<String>> statusMapper;
	private MeterRegistry meterRegistry;


	OkHttpMicrometerInterceptor(MeterRegistry meterRegistry, String requestsMetricName, boolean recordRequestPercentiles, Function<Request, Optional<String>> urlMapper, Function<Response, Optional<String>> statusMapper, boolean emptyUriIfNoMatch) {
		this.meterRegistry = meterRegistry;
		this.requestsMetricName = requestsMetricName;
		this.recordRequestPercentiles = recordRequestPercentiles;
		this.urlMapper = urlMapper;
		this.statusMapper = statusMapper;
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
		if (!emptyUriIfNoMatch) {
			defaultUri = request.url().toString();
		}

		Optional<String> interceptorUri = urlMapper.apply(request);
		Optional<String> headerUri = Optional.ofNullable(request.header(MICROMETER_URI_HEADER));


		String status = statusMapper.apply(response).orElse("" + response.code());

		String uri = interceptorUri.orElse(headerUri.orElse(defaultUri));

		List<Tag> tags = Arrays.asList(
				Tag.of("method", request.method()),
				Tag.of("uri", uri),
				Tag.of("status", status),
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

	public static class Builder {


		private MeterRegistry meterRegistry;
		private String name;
		private boolean recordRequestPercentiles = false;

		private Function<Request, Optional<String>> uriMapper = (request) -> Optional.empty();
		private Function<Response, Optional<String>> statusMapper = (request) -> Optional.empty();
		private boolean emptyUriIfNoMatch = true;

		public Builder meterRegistry(MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		public Builder metricsName(String name) {
			this.name = name;
			return this;
		}

		public Builder recordRequestPercentiles(boolean recordRequestPercentiles) {
			this.recordRequestPercentiles = recordRequestPercentiles;
			return this;
		}

		public Builder statusMapper(Function<Response, Optional<String>> statusMapper) {
			this.statusMapper = statusMapper;
			return this;
		}

		public Builder uriMapper(Function<Request, Optional<String>> uriMapper) {
			this.uriMapper = uriMapper;
			return this;
		}

		public Builder emptyUriIfNoMatch(boolean empty) {
			this.emptyUriIfNoMatch = empty;
			return this;
		}

		public OkHttpMicrometerInterceptor build() {
			if (meterRegistry == null || name == null) {
				throw new IllegalStateException("Need to specify meterRegistry and name");
			}

			return new OkHttpMicrometerInterceptor(meterRegistry, name, recordRequestPercentiles, uriMapper, statusMapper, emptyUriIfNoMatch);
		}


	}
}
