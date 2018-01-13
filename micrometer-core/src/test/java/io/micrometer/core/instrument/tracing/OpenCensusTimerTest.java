package io.micrometer.core.instrument.tracing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.export.SpanExporter;
import io.opencensus.trace.samplers.Samplers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCensusTimerTest {

    private Tracer tracer;
    private SimpleMeterRegistry registry;
    private ArrayList<SpanData> exportedSpans;

    @BeforeEach
    void setup(){
        tracer = Tracing.getTracer();
        registry = new SimpleMeterRegistry();
        registry.config().commonTags("commonTag1", "commonVal1");

        exportedSpans = new ArrayList<>();
        Tracing.getTraceConfig().updateActiveTraceParams(TraceParams.DEFAULT.toBuilder().setSampler(Samplers.probabilitySampler(1.0)).build());
        Tracing.getExportComponent().getSpanExporter().registerHandler("test", new SpanExporter.Handler() {
            @Override
            public void export(Collection<SpanData> spanDataList) {
                exportedSpans.addAll(spanDataList);
            }
        });
    }

    @AfterEach
    void cleanup(){
        Tracing.getExportComponent().getSpanExporter().unregisterHandler("test");
    }

    @Test
    void tracingTimer() throws InterruptedException {


        OpenCensusTimer.builder("trace.outer.timer", tracer).tags("testTag1","testVal1").register(registry).record(() -> {
            OpenCensusTimer.builder("trace.inner.timer", tracer).tags("testTag2","testVal2").register(registry).record(() -> {
                //Nothing to do here
            });
        });

        registry.getMeters();

        waitForSpans(Duration.ofSeconds(5), 2);

        SpanData innerSpan = exportedSpans.get(0);
        SpanData outerSpan = exportedSpans.get(1);
        assertThat(innerSpan.getName()).isEqualTo("trace.inner.timer");
        assertThat(innerSpan.getAttributes().getAttributeMap().get("commonTag1")).describedAs("Common tags are applied").isEqualTo(AttributeValue.stringAttributeValue("commonVal1"));
        assertThat(innerSpan.getParentSpanId()).isNotNull();


        assertThat(outerSpan.getName()).isEqualTo("trace.outer.timer");
        assertThat(outerSpan.getAttributes().getAttributeMap()).hasSize(2);
        assertThat(outerSpan.getAttributes().getAttributeMap().get("testTag1")).isEqualTo(AttributeValue.stringAttributeValue("testVal1"));
        assertThat(outerSpan.getParentSpanId()).isNull();
    }

    private void waitForSpans(Duration duration, int spanCount) {
        long endTime = System.currentTimeMillis() + duration.toMillis();
        while(endTime > System.currentTimeMillis()) {
            if (exportedSpans.size() == spanCount) {
                return;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                //ignored
            }
        }
        throw new AssertionError("Expected spans of "+spanCount+" not found after waiting "+duration.toMillis()+"ms Spans Found:"+exportedSpans.size());
    }



}
