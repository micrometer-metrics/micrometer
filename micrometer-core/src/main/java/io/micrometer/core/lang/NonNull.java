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
import javax.annotation.meta.TypeQualifierNickname;
import java.lang.annotation.*;

/**
 * A common annotation to declare that annotated elements cannot be {@code null}.
 * Leverages JSR 305 meta-annotations to indicate nullability in Java to common tools with
 * JSR 305 support and used by Kotlin to infer nullability of the API.
 * <p>Should be used at parameter, return value, and field level. Method overrides should
 * repeat parent {@code @NonNull} annotations unless they behave differently.
 * <p>Use {@code @NonNullApi} (scope = parameters + return values) and/or {@code @NonNullFields}
 * (scope = fields) to set the default behavior to non-nullable in order to avoid annotating
 * your whole codebase with {@code @NonNull}.
 * <p>
 * NOTE: This file has been copied from {@code org.springframework.lang}.
 *
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @see io.micrometer.common.lang.NonNullApi
 * @see io.micrometer.common.lang.NonNullFields
 * @see io.micrometer.common.lang.Nullable
 * @deprecated Please use {@link io.micrometer.common.lang.NonNull} instead.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Nonnull
@TypeQualifierNickname
@Deprecated
public @interface NonNull {
}
