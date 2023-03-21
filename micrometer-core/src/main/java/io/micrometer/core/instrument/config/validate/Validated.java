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

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.annotation.Incubating;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * Validation support for
 * {@link io.micrometer.core.instrument.config.MeterRegistryConfig}.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
@Incubating(since = "1.5.0")
public interface Validated<T> extends Iterable<Validated<T>> {

    boolean isValid();

    default boolean isInvalid() {
        return !isValid();
    }

    default List<Invalid<?>> failures() {
        return stream(spliterator(), false).filter(Validated::isInvalid)
            .map(v -> (Invalid<T>) v)
            .collect(Collectors.toList());
    }

    static Secret validSecret(String property, String value) {
        return new Secret(property, value);
    }

    static <T> None<T> none() {
        return new None<>();
    }

    static <T> Valid<T> valid(String property, @Nullable T value) {
        return new Valid<>(property, value);
    }

    static <T> Invalid<T> invalid(String property, @Nullable Object value, String message, InvalidReason reason) {
        return invalid(property, value, message, reason, null);
    }

    static <T> Invalid<T> invalid(String property, @Nullable Object value, String message, InvalidReason reason,
            @Nullable Throwable exception) {
        return new Invalid<>(property, value, message, reason, exception);
    }

    default Validated<?> and(Validated<?> validated) {
        if (this instanceof None) {
            return validated;
        }
        return new Either(this, validated);
    }

    <U> Validated<U> map(Function<T, U> mapping);

    <U> Validated<U> flatMap(BiFunction<T, Valid<T>, Validated<U>> mapping);

    default <U> Validated<U> flatMap(Function<T, Validated<U>> mapping) {
        return flatMap((value, original) -> mapping.apply(value));
    }

    /**
     * When the condition is met, turn a {@link Valid} result into an {@link Invalid}
     * result with the provided message.
     * @param condition {@code true} when the property should be considered invalid.
     * @param message A message explaining the reason why the property is considered
     * invalid.
     * @param reason An invalid reason.
     * @return When originally {@link Valid}, apply the test and either retain the valid
     * decision or make it {@link Invalid}. When originally {@link Invalid} or
     * {@link None}, don't apply the test at all, and pass through the original decision.
     */
    default Validated<T> invalidateWhen(Predicate<T> condition, String message, InvalidReason reason) {
        return flatMap((value, valid) -> condition.test(value)
                ? Validated.invalid(valid.property, value, message, reason) : valid);
    }

    default Validated<T> required() {
        return invalidateWhen(Objects::isNull, "is required", InvalidReason.MISSING);
    }

    default Validated<T> nonBlank() {
        return invalidateWhen(t -> StringUtils.isBlank(t.toString()), "cannot be blank", InvalidReason.MISSING);
    }

    T get() throws ValidationException;

    default T orElse(@Nullable T t) throws ValidationException {
        return orElseGet(() -> t);
    }

    T orElseGet(Supplier<T> t) throws ValidationException;

    void orThrow() throws ValidationException;

    /**
     * Indicates that no validation has occurred. None is considered "valid", effectively
     * a no-op validation.
     *
     * @param <T> A type that this validation is being coerced to or joined with in a list
     * of validators.
     */
    class None<T> implements Validated<T> {

        @Override
        public boolean isValid() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Validated<U> map(Function<T, U> mapping) {
            return (Validated<U>) this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Validated<U> flatMap(BiFunction<T, Valid<T>, Validated<U>> mapping) {
            return (Validated<U>) this;
        }

        @Override
        public T get() {
            return null;
        }

        @Override
        public T orElseGet(Supplier<T> t) {
            return null;
        }

        @Override
        public void orThrow() {
        }

        @NonNull
        @Override
        public Iterator<Validated<T>> iterator() {
            return Collections.emptyIterator();
        }

    }

    /**
     * A specialization {@link Valid} that won't print the secret in plain text if the
     * validation is serialized.
     */
    class Secret extends Valid<String> {

