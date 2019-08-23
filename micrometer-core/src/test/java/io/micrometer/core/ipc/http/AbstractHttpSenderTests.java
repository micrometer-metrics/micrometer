package io.micrometer.core.ipc.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@ExtendWith(WiremockResolver.class)
abstract class AbstractHttpSenderTests {
    HttpSender httpSender;

    abstract void setHttpSender();

    @BeforeEach
    void setup() {
        setHttpSender();
    }

    @ParameterizedTest
    @EnumSource(HttpSender.Method.class)
    void requestSent(HttpSender.Method method, @Wiremock WireMockServer server) throws Throwable {
        server.stubFor(any(urlEqualTo("/metrics")));

        httpSender.newRequest(server.baseUrl() + "/metrics")
                .withMethod(method)
                .withBasicAuthentication("user", "pass")
                .accept("customAccept")
                .withHeader("customHeader", "customHeaderValue")
                .send();

        server.verify(WireMock.requestMadeFor(request ->
                MatchResult.aggregate(
                        MatchResult.of(request.getMethod().getName().equals(method.name())),
                        MatchResult.of(request.getUrl().equals("/metrics"))
                ))
                .withBasicAuth(new BasicCredentials("user", "pass"))
                .withHeader("Accept", equalTo("customAccept"))
                .withHeader("customHeader", equalTo("customHeaderValue")));
    }
}
