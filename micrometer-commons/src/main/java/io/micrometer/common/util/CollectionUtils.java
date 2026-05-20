/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.common.util;

import java.util.HashSet;

/**
 * Utilities for {@link java.util.Collection}.
 *
 * @author Szymon Habrainski
 */
public final class CollectionUtils {

    /**
     * Default load factor for {@link HashSet}.
     * @see #newHashSet(int)
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private CollectionUtils() {
    }

    /**
     * Creates a {@link HashSet} sized to hold {@code expectedSize} elements without
     * resizing under the {@link #DEFAULT_LOAD_FACTOR default load factor}.
     * @param expectedSize the number of elements the set is expected to hold
     * @return a new, empty {@link HashSet}
     */
    @SuppressWarnings("NonApiType")
    public static <E> HashSet<E> newHashSet(int expectedSize) {
        return new HashSet<>(calculateInitialCapacity(expectedSize), DEFAULT_LOAD_FACTOR);
    }

    /**
     * Returns the initial capacity to pass to a hash-based collection so that it can hold
     * {@code expectedSize} entries without resizing. The collection rounds this up to the
     * next power of two; under the {@link #DEFAULT_LOAD_FACTOR default load factor} the
     * resulting resize threshold is at least {@code expectedSize}.
     */
    private static int calculateInitialCapacity(int expectedSize) {
        return (int) Math.ceil(expectedSize / (double) DEFAULT_LOAD_FACTOR);
    }

}
