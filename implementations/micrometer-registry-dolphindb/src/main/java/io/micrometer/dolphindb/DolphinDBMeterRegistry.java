package io.micrometer.dolphindb;

import com.xxdb.DBConnection;
import com.xxdb.data.*;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.StreamSupport;

public class DolphinDBMeterRegistry extends PushMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dolphindb-metrics-publisher");

    private final DolphinDBConfig config;

    private static final Logger logger = LoggerFactory.getLogger(DolphinDBMeterRegistry.class);

    private boolean databaseExists = false;

    private final DBConnection conn;

    @SuppressWarnings("deprecation")
    public DolphinDBMeterRegistry(DolphinDBConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new DolphinDBSession(config).getConn());
    }
    @Deprecated
    public DolphinDBMeterRegistry(DolphinDBConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new DolphinDBSession(config).getConn());
    }
    private DolphinDBMeterRegistry(DolphinDBConfig config, Clock clock, ThreadFactory threadFactory, DBConnection conn){
        super(config, clock);
        this.config = config;
        this.conn = conn;
        start(threadFactory);
    }

    public static Builder builder(DolphinDBConfig config) {
        return new Builder(config);
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        return new CumulativeCounter(id);
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id,
                                                      DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new DolphinDBDistributionSummary(id, clock, distributionStatisticConfig, scale);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new DolphinDBTimer(id, clock, distributionStatisticConfig, pauseDetector);
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return new CumulativeHistogramLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
                                                 ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit,
                getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new CumulativeFunctionCounter<>(id, obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    private void createDatabase() {
        try {
            String createDatabaseScript = new CreateDolphinDBScriptBuilder(config.directory())
                    .setCreateDatabase(config.partitionType(), config.partitionSchema(), config.engine())
                    .testDatabaseDirectory(config.directory())
                    .build();
//            System.out.println(createDatabaseScript);
            conn.run(createDatabaseScript);
        } catch (Throwable e) {
            logger.error("Unable to create the database '{}'", config.directory());
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void publish() {
        if (!databaseExists) {
            createDatabase();
            databaseExists = true;
        }

        List<List<Meter>> batch = MeterPartition.partition(this, config.batchSize());
        List<List> args = new ArrayList<List>();

        for(int i = 0; i < batch.size(); i++) {
            List<Meter> oneBatch = batch.get(i);
            for(int j = 0; j < oneBatch.size(); j++) {
                args.add(oneBatch.get(j).match(this::writeGauge, this::writeCounter, this::writeTimer,
                        this::writeSummary, this::writeLongTaskTimer, this::writeTimeGauge,
                        this::writeFunctionCounter, this::writeFunctionTimer, this::writeCustomMetric));
            }
        }
        List<String> metrcList = new ArrayList<>();
        List<Long> timestampList = new ArrayList<>();
        List<Double> valueList = new ArrayList<>();
        List<String> tagsList = new ArrayList<>();
        List<? extends Serializable> tmp = new ArrayList<>();
        for(int i =0; i < args.size(); i++) {
            for (int j = 0; j < args.get(i).size(); j++) {
                tmp = (List<? extends Serializable>) args.get(i).get(j);
                metrcList.add((String) tmp.get(0));
                timestampList.add((Long) tmp.get(1));
                valueList.add((Double) tmp.get(2));
                tagsList.add((String) tmp.get(3));
            }
        }
        List<String> colNames =  Arrays.asList("metric","timestamp","value","tags");
        List<Vector> cols = Arrays.asList(new BasicStringVector(metrcList), new BasicTimestampVector(timestampList), new BasicDoubleVector(valueList), new BasicStringVector(tagsList));
        BasicTable table = new BasicTable(colNames, cols);
        List<Entity> args1 = Arrays.asList(table);
//        args1.add(table);
        try {
            conn.run("tableInsert{pt}", args1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    List<List> writeCustomMetric(Meter meter) {
        long wallTime = config().clock().wallTime();

        List<Tag> tags = getConventionTags(meter.getId());

        List<List> args = new ArrayList<>();
        return args;

//        return StreamSupport.stream(meter.measure().spliterator(), false).map(ms -> {
//            Tags localTags = Tags.concat(tags, "statistics", ms.getStatistic().toString());
//            String name = getConventionName(meter.getId());
//
//            switch (ms.getStatistic()) {
//                case TOTAL:
//                case TOTAL_TIME:
//                    name += ".sum";
//                    break;
//                case MAX:
//                    name += ".max";
//                    break;
//                case ACTIVE_TASKS:
//                    name += ".active.count";
//                    break;
//                case DURATION:
//                    name += ".duration.sum";
//                    break;
//            }
//
//            return new CreateDolphinDBScriptBuilder(config.directory()).field("metric", name)
//                    .datapoints(wallTime, ms.getValue())
//                    .tags(localTags)
//                    .build();
//        });
//
//            return insertTableWithSuffx();
    }
    List<List> writeSummary(DistributionSummary summary) {
        long wallTime = config().clock().wallTime();

        final ValueAtPercentile[] percentileValues = summary.takeSnapshot().percentileValues();
        final CountAtBucket[] histogramCounts = ((DolphinDBDistributionSummary) summary).histogramCounts();
        double count = summary.count();

        List<List> args = new ArrayList<>();

        args.add(insertTableWithSuffix("count", getConventionName(summary.getId()), wallTime, count, getConventionTags(summary.getId()).toString()));
        args.add(insertTableWithSuffix("sum", getConventionName(summary.getId()), wallTime, summary.totalAmount(), getConventionTags(summary.getId()).toString()));
        args.add(insertTableWithSuffix("max", getConventionName(summary.getId()), wallTime, summary.max(), getConventionTags(summary.getId()).toString()));

        if(percentileValues.length > 0) {
            args.add(writePercentiles(summary, wallTime, percentileValues));
        }

        if(histogramCounts.length > 0) {
            args.add(writeHistogram(wallTime, summary, histogramCounts, count, getBaseTimeUnit()));
        }
        return args;
    }

    List<List> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = config().clock().wallTime();
        List<List> args = new ArrayList<>();
        args.add(insertTableWithSuffix("sum", getConventionName(timer.getId()), wallTime, timer.totalTime(getBaseTimeUnit()), getConventionTags(timer.getId()).toString()));
        return args;
    }

    List<List> writeTimer(Timer timer) {
        long wallTime = config().clock().wallTime();

        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();
        final ValueAtPercentile[] percentileValues = histogramSnapshot.percentileValues();
        final CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
        double count = timer.count();
        List<List> args = new ArrayList<>();

        args.add(insertTableWithSuffix("count", getConventionName(timer.getId()), wallTime, count, getConventionTags(timer.getId()).toString()));
        args.add(insertTableWithSuffix("sum", getConventionName(timer.getId()), wallTime, timer.totalTime(getBaseTimeUnit()), getConventionTags(timer.getId()).toString()));
        args.add(insertTableWithSuffix("max", getConventionName(timer.getId()), wallTime, timer.max(getBaseTimeUnit()), getConventionTags(timer.getId()).toString()));

        if(percentileValues.length > 0) {
            args.add(writePercentiles(timer, wallTime, percentileValues));
        }

        if(histogramCounts.length > 0) {
            args.add(writeHistogram(wallTime, timer, histogramCounts, count, getBaseTimeUnit()));
        }
        return args;
    }

    List<List> writePercentiles(Meter meter, long wallTime, ValueAtPercentile[] percentileValues){
        boolean forTimer = meter instanceof Timer;
        List<List> args = new ArrayList<>();
        for (ValueAtPercentile v : percentileValues) {
            args.add(
                insertTableWithSuffix("",
                        getConventionName(meter.getId().withTag(new ImmutableTag("quantile", doubleToGoString(v.percentile())))),
                        wallTime,
                        (forTimer ? v.value(getBaseTimeUnit()) : v.value()),
                        getConventionTags(meter.getId()).toString())
            );
        }
        return args;
    }

    List<List> writeHistogram(long wallTime, Meter meter, CountAtBucket[] histogramCounts, double count, @Nullable TimeUnit timeUnit) {
        List<List> args = new ArrayList<>();
        for(CountAtBucket c : histogramCounts) {
            args.add(
                insertTableWithSuffix("bucket",
                        getConventionName(meter.getId().withTag(new ImmutableTag("le", doubleToGoString(timeUnit == null ? c.bucket() : c.bucket(timeUnit))))),
                        wallTime,
                        c.count(),
                        getConventionTags(meter.getId()).toString())
            );
        }

        args.add(
            insertTableWithSuffix("bucket",
                    getConventionName(meter.getId().withTag(new ImmutableTag("le", "+Inf"))),
                    wallTime,
                    count,
                    getConventionTags(meter.getId()).toString())
        );
        return args;
    }

    List<List> writeFunctionCounter(FunctionCounter counter) {
        double count = counter.count();
        List<List> args = new ArrayList<>();
        if(Double.isFinite(count)) {
            args.add(insertTableWithSuffix("", getConventionName(counter.getId()), config().clock().wallTime(), counter.count(), getConventionTags(counter.getId()).toString()));
        }
        return args;
    }

    List<List> writeCounter(Counter counter) {
        List<List> args = new ArrayList<>();
        args.add(insertTableWithSuffix("", getConventionName(counter.getId()), config().clock().wallTime(), counter.count(), getConventionTags(counter.getId()).toString()));
        return args;
    }

    List<List> writeGauge(Gauge gauge) {
        double value = gauge.value();
        List<List> args = new ArrayList<>();
        if(Double.isFinite(value)){
            args.add(insertTableWithSuffix("", getConventionName(gauge.getId()), config().clock().wallTime(), value, getConventionTags(gauge.getId()).toString()));
        }
        return args;
    }

    List<List> writeTimeGauge(TimeGauge timeGauge) {
        double value = timeGauge.value(getBaseTimeUnit());
        List<List> args = new ArrayList<>();
        if(Double.isFinite(value)) {
            args.add(insertTableWithSuffix("", getConventionName(timeGauge.getId()), config().clock().wallTime(), value, getConventionTags(timeGauge.getId()).toString()));
        }
        return args;
    }

    private static String doubleToGoString(double d) {
        if (d == Double.POSITIVE_INFINITY || d == Double.MAX_VALUE || d == Long.MAX_VALUE) {
            return "+Inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (Double.isNaN(d)) {
            return "NaN";
        }
        return Double.toString(d);
    }

    List<List> writeLongTaskTimer(LongTaskTimer timer) {
        long wallTime = config().clock().wallTime();
        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();
        final ValueAtPercentile[] percentileValues = histogramSnapshot.percentileValues();
        final CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
        double count = timer.activeTasks();
        List<List> args = new ArrayList<>();

        args.add(insertTableWithSuffix("active.count", getConventionName(timer.getId()), wallTime, count, getConventionTags(timer.getId()).toString()));
        args.add(insertTableWithSuffix("duration.sum", getConventionName(timer.getId()), wallTime, timer.duration(getBaseTimeUnit()), getConventionTags(timer.getId()).toString()));
        args.add(insertTableWithSuffix("max", getConventionName(timer.getId()), wallTime, timer.max(getBaseTimeUnit()), getConventionTags(timer.getId()).toString()));

        if(percentileValues.length > 0) {
            args.add(writePercentiles(timer, wallTime, percentileValues));
        }

        if(histogramCounts.length > 0) {
            args.add(writeHistogram(wallTime, timer, histogramCounts, count, getBaseTimeUnit()));
        }
        return args;
    }

    List<? extends Serializable> insertTableWithSuffix(String suffix, String metric, long wallTime, Double value, String tags) {
//        List<Entity> args = new ArrayList<Entity>();
        String name = metric;
        if(suffix != null && !suffix.isEmpty()) {
            name = metric + "." + suffix;
        }
//        List<String> nameVector = Arrays.asList(name);
//        List<Long> timestampVector = Arrays.asList(wallTime);
//        List<Double> valueVector = Arrays.asList(value);
//        List<String> tagsVector = Arrays.asList(tags);

//        List<Vector> cols = Arrays.asList(new BasicStringVector(nameVector), new BasicLongVector(timestampVector), new BasicDoubleVector(valueVector), new BasicStringVector(tagsVector));
        List<? extends Serializable> cols = Arrays.asList(name, wallTime, value, tags);
        return cols;
    }

    @Override
    protected final TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final DolphinDBConfig config;
        private DBConnection conn;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        Builder(DolphinDBConfig config) {
            this.config = config;
            DolphinDBSession dolphinDBSession = new DolphinDBSession(config);
            this.conn = dolphinDBSession.getConn();
        }
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder dolphinDBSession(DolphinDBSession dolphinDBSession) {
            this.conn = dolphinDBSession.getConn();
            return this;
        }

        public DolphinDBMeterRegistry build() {
            return new DolphinDBMeterRegistry(config, clock, threadFactory);
        }


    }

}
