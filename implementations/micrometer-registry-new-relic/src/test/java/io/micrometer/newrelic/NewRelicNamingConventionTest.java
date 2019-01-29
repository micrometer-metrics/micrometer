/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.newrelic;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NewRelicNamingConvention}.
 *
 * @author Mustafa Shabib
 */
class NewRelicNamingConventionTest {
  private final NewRelicNamingConvention newRelicNamingConvention = new NewRelicNamingConvention();

  private static final String VALID_NAME = "validName";
  private static final String INVALID_NAME = "invalid-name";

  @Test
  void name() {
    String validString = newRelicNamingConvention.name(VALID_NAME, Type.COUNTER);
    assertThat(validString).isEqualTo(VALID_NAME);
  }

  @Test
  void nameShouldStripInvalidCharacters() {
    String validString = newRelicNamingConvention.name(INVALID_NAME, Type.COUNTER);
    assertThat(validString).isEqualTo("invalid_name");
  }

  @Test
  void tagKey() {
    String validString = newRelicNamingConvention.tagKey(VALID_NAME);
    assertThat(validString).isEqualTo(VALID_NAME);
  }

  @Test
  void tagKeyShouldStripInvalidCharacters() {
    String validString = newRelicNamingConvention.tagKey(INVALID_NAME);
    assertThat(validString).isEqualTo("invalid_name");
  }

  @Test
  void tagValue() {
    String validString = newRelicNamingConvention.tagValue(VALID_NAME);
    assertThat(validString).isEqualTo(VALID_NAME);
  }

  @Test
  void tagValueShouldNotStripInvalidCharacters() {
    String validString = newRelicNamingConvention.tagValue(INVALID_NAME);
    assertThat(validString).isEqualTo(INVALID_NAME);
  }
}
