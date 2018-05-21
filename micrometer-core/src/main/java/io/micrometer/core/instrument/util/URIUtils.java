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

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Utilities for {@link URI}.
 *
 * @author Johnny Lim
 */
public final class URIUtils {

    /**
     * Constructs a URL from a URI string.
     *
     * @param uri string to be parsed into a URI
     * @return a URL
     */
    public static URL toURL(String uri) {
        try {
            return URI.create(uri).toURL();
        } catch (MalformedURLException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private URIUtils() {
    }

}
