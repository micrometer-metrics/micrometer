/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

/**
 * General internal utility methods.
 *
 * <p>
 * This class is internal and is hence not for public use. Its APIs are unstable and can
 * change at any time.
 */
final class Utils {

    private Utils() {
    }

    /**
     * Throws an {@link IllegalArgumentException} if the argument is false. This method is
     * similar to {@code Preconditions.checkArgument(boolean, Object)} from Guava.
     * @param isValid whether the argument check passed.
     * @param errorMessage the message to use for the exception.
     */
    public static void checkArgument(boolean isValid, String errorMessage) {
        if (!isValid) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

}
