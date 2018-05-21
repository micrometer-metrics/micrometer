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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * Utilities for IO.
 *
 * @author Johnny Lim
 */
public final class IOUtils {

    private static final int EOF = -1;

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Create a {@code String} from {@link InputStream} with {@link Charset}.
     *
     * @param inputStream source {@link InputStream}
     * @param charset source {@link Charset}
     * @return created {@code String}
     */
    public static String toString(InputStream inputStream, Charset charset) {
        try (StringWriter writer = new StringWriter();
                InputStreamReader reader = new InputStreamReader(inputStream, charset);
                BufferedReader bufferedReader = new BufferedReader(reader)) {
            char[] chars = new char[DEFAULT_BUFFER_SIZE];
            int readChars;
            while ((readChars = bufferedReader.read(chars)) != EOF) {
                writer.write(chars, 0, readChars);
            }
            return writer.toString();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Create a {@code String} from {@link InputStream} with default {@link Charset}.
     *
     * @param inputStream source {@link InputStream}
     * @return created {@code String}
     */
    public static String toString(InputStream inputStream) {
        return toString(inputStream, Charset.defaultCharset());
    }

    private IOUtils() {
    }

}
