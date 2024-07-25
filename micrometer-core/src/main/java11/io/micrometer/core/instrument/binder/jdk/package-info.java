/*
 * Copyright 2022 VMware, Inc.
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

/**
 * Instrumentation of JDK classes. Deprecated since 1.13.0 use the micrometer-java11
 * module instead.
 */
// Note we can't use the @deprecated JavaDoc tag due to compiler bug JDK-8160601
@NonNullApi
@NonNullFields
package io.micrometer.core.instrument.binder.jdk;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
