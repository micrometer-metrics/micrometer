/*
 * Copyright 2002-2017 the original author or authors.
 * Copyright 2017-2021 VMware, Inc.
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
package io.micrometer.core.lang;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.*;

/**
 * A common annotation to declare that fields are to be considered as non-nullable by
 * default for a given package.
 * <p>
 * Leverages JSR-305 meta-annotations to indicate nullability in Java to common tools with
 * JSR-305 support and used by Kotlin to infer nullability of the API.
 * <p>
 * Should be used at package level in association with {@link Nullable} annotations at
 * field level.
 * <p>
 * NOTE: This file has been copied from {@code org.springframework.lang}.
 *
 * @author Sebastien Deleuze
 * @see io.micrometer.common.lang.NonNullFields
 * @see io.micrometer.common.lang.Nullable
 * @see io.micrometer.common.lang.NonNull
 * @deprecated Please use {@link io.micrometer.common.lang.NonNullFields} instead.
 */
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Nonnull
@TypeQualifierDefault(ElementType.FIELD)
@Deprecated
public @interface NonNullFields {

}
