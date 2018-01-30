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
package io.micrometer.statsd;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micrometer.statsd.internal.MemoizingFunction.memoize;
import static java.beans.Introspector.decapitalize;
import static java.util.stream.Stream.of;

class StatsdLineBuilder {
    private final Meter.Id id;
    private final StatsdFlavor flavor;
    private final HierarchicalNameMapper nameMapper;
    private final MeterRegistry.Config config;

    private final Function<NamingConvention, String> datadogTagString;
    private final Function<NamingConvention, String> telegrafTagString;

    /**
     * Because NumberFormat is not thread-safe we cannot share instances across threads.
     */
    private static final ThreadLocal<NumberFormat> NUMBER_FORMATTERS = ThreadLocal.withInitial(() -> {
        // Always create the formatter for the US locale in order to avoid this bug:
        // https://github.com/indeedeng/java-dogstatsd-client/issues/3
        final NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);
        numberFormatter.setGroupingUsed(false);
        numberFormatter.setMaximumFractionDigits(6);

        // We need to specify a value for Double.NaN that is recognizable
        final DecimalFormat decimalFormat = (DecimalFormat) numberFormatter;
        final DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
        symbols.setNaN("NaN");
        decimalFormat.setDecimalFormatSymbols(symbols);

        return numberFormatter;
    });

    StatsdLineBuilder(Meter.Id id, StatsdFlavor flavor, HierarchicalNameMapper nameMapper, MeterRegistry.Config config) {
        this.id = id;
        this.flavor = flavor;
        this.nameMapper = nameMapper;
        this.config = config;

        // service:payroll,region:us-west
        this.datadogTagString = memoize(convention ->
            id.getTags().iterator().hasNext() ?
                id.getConventionTags(convention).stream()
                    .map(t -> t.getKey() + ":" + t.getValue())
                    .collect(Collectors.joining(","))
                : null
        );

        // service=payroll,region=us-west
        this.telegrafTagString = memoize(convention ->
            id.getTags().iterator().hasNext() ?
                id.getConventionTags(convention).stream()
                    .map(t -> t.getKey() + "=" + t.getValue())
                    .collect(Collectors.joining(","))
                : null
        );
    }

    String count(long amount) {
        return count(amount, Statistic.COUNT);
    }

    String count(long amount, Statistic stat) {
        return line(Long.toString(amount), stat, "c");
    }

    String gauge(double amount) {
        return gauge(amount, Statistic.VALUE);
    }

    String gauge(double amount, Statistic stat) {
        return line(NUMBER_FORMATTERS.get().format(amount), stat, "g");
    }

    String histogram(double amount) {
        return line(NUMBER_FORMATTERS.get().format(amount), null, "h");
    }

    String timing(double timeMs) {
        return line(NUMBER_FORMATTERS.get().format(timeMs), null, "ms");
    }

    private String line(String amount, @Nullable Statistic stat, String type) {
        switch (flavor) {
            case ETSY:
                return metricName(stat) + ":" + amount + "|" + type;
            case DATADOG:
                return metricName(stat) + ":" + amount + "|" + type + tags(stat, datadogTagString.apply(config.namingConvention()),":", "|#");
            case TELEGRAF:
            default:
                return metricName(stat) + tags(stat, telegrafTagString.apply(config.namingConvention()),"=", ",") + ":" + amount + "|" + type;
        }
    }

    private String tags(@Nullable Statistic stat, String otherTags, String keyValueSeparator, String preamble) {
        String tags = of(stat == null ? null : "statistic" + keyValueSeparator + decapitalize(stat.toString()), otherTags)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(","));

        if(!tags.isEmpty())
            tags = preamble + tags;
        return tags;
    }

    private String metricName(@Nullable Statistic stat) {
        switch (flavor) {
            case ETSY:
                return nameMapper.toHierarchicalName(stat != null ? id.withTag(stat) : id, config.namingConvention());
            case DATADOG:
            case TELEGRAF:
            default:
                return config.namingConvention().name(id.getName(), id.getType(), id.getBaseUnit());
        }
    }
}
