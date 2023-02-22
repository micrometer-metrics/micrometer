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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * File descriptor metrics gathered by the JVM.
 * <p>
 * Supported JVM implementations:
 * <ul>
 * <li>HotSpot</li>
 * <li>J9</li>
 * </ul>
 *
 * @author Michael Weirauch
 * @author Tommy Ludwig
 */
@NonNullApi
@NonNullFields
public class FileDescriptorMetrics implements MeterBinder {

    /**
     * List of public, exported interface class names from supported JVM implementations.
     */
    private static final List<String> UNIX_OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
            "com.sun.management.UnixOperatingSystemMXBean", // HotSpot
            "com.ibm.lang.management.UnixOperatingSystemMXBean" // J9
    );

    private final OperatingSystemMXBean osBean;

    private final Iterable<Tag> tags;

    @Nullable
    private final Class<?> osBeanClass;

    @Nullable
    private final Method openFilesMethod;

    @Nullable
    private final Method maxFilesMethod;

    public FileDescriptorMetrics() {
        this(emptyList());
    }

    public FileDescriptorMetrics(Iterable<Tag> tags) {
        this(ManagementFactory.getOperatingSystemMXBean(), tags);
    }

    // VisibleForTesting
    FileDescriptorMetrics(OperatingSystemMXBean osBean, Iterable<Tag> tags) {
        this.osBean = osBean;
        this.tags = tags;

        this.osBeanClass = getFirstClassFound(UNIX_OPERATING_SYSTEM_BEAN_CLASS_NAMES);
        this.openFilesMethod = detectMethod("getOpenFileDescriptorCount");
        this.maxFilesMethod = detectMethod("getMaxFileDescriptorCount");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (openFilesMethod != null) {
            Gauge.builder("process.files.open", osBean, x -> invoke(openFilesMethod))
                .tags(tags)
                .description("The open file descriptor count")
                .baseUnit(BaseUnits.FILES)
                .register(registry);
        }

        if (maxFilesMethod != null) {
            Gauge.builder("process.files.max", osBean, x -> invoke(maxFilesMethod))
                .tags(tags)
                .description("The maximum file descriptor count")
                .baseUnit(BaseUnits.FILES)
                .register(registry);
        }
    }

    private double invoke(@Nullable Method method) {
        try {
            return method != null ? (double) (long) method.invoke(osBean) : Double.NaN;
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return Double.NaN;
        }
    }

    @Nullable
    private Method detectMethod(String name) {
        if (osBeanClass == null) {
            return null;
        }
        try {
            // ensure the Bean we have is actually an instance of the interface
            osBeanClass.cast(osBean);
            return osBeanClass.getDeclaredMethod(name);
        }
        catch (ClassCastException | NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    @Nullable
    private Class<?> getFirstClassFound(List<String> classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            }
            catch (ClassNotFoundException ignore) {
            }
        }
        return null;
    }

}
