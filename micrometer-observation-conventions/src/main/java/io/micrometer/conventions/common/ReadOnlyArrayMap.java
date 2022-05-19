/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.micrometer.conventions.common;

import java.lang.reflect.Array;
import java.util.*;

/**
 * A read-only view of an array of key-value pairs.
 *
 * <p>
 * This class is internal and is hence not for public use. Its APIs are unstable and can
 * change at any time.
 */
@SuppressWarnings("unchecked")
final class ReadOnlyArrayMap<K, V> extends AbstractMap<K, V> {

    /** Returns a read-only view of the given {@code array}. */
    public static <K, V> Map<K, V> wrap(List<Object> array) {
        if (array.isEmpty()) {
            return Collections.emptyMap();
        }
        return new ReadOnlyArrayMap<>(array);
    }

    private final List<Object> array;

    private final int size;

    private ReadOnlyArrayMap(List<Object> array) {
        this.array = array;
        this.size = array.size() / 2;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(Object o) {
        if (o == null) {
            return false; // null keys are not allowed
        }
        return arrayIndexOfKey(o) != -1;
    }

    @Override
    public boolean containsValue(Object o) {
        for (int i = 0; i < array.size(); i += 2) {
            if (value(i + 1).equals(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object o) {
        if (o == null) {
            return null; // null keys are not allowed
        }
        int i = arrayIndexOfKey(o);
        return i != -1 ? value(i + 1) : null;
    }

    int arrayIndexOfKey(Object o) {
        int result = -1;
        for (int i = 0; i < array.size(); i += 2) {
            if (o.equals(key(i))) {
                return i;
            }
        }
        return result;
    }

    K key(int i) {
        return (K) array.get(i);
    }

    V value(int i) {
        return (V) array.get(i);
    }

    @Override
    public Set<K> keySet() {
        return new KeySetView();
    }

    @Override
    public Collection<V> values() {
        return new ValuesView();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySetView();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    final class KeySetView extends SetView<K> {

        @Override
        K elementAtArrayIndex(int i) {
            return key(i);
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

    }

    final class ValuesView extends SetView<V> {

        @Override
        V elementAtArrayIndex(int i) {
            return value(i + 1);
        }

        @Override
        public boolean contains(Object o) {
            return containsValue(o);
        }

    }

    final class EntrySetView extends SetView<Entry<K, V>> {

        @Override
        Entry<K, V> elementAtArrayIndex(int i) {
            return new SimpleImmutableEntry<>(key(i), value(i + 1));
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry) || ((Entry<?, ?>) o).getKey() == null) {
                return false;
            }
            Entry<?, ?> that = (Entry<?, ?>) o;
            int i = arrayIndexOfKey(that.getKey());
            if (i == -1) {
                return false;
            }
            return value(i + 1).equals(that.getValue());
        }

    }

    abstract class SetView<E> implements Set<E> {

        @Override
        public int size() {
            return size;
        }

        // By abstracting this, {@link #keySet()} {@link #values()} and {@link
        // #entrySet()} only
        // implement need implement two methods based on {@link #<E>}: this method and and
        // {@link
        // #contains(Object)}.
        abstract E elementAtArrayIndex(int i);

        @Override
        public Iterator<E> iterator() {
            return new ReadOnlyIterator();
        }

        @Override
        public Object[] toArray() {
            return copyTo(new Object[size]);
        }

        @Override
        public <T> T[] toArray(T[] a) {
            T[] result = a.length >= size ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), size());
            return copyTo(result);
        }

        <T> T[] copyTo(T[] dest) {
            for (int i = 0, d = 0; i < array.size(); i += 2) {
                dest[d++] = (T) elementAtArrayIndex(i);
            }
            return dest;
        }

        final class ReadOnlyIterator implements Iterator<E> {

            int current = 0;

            @Override
            public boolean hasNext() {
                return current < array.size();
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                E result = elementAtArrayIndex(current);
                current += 2;
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        }

        @Override
        public boolean containsAll(Collection<?> c) {
            if (c == null) {
                return false;
            }
            if (c.isEmpty()) {
                return true;
            }

            for (Object element : c) {
                if (!contains(element)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("ReadOnlyArrayMap{");
        for (int i = 0; i < array.size(); i += 2) {
            result.append(key(i)).append('=').append(value(i + 1));
            result.append(',');
        }
        result.setLength(result.length() - 1);
        return result.append("}").toString();
    }

}
