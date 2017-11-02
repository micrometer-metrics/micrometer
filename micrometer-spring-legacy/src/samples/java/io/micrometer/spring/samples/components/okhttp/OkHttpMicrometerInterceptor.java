package io.micrometer.spring.samples.components.okhttp;

import io.micrometer.core.instrument.Meter;
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
	private final Function<Request, String> urlMapper;
	private MeterRegistry meterRegistry;


	OkHttpMicrometerInterceptor(MeterRegistry meterRegistry, String requestsMetricName, boolean recordRequestPercentiles, Function<Request, String> urlMapper) {
		this.meterRegistry = meterRegistry;
		this.requestsMetricName = requestsMetricName;
		this.recordRequestPercentiles = recordRequestPercentiles;
		this.urlMapper = urlMapper;
	}

	@Override
	public Response intercept(Chain chain) throws IOException {
		Request request = chain.request();

		long startTime = System.nanoTime();
		Response response = null;
		IOException e = null;
		try {
			response = chain.proceed(request);
			return response;
		} catch (IOException err) {
			e = err;
			throw e;
		} finally {
			getTimeBuilder(request, response, e).register(this.meterRegistry)
					.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

		}
	}

	private Timer.Builder getTimeBuilder(Request request, Response response, IOException exception) {

		List<Tag> tags = Arrays.asList(
				Tag.of("method", request.method()),
				Tag.of("uri", urlMapper.apply(request)),
				Tag.of("status", getStatusMessage(response, exception)),
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

	private String getStatusMessage(Response response, IOException exception) {
		if (exception != null) {
			return "IO_ERROR";
		}

		if (response == null) {
			return "CLIENT_ERROR";
		}
		return "" + response.code();
	}

	public static class Builder {


		private MeterRegistry meterRegistry;
		private String name = "okhttp.client";
		private boolean recordRequestPercentiles = false;
		private Function<Request, String> uriMapper = (request) -> Optional.ofNullable(request.header(MICROMETER_URI_HEADER)).orElse("none");

		public Builder(MeterRegistry meterRegistry) {

			this.meterRegistry = meterRegistry;
		}

		public Builder metricsName(String name) {
			this.name = name;
			return this;
		}

		public Builder recordRequestPercentiles(boolean recordRequestPercentiles) {
			this.recordRequestPercentiles = recordRequestPercentiles;
			return this;
		}


		public Builder uriMapper(Function<Request, String> uriMapper) {
			this.uriMapper = uriMapper;
			return this;
		}

		public OkHttpMicrometerInterceptor build() {
			if (meterRegistry == null) {
				throw new IllegalStateException("Need to specify meterRegistry and name");
			}

			return new OkHttpMicrometerInterceptor(meterRegistry, name, recordRequestPercentiles, uriMapper);
		}


	}
}
