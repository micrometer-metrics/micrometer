package io.micrometer.core.instrument.util;

import io.micrometer.core.lang.Nullable;

public class Assert {

    /**
     * Assert that an object is not {@code null}.
     * <pre class="code">Assert.notNull(object, "{field} must not be null");</pre>
     * @param object the object to check
     * @param field the exception message to use if the assertion fails
     * @throws IllegalArgumentException if the object is {@code null}
     */
    public static void notNull(@Nullable Object object, String field) {
        if (object == null) {
            throw new IllegalArgumentException("The "+field+" parameter must not be null");
        }
    }
}
