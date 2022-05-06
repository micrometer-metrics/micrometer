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
package io.micrometer.core.instrument.util;

import io.micrometer.common.lang.Nullable;

import java.io.*;
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
     * @param inputStream source {@link InputStream}
     * @param charset source {@link Charset}
     * @return created {@code String}
     */
    public static String toString(@Nullable InputStream inputStream, Charset charset) {
        if (inputStream == null)
            return "";

        try (StringWriter writer = new StringWriter();
                InputStreamReader reader = new InputStreamReader(inputStream, charset);
                BufferedReader bufferedReader = new BufferedReader(reader)) {
            char[] chars = new char[DEFAULT_BUFFER_SIZE];
            int readChars;
            while ((readChars = bufferedReader.read(chars)) != EOF) {
                writer.write(chars, 0, readChars);
            }
            return writer.toString();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Create a {@code String} from {@link InputStream} with default {@link Charset}.
     * @param inputStream source {@link InputStream}
     * @return created {@code String}
     */
    public static String toString(@Nullable InputStream inputStream) {
        return toString(inputStream, Charset.defaultCharset());
    }

    private IOUtils() {
    }

}
