package io.micrometer.spring.samples.components.okhttp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static io.micrometer.spring.samples.components.okhttp.OkHttpMicrometerInterceptor.MICROMETER_URI_HEADER;

@RestController
public class OkHttpController {

	private OkHttpClient client;
	private ObjectMapper mapper;

	public OkHttpController(OkHttpClient client, ObjectMapper mapper) {

		this.client = client;
		this.mapper = mapper;
	}


	@GetMapping("/ip")
	public JsonNode ip() throws IOException {
		Request request = new Request.Builder()
				.url("http://httpbin.org/ip")
				.header("User-Agent", "OkHttp Example")
				.build();

		try (Response response = client.newCall(request).execute()) {
			return mapper.readTree(response.body().bytes());
		}
	}

	@GetMapping("/get")
	public JsonNode get() throws IOException {
		Request request = new Request.Builder()
				.url("http://httpbin.org/get")
				.header(MICROMETER_URI_HEADER, "get")
				.header("User-Agent", "OkHttp Example")
				.build();


		try (Response response = client.newCall(request).execute()) {
			return mapper.readTree(response.body().bytes());
		}
	}
}
