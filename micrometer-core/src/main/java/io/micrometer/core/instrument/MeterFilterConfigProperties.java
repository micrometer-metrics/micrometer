package io.micrometer.core.instrument;

import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class MeterFilterConfigProperties implements MeterFilter {
    private final Map<String, String> filter = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public Map<String, String> getFilter() {
        return filter;
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        String rule = findConfigFor(id, "enabled");

        if ("true".equalsIgnoreCase(rule) || "enabled".equalsIgnoreCase(rule)) {
            return MeterFilterReply.ACCEPT;
        } else if ("false".equalsIgnoreCase(rule) || "disabled".equalsIgnoreCase(rule)) {
            return MeterFilterReply.DENY;
        }

        return MeterFilterReply.NEUTRAL;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        return id;
    }

    @Override
    public HistogramConfig configure(Meter.Id id, HistogramConfig config) {
        HistogramConfig.Builder builder = HistogramConfig.builder();
        String durationExpiry = findConfigFor(id, "durationExpiry");
        if (durationExpiry != null) {
            builder.histogramExpiry(TimeUtils.simpleParse(durationExpiry));
        }

        String histogramBufferLength = findConfigFor(id, "histogramBufferLength");
        if (histogramBufferLength != null) {
            builder.histogramBufferLength(Integer.parseInt(histogramBufferLength));
        }

        String percentileHistogram = findConfigFor(id, "percentileHistogram");
        if (percentileHistogram != null) {
            builder.percentilesHistogram(Boolean.parseBoolean(percentileHistogram));
        }

        String percentiles = findConfigFor(id, "percentiles");
        if (percentiles != null) {
            double[] percentilesDbl = Arrays.stream(percentiles.split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
            builder.percentiles(percentilesDbl);
        }

        String maxVal = findConfigFor(id, "maximumExpectedValue");
        if (maxVal != null) {
            Long maxValParse = tryDurationParseToNanos(maxVal);
            if(maxValParse == null) {
                maxValParse = Long.parseLong(maxVal);
            }
            builder.maximumExpectedValue(maxValParse);
        }

        String minVal = findConfigFor(id, "minimumExpectedValue");
        if (minVal != null) {
            Long minValParse = tryDurationParseToNanos(minVal);
            if(minValParse == null) {
                minValParse = Long.parseLong(minVal);
            }
            builder.minimumExpectedValue(minValParse);
        }

        String sla = findConfigFor(id, "sla");
        if (sla != null) {
            long[] slas = Arrays.stream(sla.split(","))
                .mapToLong( s -> {
                    Long l = tryDurationParseToNanos(s);
                    if(l == null) {
                        l = Long.parseLong(s);
                    }
                    return l;
                })
                .toArray();
            builder.sla(slas);
        }


        return builder.build().merge(config);
    }

    private Long tryDurationParseToNanos(String dur) {
        try {
            return TimeUtils.simpleParse(dur).toNanos();
        } catch (DateTimeParseException e) {
            return null;
        }
    }


    protected String findConfigFor(Meter.Id id, String propertyName) {
        return findMostSpecificRule(id.getName(), propertyName, filter, null);
    }

    protected String findMostSpecificRule(String name, String suffix, Map<String, String> map, String defaultVal) {
        String filterStatus = defaultVal;

        String filterLookup = null;
        if (suffix != null) {
            filterLookup = map.get(name + "." + suffix);
            if (filterLookup != null) {
                filterStatus = filterLookup;
            }
        }

        if (filterLookup == null) {
            filterLookup = map.get(name);
            if (filterLookup != null) {
                filterStatus = filterLookup;
            } else if (name.contains(".")) {
                filterStatus = findMostSpecificRule(name.substring(0, name.lastIndexOf(".")), suffix, map, defaultVal);
            }
        }

        if (filterStatus == null || filterStatus.equals(defaultVal)) {
            filterLookup = map.get("ALL." + suffix);
            if (filterLookup != null) {
                filterStatus = filterLookup;
            }
        }

        return filterStatus;
    }


}
