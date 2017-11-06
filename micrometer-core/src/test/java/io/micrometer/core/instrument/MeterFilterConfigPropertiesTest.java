package io.micrometer.core.instrument;


import io.micrometer.core.instrument.histogram.HistogramConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class MeterFilterConfigPropertiesTest {

    private MeterFilterConfigProperties sut;

    @BeforeEach
    void setup(){
        sut = new MeterFilterConfigProperties();
        sut.getOverrides().put("ALL.enabled","false");
        sut.getOverrides().put("ALL.percentiles","0.3,0.4");
        sut.getOverrides().put("ALL.percentileHistogram","false");
        sut.getOverrides().put("ALL.maximumExpectedValue","7s");
        sut.getOverrides().put("ALL.minimumExpectedValue","10000000");
        sut.getOverrides().put("ALL.histogramBufferLength","100");
        sut.getOverrides().put("ALL.durationExpiry","1m");
        sut.getOverrides().put("ALL.sla","100ms, 1s");
        sut.getOverrides().put("jvm.enabled","true");
        sut.getOverrides().put("jvm.my.daemon.enabled","false");
        sut.getOverrides().put("jvm.memory.used","enabled");
        sut.getOverrides().put("jvm.combineTags","tag1|a,b;tag2");
        sut.getOverrides().put("jvm.some.timer.percentiles","0.95,0.99");
    }

    @Test
    void accept() {
        assertThat(sut.accept(newMeter("my.counter"))).isEqualTo(MeterFilterReply.DENY);
        assertThat(sut.accept(newMeter("jvm.thing"))).isEqualTo(MeterFilterReply.ACCEPT);
        assertThat(sut.accept(newMeter("jvm.my.daemon"))).isEqualTo(MeterFilterReply.DENY);
        assertThat(sut.accept(newMeter("jvm.my.multi.daemon"))).isEqualTo(MeterFilterReply.ACCEPT);
        assertThat(sut.accept(newMeter("jvm.memory.used"))).isEqualTo(MeterFilterReply.ACCEPT);
    }

    @Test
    void configure() {
        HistogramConfig parent = HistogramConfig.builder().build();

        assertThat(sut.configure(newMeter("my.timer"),parent).getPercentiles()).contains(0.3, 0.4);
        HistogramConfig timerConf = sut.configure(newMeter("jvm.some.timer"), parent);
        assertThat(timerConf.getPercentiles()).hasSize(2);
        assertThat(timerConf.getPercentiles()).contains(0.95, 0.99);
    }

    @Test
    void configErrors(){
        assertThatThrownBy(() -> newFilter("ALL.enabled","flase"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.enabled value:flase");

        assertThatThrownBy(() -> newFilter("ALL.percentiles","O.3"))
            .describedAs("O is not 0 (and is unparsable as double)")
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.percentiles value:O.3");

        assertThatThrownBy(() -> newFilter("ALL.percentileHistogram","troo"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.percentileHistogram value:troo");

        assertThatThrownBy(() -> newFilter("ALL.maximumExpectedValue","blerp"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.maximumExpectedValue value:blerp");

        assertThatThrownBy(() -> newFilter("ALL.minimumExpectedValue","OneHundred"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.minimumExpectedValue value:OneHundred");

        assertThatThrownBy(() -> newFilter("ALL.histogramBufferLength","short"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.histogramBufferLength value:short");

        assertThatThrownBy(() -> newFilter("ALL.durationExpiry","1 minute"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.durationExpiry value:1 minute");

        assertThatThrownBy(() -> newFilter("ALL.sla","100ms;1s"))
            .describedAs("Commas must be used to delimit")
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Error parsing rule ALL.sla value:100ms;1s");

    }

    private MeterFilterConfigProperties newFilter(String key, String value) {
        MeterFilterConfigProperties filter = new MeterFilterConfigProperties();
        filter.getOverrides().put(key, value);

        Meter.Id id = newMeter("my.counter");
        filter.accept(id);
        filter.configure(id, new HistogramConfig());
        return filter;
    }

    private Meter.Id newMeter(String name) {
        return new Meter.Id(name, Collections.emptyList(), "Base", "Description");
    }
}
