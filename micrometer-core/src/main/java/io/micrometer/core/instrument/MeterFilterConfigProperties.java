package io.micrometer.core.instrument;

import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class MeterFilterConfigProperties implements MeterFilter {
    private final Map<String, String> overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public Map<String, String> getOverrides() {
        return overrides;
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        ConfigurationRule rule = findConfigFor(id, "enabled");

        if ("true".equalsIgnoreCase(rule.getValue()) || "enabled".equalsIgnoreCase(rule.getValue())) {
            return MeterFilterReply.ACCEPT;
        } else if ("false".equalsIgnoreCase(rule.getValue()) || "disabled".equalsIgnoreCase(rule.getValue())) {
            return MeterFilterReply.DENY;
        }

        if (rule.getValue() != null) {
            throw new ConfigurationException("Error parsing rule "+rule.getRule()+" value:"+rule.getValue());
        }

        return MeterFilterReply.NEUTRAL;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        return id;
    }

    @Override
    public HistogramConfig configure(Meter.Id id, HistogramConfig histogramConfig) {
        HistogramConfig.Builder builder = HistogramConfig.builder();
        checkConfig(id,"durationExpiry", (config) ->
            builder.histogramExpiry(TimeUtils.simpleParse(config)));

        checkConfig(id,"histogramBufferLength", (config) ->
            builder.histogramBufferLength(Integer.parseInt(config)));

        checkConfig(id,"percentileHistogram", (config) ->{
                checkBoolean(config);
                builder.percentilesHistogram(Boolean.parseBoolean(config));
            });

        checkConfig(id,"percentiles", (config) -> {
            double[] percentilesDbl = Arrays.stream(config.split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
            builder.percentiles(percentilesDbl);
        });

        checkConfig(id,"maximumExpectedValue", (config) -> {
            Long maxValParse = tryDurationParseToNanos(config);
            if (maxValParse == null) {
                maxValParse = Long.parseLong(config);
            }
            builder.maximumExpectedValue(maxValParse);
        });


        checkConfig(id,"minimumExpectedValue", (config) -> {
            Long minValParse = tryDurationParseToNanos(config);
            if (minValParse == null) {
                minValParse = Long.parseLong(config);
            }
            builder.minimumExpectedValue(minValParse);
        });

        checkConfig(id,"sla", (config) -> {
            long[] slas = Arrays.stream(config.split(","))
                .mapToLong(s -> {
                    Long l = tryDurationParseToNanos(s);
                    if (l == null) {
                        l = Long.parseLong(s);
                    }
                    return l;
                })
                .toArray();
            builder.sla(slas);
        });

        return builder.build().merge(histogramConfig);
    }

    private void checkBoolean(String config) {
        if(!config.equalsIgnoreCase("false")
            && !config.equalsIgnoreCase("true")) {
            throw new ConfigurationException("Invalid boolean:"+ config);
        }
    }

    private void checkConfig(Meter.Id id, String configKey, ConfigCallback callback) {
        ConfigurationRule rule = findConfigFor(id, configKey);
        try {
            if(rule != null && rule.getValue() != null) {
                callback.configure(rule.getValue());
            }
        } catch (Exception e) {
            throw new ConfigurationException("Error parsing rule "+rule.getRule()+" value:"+rule.getValue(), e);
        }
    }

    interface ConfigCallback {
        void configure(String configValu) throws Exception;
    }


    private Long tryDurationParseToNanos(String dur) {
        try {
            return TimeUtils.simpleParse(dur).toNanos();
        } catch (DateTimeParseException e) {
            return null;
        }
    }


    @SuppressWarnings("WeakerAccess")
    protected ConfigurationRule findConfigFor(Meter.Id id, String propertyName) {
        return findMostSpecificRule(id.getName(), propertyName, overrides, null);
    }

    @SuppressWarnings("WeakerAccess")
    protected ConfigurationRule findMostSpecificRule(String name, String suffix, Map<String, String> map, String defaultVal) {
        ConfigurationRule filterStatus = new ConfigurationRule("DEFAULT", defaultVal);

        String filterLookup = null;
        if (suffix != null) {
            String rule = name + "." + suffix;
            filterLookup = map.get(rule);
            if (filterLookup != null) {
                filterStatus = new ConfigurationRule(rule, filterLookup);
            }
        }

        if (filterLookup == null) {
            filterLookup = map.get(name);
            if (filterLookup != null) {
                filterStatus = new ConfigurationRule(name, filterLookup);
            } else if (name.contains(".")) {
                filterStatus = findMostSpecificRule(name.substring(0, name.lastIndexOf(".")), suffix, map, defaultVal);
            }
        }

        if (filterStatus.value == null || filterStatus.value.equals(defaultVal)) {
            String rule = "ALL." + suffix;
            filterLookup = map.get(rule);
            if (filterLookup != null) {
                filterStatus = new ConfigurationRule(rule, filterLookup);
            }
        }

        return filterStatus;
    }

    public static class ConfigurationRule {
        private final String rule;
        private final String value;

        public ConfigurationRule(String rule, String value) {
            this.rule = rule;
            this.value = value;
        }

        public String getRule() {
            return rule;
        }

        public String getValue() {
            return value;
        }
    }

}
