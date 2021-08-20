/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Record metrics that report disk space usage.
 *
 * @author jmcshane
 * @author Johnny Lim
 * @author Chris Bono
 * @since 1.8.0
 */
@NonNullApi
@NonNullFields
public class DiskSpaceMetrics implements MeterBinder {

    private final Iterable<Tag> tags;
    private final List<File> paths;

    public DiskSpaceMetrics(File path) {
        this(path, emptyList());
    }

    public DiskSpaceMetrics(List<File> paths) {
        this(paths, emptyList());
    }

    public DiskSpaceMetrics(File path, Iterable<Tag> tags) {
        this(Collections.singletonList(path), tags);
    }

    public DiskSpaceMetrics(List<File> paths, Iterable<Tag> tags) {
        this.paths = paths;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        paths.forEach(path -> bindWithPath(registry, path));
    }

    private void bindWithPath(MeterRegistry registry, File path) {
        Iterable<Tag> tagsWithPath = Tags.concat(tags, "path", path.getAbsolutePath());
        Gauge.builder("disk.free", path, File::getUsableSpace)
                .tags(tagsWithPath)
                .description("Usable space for path")
                .baseUnit(BaseUnits.BYTES)
                .strongReference(true)
                .register(registry);
        Gauge.builder("disk.total", path, File::getTotalSpace)
                .tags(tagsWithPath)
                .description("Total space for path")
                .baseUnit(BaseUnits.BYTES)
                .strongReference(true)
                .register(registry);
    }
}
