/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.noop.NoOpRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.assertj.core.api.Assertions.assertThat;

class MeterFactoryTest {
  private MeterRegistry registry;

  @ParameterizedTest
  @ArgumentsSource(MeterRegistriesProvider.class)
  @DisplayName("meters with the same name and tags are registered once")
  void uniqueMeters(MeterRegistry registry) {
    MeterFactory.Config.setRegistry(registry);

    MeterFactory.counter("foo");
    MeterFactory.counter("foo");

    assertThat(MeterFactory.Config.registry().getMeters().size()).isEqualTo(1);
  }

  @ParameterizedTest
  @ArgumentsSource(MeterRegistriesProvider.class)
  @DisplayName("meters with the same name and tags are registered once")
  void longTaskTimer(MeterRegistry registry) {
    MeterFactory.Config.setRegistry(registry);

    MeterFactory.counter("foo");
    MeterFactory.counter("foo");

    assertThat(MeterFactory.Config.registry().getMeters().size()).isEqualTo(1);
  }

  @Test
  @DisplayName("no op registry is supported and uses the same internal meters to minimize memory impact")
  void noopMeter() {
    MeterFactory.Config.setRegistry(new NoOpRegistry());

    Counter a = MeterFactory.counter("foo");
    a.increment();
    Counter b = MeterFactory.counter("foo2");
    b.increment(10D);
    assertThat(a).isSameAs(b);

    assertThat(MeterFactory.Config.registry().getMeters().size())
            .as("NoOpRegistry is always empty")
            .isEqualTo(0);
  }

  @BeforeEach
  void init() {
    //Save off existing registry
    registry = MeterFactory.Config.registry();
  }

  @AfterEach
  void tearDown() {
    //Reset registry
    MeterFactory.Config.setRegistry(registry);
  }




}
