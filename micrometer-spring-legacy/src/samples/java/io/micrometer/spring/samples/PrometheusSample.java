/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.samples;

import com.netflix.hystrix.contrib.javanica.aop.aspectj.HystrixCommandAspect;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;

@SpringBootApplication(scanBasePackages = "io.micrometer.spring.samples.components")
@EnableScheduling
public class PrometheusSample {
    public static void main(String[] args) {
        new SpringApplicationBuilder(PrometheusSample.class).profiles("prometheus").run(args);
    }

    @Configuration
    public class HystrixConfiguration {


        @Autowired(required = false)
        private HystrixConcurrencyStrategy existingConcurrencyStrategy;

        @Autowired(required = false)
        private MeterRegistry meterRegistry;


        @Bean
        public HystrixCommandAspect hystrixAspect(){
            return new HystrixCommandAspect();
        }

        @PostConstruct
        public void init() {
            // Keeps references of existing Hystrix plugins.
            HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance()
                .getEventNotifier();
            HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance()
                .getMetricsPublisher();
            HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance()
                .getPropertiesStrategy();
            HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins.getInstance()
                .getCommandExecutionHook();

            HystrixPlugins.reset();

            // Registers existing plugins excepts the Concurrent Strategy plugin.
//            HystrixPlugins.getInstance().registerConcurrencyStrategy(new DomoHystrixContextConcurrencyStrategy(existingConcurrencyStrategy, toeProvider));
            HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
            HystrixPlugins.getInstance().registerMetricsPublisher(new MicrometerMetricsPublisher(meterRegistry));
//            HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
            HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
            HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook);
        }
    }
}
