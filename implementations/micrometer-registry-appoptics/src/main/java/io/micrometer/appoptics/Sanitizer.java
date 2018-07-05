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
package io.micrometer.appoptics;

import java.util.regex.Pattern;

/**
 * Filters out unwanted characters and truncates
 */
public abstract class Sanitizer {

    private static String sanitize(
        final String unclean,
        final Pattern disallowedChars,
        final int maxLength,
        final boolean trimFromFront) {
        if (unclean == null) {
            return null;
        }
        final String sanitized = disallowedChars.matcher(unclean).replaceAll("");
        if (sanitized.length() > maxLength) {
            if (trimFromFront) {
                return sanitized.substring(sanitized.length() - maxLength, sanitized.length());
            } else {
                return sanitized.substring(0, maxLength);
            }
        }
        return sanitized;
    }

    public static final Sanitizer TAG_NAME_SANITIZER = new Sanitizer() {
        private final int lengthLimit = 64;
        // "replace anything that isn't a word char, dash, dot, colon or underscore"
        private final Pattern disallowedCharacters = Pattern.compile("[^-.:_\\w]");

        @Override
        public String apply(final String name) {
            return sanitize(name, disallowedCharacters, lengthLimit, false);
        }
    };

    public static final Sanitizer TAG_VALUE_SANITIZER = new Sanitizer() {
        private final int lengthLimit = 255;
        // "replace anything that isn't a word char, dash, dot, colon, underscore, question mark, slash or space"
        private final Pattern disallowedCharacters = Pattern.compile("[^-.:_?\\\\/\\w ]");

        @Override
        public String apply(final String value) {
            return sanitize(value, disallowedCharacters, lengthLimit, false);
        }
    };

    public static final Sanitizer NAME_SANITIZER = new Sanitizer() {
        private final int lengthLimit = 255;
        // "replace anything that isn't a letter, number, dash, dot, colon or underscore"
        private final Pattern disallowedCharacters = Pattern.compile("[^-:A-Za-z0-9_.]");

        @Override
        public String apply(final String source) {
            return sanitize(source, disallowedCharacters, lengthLimit, false);
        }
    };

    /**
     * Apply the sanitizer to the input
     *
     * @param input the input
     * @return the sanitized output
     */
    public abstract String apply(String input);
}
