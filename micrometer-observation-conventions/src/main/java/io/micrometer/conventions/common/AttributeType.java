/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

/**
 * An enum that represents all the possible value types for an {@code AttributeKey} and
 * hence the types of values that are allowed for {@link Attributes}.
 */
public enum AttributeType {

    STRING, BOOLEAN, LONG, DOUBLE, STRING_ARRAY, BOOLEAN_ARRAY, LONG_ARRAY, DOUBLE_ARRAY

}
