/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.observation.annotation;

import java.lang.annotation.*;

/**
 * Container annotation that aggregates several {@link ObservationKeyValue} annotations.
 *
 * Can be used natively, declaring several nested {@link ObservationKeyValue} annotations.
 * Can also be used in conjunction with Java 8's support for repeatable annotations, where
 * {@link ObservationKeyValue} can simply be declared several times on the same parameter,
 * implicitly generating this container annotation.
 *
 * @author Seungyong Hong
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target({ ElementType.PARAMETER, ElementType.METHOD })
@Documented
public @interface ObservationKeyValues {

    ObservationKeyValue[] value();

}
