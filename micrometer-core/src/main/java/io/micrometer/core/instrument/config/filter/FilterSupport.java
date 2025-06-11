/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.config.filter;

class FilterSupport {

    /**
     * At the moment of writing, it was impossible to estimate tags count from the outside
     * of class, but quite often a temporary storage (ArrayList) had to be allocated
     * during processing. To avoid excessive resizes, this constant is introduced to
     * preallocate space for such a list.
     */
    public static final int DEFAULT_TAG_COUNT_EXPECTATION = 32;

    private FilterSupport() {
    }

}
