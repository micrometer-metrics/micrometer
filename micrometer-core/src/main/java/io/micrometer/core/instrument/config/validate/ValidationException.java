/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.config.validate;

import io.micrometer.core.annotation.Incubating;

import java.util.stream.Collectors;

/**
 * Turns a set of {@link Validated.Invalid} into a throwable exception, which is used to
 * throw an unchecked exception at runtime when one or more properties are invalid.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
@Incubating(since = "1.5.0")
public class ValidationException extends IllegalStateException {

    private final Validated<?> validation;

    public ValidationException(Validated<?> validation) {
        super(validation.failures()
            .stream()
            .map(invalid -> invalid.getProperty() + " was '"
                    + (invalid.getValue() == null ? "null" : invalid.getValue()) + "' but it " + invalid.getMessage())
            .collect(Collectors.joining("\n", validation.failures().size() > 1 ? "Multiple validation failures:\n" : "",
                    "")));
        this.validation = validation;
    }

    public Validated<?> getValidation() {
        return validation;
    }

}