        public Secret(String property, String value) {
            super(property, value);
        }

        @Override
        public String toString() {
            return "Secret{" + "property='" + property + '\'' + '}';
        }

    }

    /**
     * A valid property value.
     *
     * @param <T> The type of the property.
     */
    class Valid<T> implements Validated<T> {

        protected final String property;

        private final T value;

        public Valid(String property, T value) {
            this.property = property;
            this.value = value;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @NonNull
        @Override
        public Iterator<Validated<T>> iterator() {
            return Stream.of((Validated<T>) this).iterator();
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void orThrow() {
        }

        @Override
        public T orElseGet(Supplier<T> t) {
            return value == null ? t.get() : value;
        }

        @Override
        public <U> Validated<U> map(Function<T, U> mapping) {
            return new Valid<>(property, mapping.apply(value));
        }

        @Override
        public <U> Validated<U> flatMap(BiFunction<T, Valid<T>, Validated<U>> mapping) {
            return mapping.apply(value, this);
        }

        public String getProperty() {
            return property;
        }

        @Override
        public String toString() {
            return "Valid{" + "property='" + property + '\'' + ", value='" + value + '\'' + '}';
        }

    }

    class Invalid<T> implements Validated<T> {

        private final String property;

        @Nullable
        private final Object value;

        private final String message;

        private final InvalidReason reason;

        @Nullable
        private final Throwable exception;

        public Invalid(String property, @Nullable Object value, String message, InvalidReason reason,
                @Nullable Throwable exception) {
            this.property = property;
            this.value = value;
            this.message = message;
            this.reason = reason;
            this.exception = exception;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @NonNull
        @Override
        public Iterator<Validated<T>> iterator() {
            return Stream.of((Validated<T>) this).iterator();
        }

        public String getMessage() {
            return message;
        }

        public InvalidReason getReason() {
            return reason;
        }

        @Nullable
        public Throwable getException() {
            return exception;
        }

        @Override
        public T get() throws ValidationException {
            throw new ValidationException(this);
        }

        @Override
        public T orElseGet(Supplier<T> t) throws ValidationException {
            throw new ValidationException(this);
        }

        @Override
        public void orThrow() throws ValidationException {
            throw new ValidationException(this);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Validated<U> map(Function<T, U> mapping) {
            return (Validated<U>) this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <U> Validated<U> flatMap(BiFunction<T, Valid<T>, Validated<U>> mapping) {
            return (Validated<U>) this;
        }

        public String getProperty() {
            return property;
        }

        @Nullable
        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "Invalid{" + "property='" + property + '\'' + ", value='" + value + '\'' + ", message='" + message
                    + '\'' + '}';
        }

    }

    class Either implements Validated<Object> {

        private final Validated<?> left;

        private final Validated<?> right;

        public Either(Validated<?> left, Validated<?> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isValid() {
            return left.isValid() && right.isValid();
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("get not supported on more than one Validated object");
        }

        @Override
        public Object orElseGet(Supplier<Object> o) throws ValidationException {
            throw new UnsupportedOperationException("orElse not supported on more than one Validated object");
        }

        @Override
        public void orThrow() throws ValidationException {
            List<Invalid<?>> failures = failures();
            if (!failures.isEmpty()) {
                throw new ValidationException(this);
            }
        }

        @Override
        public <U> Validated<U> map(Function<Object, U> mapping) {
            throw new UnsupportedOperationException("cannot invoke map on more than one Validated object");
        }

        @Override
        public <U> Validated<U> flatMap(BiFunction<Object, Valid<Object>, Validated<U>> mapping) {
            throw new UnsupportedOperationException("cannot invoke flatMap on more than one Validated object");
        }

        @NonNull
        @Override
        public Iterator<Validated<Object>> iterator() {
            return Stream
                .concat(stream(left.spliterator(), false).map(v -> v.map(o -> (Object) o)),
                        stream(right.spliterator(), false).map(v -> v.map(o -> (Object) o)))
                .iterator();
        }

    }

}
