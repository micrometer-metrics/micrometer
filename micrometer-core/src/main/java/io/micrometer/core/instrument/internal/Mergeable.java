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
package io.micrometer.core.instrument.internal;

/**
 * Based off of {@code org.springframework.beans.Mergeable}
 *
 * @author Jon Schneider
 */
public interface Mergeable<T> {

    /**
     * Merge the current value set with that of the supplied object. The supplied object
     * is considered the parent, and values in the callee's value set must override those
     * of the supplied object.
     * @param parent the object to merge with
     * @return the result of the merge operation
     */
    T merge(T parent);

}
