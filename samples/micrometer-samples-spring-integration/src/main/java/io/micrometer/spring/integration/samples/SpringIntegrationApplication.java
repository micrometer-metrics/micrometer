/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.integration.samples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.ws.SimpleWebServiceOutboundGateway;
import org.springframework.integration.ws.WebServiceHeaders;
import org.springframework.integration.xml.transformer.XPathTransformer;

@Configuration
@SpringBootApplication
@IntegrationComponentScan
public class SpringIntegrationApplication {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(SpringIntegrationApplication.class, args);
        TempConverter converter = ctx.getBean(TempConverter.class);

        for (int i = 0; i < 1000; i++) {
            System.out.println(converter.fahrenheitToCelcius(68.0f));
            Thread.sleep(10);
        }

        ctx.close();
    }

    @Bean
    public IntegrationFlow convert() {
        return f -> f
            .transform(payload -> "<FahrenheitToCelsius xmlns=\"https://www.w3schools.com/xml/\">" + "<Fahrenheit>"
                    + payload + "</Fahrenheit>" + "</FahrenheitToCelsius>", e -> e.id("toXml"))
            .enrichHeaders(
                    h -> h.header(WebServiceHeaders.SOAP_ACTION, "https://www.w3schools.com/xml/FahrenheitToCelsius"))
            .handle(new SimpleWebServiceOutboundGateway("https://www.w3schools.com/xml/tempconvert.asmx"),
                    e -> e.id("w3schools"))
            .transform(new XPathTransformer("/*[local-name()=\"FahrenheitToCelsiusResponse\"]"
                    + "/*[local-name()=\"FahrenheitToCelsiusResult\"]"), e -> e.id("toResponse"));
    }

    @MessagingGateway
    public interface TempConverter {

        @Gateway(requestChannel = "convert.input")
        float fahrenheitToCelcius(float fahren);

    }

}
