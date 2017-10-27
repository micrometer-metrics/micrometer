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
package io.micrometer.core.instrument;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FilterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.filter.FilterProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.step.StepDouble;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class FilterRegistryTest {

    @Test
    public void it_shouldExcludeMetrics(){
        FilterProperties filterProperties = new FilterProperties();
        filterProperties.getFilter().put("test.filter","exclude");

        FilterRegistry f1 = new FilterRegistry(new SimpleMeterRegistry(), filterProperties);

        f1.counter("test.filter.me","tag1","val1").increment();
        f1.counter("test.keep.me","tag1","val1").increment();

        assertThat(f1.find("test.filter.me").meter()).isNotPresent();
        assertThat(f1.find("test.keep.me").meter()).isPresent();
    }

    @Test
    public void it_shouldCombineTagValues(){
        FilterProperties filterProperties = new FilterProperties();
        filterProperties.getCombine().put("test.combine","big|val2,val5;second;;;");

        FilterRegistry f1 = new FilterRegistry(createTestRegistry(), filterProperties);

        f1.counter("test.combine.me","big","val1","second","val4").increment();
        f1.counter("test.combine.me","big","val2","second","val3").increment();
        f1.counter("test.combine.me","big","val3","second","val2").increment();
        f1.counter("test.combine.me","big","val4","second","val1").increment();
        f1.counter("test.combine.me","big","val4","second","val1").increment();
        f1.counter("test.combine.me","big","val4","second","val1").increment();

        assertThat(f1.find("test.combine.me").tags("big","val2", "second", "other").firstValue()).hasValue(1.0);
        assertThat(f1.find("test.combine.me").tags("big","other", "second", "other").firstValue()).hasValue(5.0);
    }

    @Test
    public void it_shouldAllowSettingTheCombinedTagValue() throws InterruptedException {
        FilterProperties filterProperties = new FilterProperties();
        filterProperties.setDefaultCombinedBucketName("combined");
        filterProperties.getCombine().put("test.combine","big|val2");

        FilterRegistry f1 = new FilterRegistry(createTestRegistry(), filterProperties);


        f1.counter("test.combine.me","big","val1","second","val4").increment();
        f1.counter("test.combine.me","big","val1","second","val4").increment();
        f1.counter("test.combine.me","big","val2","second","val3").increment();


        f1.getMeters().forEach(m -> m.measure().forEach( e -> System.out.println("Meter:"+m+" - "+ e.getValue()+" "+ e.getStatistic()+" "+e.getValueFunction().get())));


        assertThat(f1.find("test.combine.me").tags("big","combined", "second", "val4").firstValue()).hasValue(2.0);
        assertThat(f1.find("test.combine.me").tags("big","val2", "second", "val3").firstValue()).hasValue(1.0);
    }

    private MeterRegistry createTestRegistry() {
        return new SimpleMeterRegistry() {
            @Override
            protected Counter newCounter(Meter.Id id) {
                return new SimpleCounter(id);
            }
        };
    }


    public class SimpleCounter extends AbstractMeter implements Counter {
        private final AtomicDouble value;

        /** Create a new instance. */
        public SimpleCounter(Id id) {
            super(id);
            this.value = new AtomicDouble();
        }

        @Override
        public void increment(double amount) {
            value.getAndAdd(amount);
        }

        @Override
        public double count() {
            return value.get();
        }
    }

    @Test
    public void it_ShouldAllowFilteringToBeTheDefault(){
        FilterProperties filterProperties = new FilterProperties();
        filterProperties.setDefaultFilter(FilterProperties.EXCLUDE);
        filterProperties.getFilter().put("test.include","include");
        filterProperties.getFilter().put("test.include.not","exclude");

        FilterRegistry f1 = new FilterRegistry(new SimpleMeterRegistry(), filterProperties);

        f1.counter("any.metric").increment();
        f1.counter("test.include.not.me").increment();
        f1.counter("test.include.me").increment();

        assertThat(f1.find("any.metric").meter()).isNotPresent();
        assertThat(f1.find("test.include.not.me").meter()).isNotPresent();
        assertThat(f1.find("test.include.me").meter()).isPresent();
    }


    @Test
    public void it_ShouldBeAbleToFilterAllMetricTypes(){
        FilterProperties filterProperties = new FilterProperties();
        filterProperties.setDefaultFilter(FilterProperties.EXCLUDE);

        FilterRegistry f1 = new FilterRegistry(new SimpleMeterRegistry(), filterProperties);

        f1.summary("any.summary").record(1);;
        f1.timer("some.timer").record(1, TimeUnit.MILLISECONDS);
        f1.register(new Meter.Id("a.meter", Collections.emptyList(), " ", " "), Meter.Type.Gauge,
            Arrays.asList(
                new Measurement(() -> 1.0, Statistic.Count),
                new Measurement(() -> 2.0, Statistic.Total)
            ));
        f1.gauge("a.gauge", 10);
        f1.more().counter(new Meter.Id("more.counter", Collections.emptyList(), " ", " "), 10, x -> x);
        f1.more().timeGauge(new Meter.Id("more.timeGauge", Collections.emptyList(), " ", " "), 10, TimeUnit.SECONDS, x -> x);
        f1.more().timer(new Meter.Id("more.timer", Collections.emptyList(), " ", " "), 10, x -> x, x -> x, TimeUnit.NANOSECONDS);
        f1.more().longTaskTimer(new Meter.Id("more.longTaskTimer", Collections.emptyList(), " ", " ")).duration(123, TimeUnit.MILLISECONDS);


        assertThat(f1.getMeters().size()).isEqualTo(0);
    }



    /**

metrics:
  combined.bucket.name: other
  filter:
    test.filter: exclude
   combine:
    test.combine: big|val1,val3;second;

     */

}
