/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * An immutable set of key-value pairs.
 *
 * <p>
 * Key-value pairs are dropped for {@code null} or empty keys.
 *
 * <p>
 * Note: for subclasses of this, null keys will be removed, but if your key has another
 * concept of being "empty", you'll need to remove them before calling the constructor,
 * assuming you don't want the "empty" keys to be kept in your collection.
 *
 * <p>
 * This class is internal and is hence not for use. Its APIs are unstable and can change
 * at any time.
 *
 * @param <V> The type of the values contained in this.
 */
abstract class ImmutableKeyValuePairs<K, V> {

    private final Object[] data;

    /**
     * Stores the raw object data directly. Does not do any de-duping or sorting. If you
     * use this constructor, you *must* guarantee that the data has been de-duped and
     * sorted by key before it is passed here.
     */
    protected ImmutableKeyValuePairs(Object[] data) {
        this.data = data;
    }

    /**
     * Sorts and dedupes the key/value pairs in {@code data}. {@code null} values will be
     * removed. Keys will be compared with the given {@link Comparator}.
     */
    protected ImmutableKeyValuePairs(Object[] data, Comparator<?> keyComparator) {
        this(sortAndFilter(data, keyComparator));
    }

    // TODO: Improve this to avoid one allocation, for the moment only some Builders and
    // the asMap
    // calls this.
    protected final List<Object> data() {
        return Arrays.asList(data);
    }

    public final int size() {
        return data.length / 2;
    }

    public final boolean isEmpty() {
        return data.length == 0;
    }

    public final Map<K, V> asMap() {
        return ReadOnlyArrayMap.wrap(data());
    }

    /**
     * Returns the value for the given {@code key}, or {@code null} if the key is not
     * present.
     */
    @SuppressWarnings("unchecked")
    public final V get(K key) {
        if (key == null) {
            return null;
        }
        for (int i = 0; i < data.length; i += 2) {
            if (key.equals(data[i])) {
                return (V) data[i + 1];
            }
        }
        return null;
    }

    /** Iterates over all the key-value pairs of labels contained by this instance. */
    @SuppressWarnings("unchecked")
    public final void forEach(BiConsumer<? super K, ? super V> consumer) {
        if (consumer == null) {
            return;
        }
        for (int i = 0; i < data.length; i += 2) {
            consumer.accept((K) data[i], (V) data[i + 1]);
        }
    }

    /**
     * Sorts and dedupes the key/value pairs in {@code data}. {@code null} values will be
     * removed. Keys will be compared with the given {@link Comparator}.
     */
    private static Object[] sortAndFilter(Object[] data, Comparator<?> keyComparator) {
        Utils.checkArgument(data.length % 2 == 0, "You must provide an even number of key/value pair arguments.");

        if (data.length == 0) {
            return data;
        }

        mergeSort(data, keyComparator);
        return dedupe(data, keyComparator);
    }

    // note: merge sort implementation cribbed from this wikipedia article:
    // https://en.wikipedia.org/wiki/Merge_sort (this is the top-down variant)
    private static void mergeSort(Object[] data, Comparator<?> keyComparator) {
        Object[] workArray = new Object[data.length];
        System.arraycopy(data, 0, workArray, 0, data.length);
        splitAndMerge(workArray, 0, data.length, data, keyComparator); // sort data from
                                                                       // workArray[] into
                                                                       // sourceArray[]
    }

    /**
     * Sort the given run of array targetArray[] using array workArray[] as a source.
     * beginIndex is inclusive; endIndex is exclusive (targetArray[endIndex] is not in the
     * set).
     */
    private static void splitAndMerge(Object[] workArray, int beginIndex, int endIndex, Object[] targetArray,
            Comparator<?> keyComparator) {
        if (endIndex - beginIndex <= 2) { // if single element in the run, it's sorted
            return;
        }
        // split the run longer than 1 item into halves
        int midpoint = ((endIndex + beginIndex) / 4) * 2; // note: due to it's being
                                                          // key/value pairs
        // recursively sort both runs from array targetArray[] into workArray[]
        splitAndMerge(targetArray, beginIndex, midpoint, workArray, keyComparator);
        splitAndMerge(targetArray, midpoint, endIndex, workArray, keyComparator);
        // merge the resulting runs from array workArray[] into targetArray[]
        merge(workArray, beginIndex, midpoint, endIndex, targetArray, keyComparator);
    }

    /**
     * Left source half is sourceArray[ beginIndex:middleIndex-1]. Right source half is
     * sourceArray[ middleIndex:endIndex-1]. Result is targetArray[
     * beginIndex:endIndex-1].
     */
    @SuppressWarnings("unchecked")
    private static <K> void merge(Object[] sourceArray, int beginIndex, int middleIndex, int endIndex,
            Object[] targetArray, Comparator<K> keyComparator) {
        int leftKeyIndex = beginIndex;
        int rightKeyIndex = middleIndex;

        // While there are elements in the left or right runs, fill in the target array
        // from left to
        // right
        for (int k = beginIndex; k < endIndex; k += 2) {
            // If left run head exists and is <= existing right run head.
            if (leftKeyIndex < middleIndex - 1
                    && (rightKeyIndex >= endIndex - 1 || compareToNullSafe((K) sourceArray[leftKeyIndex],
                            (K) sourceArray[rightKeyIndex], keyComparator) <= 0)) {
                targetArray[k] = sourceArray[leftKeyIndex];
                targetArray[k + 1] = sourceArray[leftKeyIndex + 1];
                leftKeyIndex = leftKeyIndex + 2;
            }
            else {
                targetArray[k] = sourceArray[rightKeyIndex];
                targetArray[k + 1] = sourceArray[rightKeyIndex + 1];
                rightKeyIndex = rightKeyIndex + 2;
            }
        }
    }

    private static <K> int compareToNullSafe(K key, K pivotKey, Comparator<K> keyComparator) {
        if (key == null) {
            return pivotKey == null ? 0 : -1;
        }
        if (pivotKey == null) {
            return 1;
        }
        return keyComparator.compare(key, pivotKey);
    }

    @SuppressWarnings("unchecked")
    private static <K> Object[] dedupe(Object[] data, Comparator<K> keyComparator) {
        Object previousKey = null;
        int size = 0;

        // Implement the "last one in wins" behavior.
        for (int i = 0; i < data.length; i += 2) {
            Object key = data[i];
            Object value = data[i + 1];
            // Skip entries with key null.
            if (key == null) {
                continue;
            }
            // If the previously added key is equal with the current key, we overwrite
            // what we have.
            if (previousKey != null && keyComparator.compare((K) key, (K) previousKey) == 0) {
                size -= 2;
            }
            // Skip entries with null value, we do it here because we want them to
            // overwrite and remove
            // entries with same key that we already added.
            if (value == null) {
                continue;
            }
            previousKey = key;
            data[size++] = key;
            data[size++] = value;
        }
        // Elements removed from the array, copy the array. We optimize for the case where
        // we don't have
        // duplicates or invalid entries.
        if (data.length != size) {
            Object[] result = new Object[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableKeyValuePairs)) {
            return false;
        }
        ImmutableKeyValuePairs<?, ?> that = (ImmutableKeyValuePairs<?, ?>) o;
        return Arrays.equals(this.data, that.data);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result *= 1000003;
        result ^= Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < data.length; i += 2) {
            // Quote string values
            Object value = data[i + 1];
            String valueStr = value instanceof String ? '"' + (String) value + '"' : value.toString();
            sb.append(data[i]).append("=").append(valueStr).append(", ");
        }
        // get rid of that last pesky comma
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }

}
