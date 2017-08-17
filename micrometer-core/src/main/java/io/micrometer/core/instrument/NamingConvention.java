/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Jon Schneider
 */
public interface NamingConvention {
    NamingConvention snakeCase = (name, type) ->
        Arrays.stream(name.split("\\.")).filter(Objects::nonNull).collect(Collectors.joining("_"));

    NamingConvention camelCase = (name, type) -> {
        String[] parts = name.split("\\.");
        StringBuilder conventionName = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String str = parts[i];
            if (str == null || str.length() == 0)
                continue;

            if (i == 0) {
                conventionName.append(str.toLowerCase());
            } else {
                final char firstChar = str.charAt(0);
                if (Character.isTitleCase(firstChar)) {
                    conventionName.append(str); // already capitalized
                } else {
                    conventionName.append(String.valueOf(Character.toTitleCase(firstChar))).append(str.substring(1));
                }
            }
        }
        return conventionName.toString();
    };

    String name(String name, Meter.Type type);
}
