package io.micrometer.core.instrument;


import io.micrometer.core.instrument.histogram.HistogramConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MeterFilterConfigPropertiesTest {

    private MeterFilterConfigProperties sut;

    @BeforeEach
    public void setup(){
        sut = new MeterFilterConfigProperties();
        sut.getFilter().put("ALL.enabled","false");
        sut.getFilter().put("ALL.percentiles","0.3,0.4");
        sut.getFilter().put("ALL.percentileHistogram","false");
        sut.getFilter().put("ALL.maximumExpectedValue","PT7S");
        sut.getFilter().put("ALL.minimumExpectedValue","10000000");
        sut.getFilter().put("ALL.histogramBufferLength","100");
        sut.getFilter().put("ALL.durationExpiry","PT60S");
        sut.getFilter().put("ALL.sla","PT0.1S,PT1S");
        sut.getFilter().put("jvm.enabled","true");
        sut.getFilter().put("jvm.my.daemon.enabled","false");
        sut.getFilter().put("jvm.memory.used","enabled");
        sut.getFilter().put("jvm.combineTags","tag1|a,b;tag2");
        sut.getFilter().put("jvm.some.timer.percentiles","0.95,0.99");
    }

    @Test
    public void accept() {
        assertThat(sut.accept(newMeter("my.counter"))).isEqualTo(MeterFilterReply.DENY);
        assertThat(sut.accept(newMeter("jvm.thing"))).isEqualTo(MeterFilterReply.ACCEPT);
        assertThat(sut.accept(newMeter("jvm.my.daemon"))).isEqualTo(MeterFilterReply.DENY);
        assertThat(sut.accept(newMeter("jvm.my.multi.daemon"))).isEqualTo(MeterFilterReply.ACCEPT);
        assertThat(sut.accept(newMeter("jvm.memory.used"))).isEqualTo(MeterFilterReply.ACCEPT);
    }

    @Test
    public void configure() {
        HistogramConfig parent = HistogramConfig.builder().build();

        assertThat(sut.configure(newMeter("my.timer"),parent).getPercentiles()).contains(0.3, 0.4);
        HistogramConfig timerConf = sut.configure(newMeter("jvm.some.timer"), parent);
        assertThat(timerConf.getPercentiles()).hasSize(2);
        assertThat(timerConf.getPercentiles()).contains(0.95, 0.99);

    }

    Meter.Id newMeter(String name) {
        return new Meter.Id(name, Collections.emptyList(), "Base", "Description");
    }
}
