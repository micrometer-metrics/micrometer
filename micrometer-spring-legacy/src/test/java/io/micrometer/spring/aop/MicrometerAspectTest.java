package io.micrometer.spring.aop;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Service;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;

import static org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD;

@SpringBootTest
@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
@RunWith(SpringRunner.class)
public class MicrometerAspectTest {

    @Inject
    private DummyService dummyService;

    @Inject
    private SimpleMeterRegistry simpleMeterRegistry;

    @Service
    @Timed
    public static class DummyService {

        @Timed()
        public Object doSomethingAnnotated(Object argument) {
            return argument;
        }

        public Object doSomething(Object argument) {
            return argument;
        }
    }

    @ComponentScan
    @Configuration
    @EnableAspectJAutoProxy
    public static class MicrometerAspectTestConfig {

        @Bean
        public SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public MicrometerAspect micrometerAspect(MeterRegistry meterRegistry) {
            return new MicrometerAspect(meterRegistry);
        }
    }

    @Test
    public void assertTypeMatchMonitoring() {
        Object arg = new Object();
        Object response = dummyService.doSomething(arg);
        Assertions.assertThat(arg).isSameAs(response);
        assertMethodMonitored("doSomething");
    }

    private void assertMethodMonitored(String method) {
        Assertions.assertThat(simpleMeterRegistry.getMeters())
            .hasSize(1)
            .allSatisfy(meter -> {
                Assertions.assertThat(meter.getId().getType()).hasSameClassAs(Meter.Type.TIMER);
                Assertions.assertThat(meter).isExactlyInstanceOf(CumulativeTimer.class);
                Assertions.assertThat(((CumulativeTimer) meter).count()).isEqualTo(1);
                Assertions.assertThat(meter.getId().getTags())
                    .contains(
                        Tag.of("class", AopUtils.getTargetClass(MicrometerAspectTest.this.dummyService).getName()),
                        Tag.of("method", method)
                    );
            });
    }

    @Test
    public void assertMethodMatchMonitoring() {
        final Object arg = new Object();
        Assertions.assertThat(dummyService.doSomethingAnnotated(arg)).isSameAs(arg);
        assertMethodMonitored("doSomethingAnnotated");
    }


}
