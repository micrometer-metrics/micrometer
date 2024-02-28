/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.common.lang.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

class JvmMemory {

    private JvmMemory() {
    }

    static Stream<MemoryPoolMXBean> getLongLivedHeapPools() {
        return ManagementFactory.getMemoryPoolMXBeans()
            .stream()
            .filter(JvmMemory::isHeap)
            .filter(mem -> isLongLivedPool(mem.getName()));
    }

    static boolean isConcurrentPhase(String cause, String name) {
        return "No GC".equals(cause) //
                || "Shenandoah Cycles".equals(name) // Shenandoah
                || (name.startsWith("ZGC") && name.endsWith("Cycles")) // ZGC
                || (name.startsWith("GPGC") && !name.endsWith("Pauses")) // Zing GPGC
        ;
    }

    static boolean isAllocationPool(String name) {
        return name != null && (name.endsWith("Eden Space") || "Shenandoah".equals(name) //
                || "ZHeap".equals(name) // ZGC non-generational
                || "ZGC Young Generation".equals(name) // generational ZGC
                || name.endsWith("New Gen") // Zing GPGC
                || name.endsWith("nursery-allocate") || name.endsWith("-eden") // "balanced-eden"
                || "JavaHeap".equals(name) // metronome
        );
    }

    static boolean isLongLivedPool(String name) {
        return name != null && (name.endsWith("Old Gen") || name.endsWith("Tenured Gen") || "Shenandoah".equals(name)
                || "ZHeap".equals(name) // ZGC non-generational
                || "ZGC Old Generation".equals(name) // generational ZGC
                || name.endsWith("balanced-old") //
                || name.contains("tenured") // "tenured", "tenured-SOA", "tenured-LOA"
                || "JavaHeap".equals(name) // metronome
        );
    }

    static boolean isHeap(MemoryPoolMXBean memoryPoolBean) {
        return MemoryType.HEAP.equals(memoryPoolBean.getType());
    }

    static double getUsageValue(MemoryPoolMXBean memoryPoolMXBean, ToLongFunction<MemoryUsage> getter) {
        MemoryUsage usage = getUsage(memoryPoolMXBean);
        if (usage == null) {
            return Double.NaN;
        }
        return getter.applyAsLong(usage);
    }

    @Nullable
    private static MemoryUsage getUsage(MemoryPoolMXBean memoryPoolMXBean) {
        try {
            return memoryPoolMXBean.getUsage();
        }
        catch (InternalError e) {
            // Defensive for potential InternalError with some specific JVM options. Based
            // on its Javadoc,
            // MemoryPoolMXBean.getUsage() should return null, not throwing InternalError,
            // so it seems to be a JVM bug.
            return null;
        }
    }

}
