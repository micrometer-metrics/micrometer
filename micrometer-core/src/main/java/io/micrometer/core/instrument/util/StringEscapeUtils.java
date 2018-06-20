/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.util;

import io.micrometer.core.lang.Nullable;

/**
 * Utilities for escaping {@code String}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public final class StringEscapeUtils {
    /**
     * Modified from the quote method in:
     * https://github.com/codehaus/jettison/blob/master/src/main/java/org/codehaus/jettison/json/JSONObject.java
     *
     * @param string The string to escape.
     * @return An escaped JSON string.
     */
    public static String escapeJson(@Nullable String string) {
        if (StringUtils.isEmpty(string)) {
            return "";
        }

        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 2);

        for (int i = 0; i < len; i += 1) {
            char c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private StringEscapeUtils() {
    }

}
