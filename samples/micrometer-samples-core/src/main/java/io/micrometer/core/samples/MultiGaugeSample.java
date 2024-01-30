/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.core.samples;

import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.MultiGauge.Row;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MultiGaugeSample {

    public static void main(String[] args) {
        // MeterRegistry registry = SampleConfig.myMonitoringSystem();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        MultiGauge temperatures = MultiGauge.builder("temperatures")
            .tag("home", "little-house")
            .description("The temperature by room")
            .baseUnit("celsius")
            .register(registry);

        // @formatter:off
        for (int i = 0; i < 3; i++) {
            temperatures.register(
                fetchTemperatures().stream()
                    .map(record -> Row.of(Tags.of("room", record.getRoom().name()), record.getTemperature()))
                    .collect(Collectors.toList()),
                true // whether to overwrite the previous value or only record it once
            );

            System.out.println("---");
            System.out.println(registry.getMetersAsString());
        }
        // @formatter:on
    }

    private static List<TemperatureRecord> fetchTemperatures() {
        return Arrays.stream(Room.values())
            .map(room -> new TemperatureRecord(room, Math.random() * 3 + 20))
            .collect(Collectors.toList());
    }

    private enum Room {

        LIVING_ROOM, BEDROOM, KITCHEN

    }

    private static class TemperatureRecord {

        private final Room room;

        private final double temperature;

        private TemperatureRecord(Room room, double temperature) {
            this.room = room;
            this.temperature = temperature;
        }

        public Room getRoom() {
            return room;
        }

        public double getTemperature() {
            return temperature;
        }

    }

}
