package io.micrometer.statsd;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.NamingConvention;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.reactivestreams.Subscriber;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.micrometer.statsd.internal.MemoizingSupplier.memoize;

class StatsdLineBuilder {
    private final Meter.Id id;
    private final StatsdFlavor flavor;
    private final NamingConvention convention;

    private final Supplier<String> datadogTagString;
    private final Supplier<String> telegrafTagString;

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

    StatsdLineBuilder(Meter.Id id, StatsdFlavor flavor, NamingConvention convention) {
        this.id = id;
        this.flavor = flavor;
        this.convention = convention;

        // service:payroll,region:us-west
        this.datadogTagString = memoize(() ->
            id.getTags().iterator().hasNext() ?
                "," + id.getConventionTags(convention).stream()
                    .map(t -> t.getKey() + ":" + t.getValue())
                    .collect(Collectors.joining(","))
                : ""
        );

        // service=payroll,region=us-west
        this.telegrafTagString = memoize(() ->
            id.getTags().iterator().hasNext() ?
                "," + id.getConventionTags(convention).stream()
                    .map(t -> t.getKey() + "=" + t.getValue())
                    .collect(Collectors.joining(","))
                : ""
        );
    }

    String count(long amount) {
        return count(amount, Statistic.Count);
    }

    String count(long amount, Statistic stat) {
        return line(Long.toString(amount), stat, "c");
    }

    String gauge(double amount) {
        return gauge(amount, Statistic.Value);
    }

    String gauge(double amount, Statistic stat) {
        return line(NUMBER_FORMATTERS.get().format(amount), stat, "g");
    }

    private String line(String amount, Statistic stat, String type) {
        switch (flavor) {
            case Etsy:
                return HierarchicalNameMapper.DEFAULT.toHierarchicalName(id.withTag(stat), convention) + ":" + amount + "|" + type;
            case Datadog:
                return convention.name(id.getName(), Meter.Type.Counter, id.getBaseUnit()) + ":" + amount + "|" + type + "|#statistic:" + stat.toString().toLowerCase() + datadogTagString.get();
            case Telegraf:
            default:
                return convention.name(id.getName(), Meter.Type.Counter, id.getBaseUnit()) + ",statistic=" + stat.toString().toLowerCase() + telegrafTagString.get() + ":" + amount + "|" + type;
        }
    }
}
