/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.testsupport.system.CapturedOutput;
import io.micrometer.core.testsupport.system.OutputCaptureExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.management.*;
import java.net.URI;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JCacheMetrics}.
 *
 * @author Oleksii Bondar
 */
@ExtendWith(OutputCaptureExtension.class)
class JCacheMetricsTest extends AbstractCacheMetricsTest {

    @SuppressWarnings("unchecked")
    // tag::setup[]
    Cache<String, String> cache;

    JCacheMetrics<String, String, Cache<String, String>> metrics;

    // end::setup[]

    private CacheManager cacheManager;

    private MBeanServer mbeanServer;

    private Long expectedAttributeValue = new Random().nextLong();

    @BeforeEach
    void setup() throws Exception {
        cache = mock(Cache.class);
        cacheManager = mock(CacheManager.class);
        when(cache.getCacheManager()).thenReturn(cacheManager);
        when(cache.getName()).thenReturn("testCache");
        when(cacheManager.getURI()).thenReturn(new URI("http://localhost"));
        // tag::setup_2[]
        metrics = new JCacheMetrics<>(cache, expectedTag);
        // end::setup_2[]

        // emulate MBean server with MBean used for statistic lookup
        mbeanServer = MBeanServerFactory.createMBeanServer();
        ObjectName objectName = new ObjectName("javax.cache:type=CacheStatistics");
        metrics.objectName = objectName;
        CacheMBeanStub mBean = new CacheMBeanStub(expectedAttributeValue);
        mbeanServer.registerMBean(mBean, objectName);
    }

    @AfterEach
    void tearDown() {
        if (mbeanServer != null) {
            MBeanServerFactory.releaseMBeanServer(mbeanServer);
        }
    }

    @Test
    void reportExpectedMetrics() {
        // tag::register[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);
        // end::register[]

        verifyCommonCacheMetrics(meterRegistry, metrics);

        FunctionCounter cacheRemovals = fetch(meterRegistry, "cache.removals").functionCounter();
        assertThat(cacheRemovals.count()).isEqualTo(expectedAttributeValue.doubleValue());
    }

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        // tag::monitor[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        JCacheMetrics.monitor(meterRegistry, cache, expectedTag);
        // end::monitor[]

        meterRegistry.get("cache.removals").tags(expectedTag).gauge();
    }

    @Test
    void constructInstanceViaStaticMethodMonitorWithVarArgTags() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        JCacheMetrics.monitor(meterRegistry, cache, "version", "1.0");

        meterRegistry.get("cache.removals").tags(Tags.of("version", "1.0")).gauge();
    }

    @Test
    void returnNullForCacheSize() {
        assertThat(metrics.size()).isNull();
    }

    @Test
    void returnMissCount() {
        assertThat(metrics.missCount()).isEqualTo(expectedAttributeValue);
    }

    @Test
    void returnEvictionCount() {
        assertThat(metrics.evictionCount()).isEqualTo(expectedAttributeValue);
    }

    @Test
    void returnHitCount() {
        assertThat(metrics.hitCount()).isEqualTo(expectedAttributeValue);
    }

    @Test
    void returnPutCount() {
        assertThat(metrics.putCount()).isEqualTo(expectedAttributeValue);
    }

    @Test
    void defaultValueWhenNoMBeanAttributeFound() throws MalformedObjectNameException {
        // change source MBean to emulate AttributeNotFoundException
        metrics.objectName = new ObjectName("javax.cache:type=CacheInformation");

        assertThat(metrics.hitCount()).isEqualTo(0L);
    }

    @Test
    void defaultValueWhenObjectNameNotInitialized() throws MalformedObjectNameException {
        // set cacheManager to null to emulate scenario when objectName not initialized
        when(cache.getCacheManager()).thenReturn(null);
        metrics = new JCacheMetrics<>(cache, expectedTag);

        assertThat(metrics.hitCount()).isEqualTo(0L);
    }

    @Test
    void doNotReportMetricWhenObjectNameNotInitialized() throws MalformedObjectNameException {
        // set cacheManager to null to emulate scenario when objectName not initialized
        when(cache.getCacheManager()).thenReturn(null);
        metrics = new JCacheMetrics<>(cache, expectedTag);
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindImplementationSpecificMetrics(registry);

        assertThat(registry.find("cache.removals").tags(expectedTag).meter()).isNull();
    }

    @Test
    @Issue("#2754")
    void cacheRemovalsIsGaugeWhenConfigured() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics = new JCacheMetrics<>(cache, expectedTag, false);
        metrics.bindTo(meterRegistry);

        assertThat(meterRegistry.get("cache.removals").tags(expectedTag).meter()).isNotNull().isInstanceOf(Gauge.class);
    }

    @Test
    @Issue("#5066")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void doNotReportMetricsWhenStatisticsAreDisabled(CapturedOutput output) throws Exception {
        Cache<String, String> disabledCache = mock(Cache.class);
        CacheManager disabledCacheManager = mock(CacheManager.class);
        when(disabledCache.getCacheManager()).thenReturn(disabledCacheManager);
        when(disabledCache.getName()).thenReturn("disabledCache");
        when(disabledCacheManager.getURI()).thenReturn(new URI("http://localhost"));

        CompleteConfiguration config = mock(CompleteConfiguration.class);
        when(config.isStatisticsEnabled()).thenReturn(false);
        when(disabledCache.getConfiguration(CompleteConfiguration.class)).thenReturn(config);

        JCacheMetrics<String, String, Cache<String, String>> disabledMetrics = new JCacheMetrics<>(disabledCache,
                expectedTag);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        disabledMetrics.bindTo(meterRegistry);

        assertThat(output).contains(
                "The cache 'disabledCache' is not recording statistics. No meters that require statistics will be registered.");
        assertThat(meterRegistry.find("cache.gets").tag("result", "hit").functionCounter()).isNull();
        assertThat(meterRegistry.find("cache.gets").tag("result", "miss").functionCounter()).isNull();
        assertThat(meterRegistry.find("cache.puts").functionCounter()).isNull();
        assertThat(meterRegistry.find("cache.evictions").functionCounter()).isNull();
        assertThat(meterRegistry.find("cache.removals").meter()).isNull();
    }

    private static class CacheMBeanStub implements DynamicMBean {

        private Long expectedAttributeValue;

        public CacheMBeanStub(Long attributeValue) {
            this.expectedAttributeValue = attributeValue;
        }

        @Override
        public Object getAttribute(String attribute)
                throws AttributeNotFoundException, MBeanException, ReflectionException {
            return expectedAttributeValue;
        }

        @Override
        public void setAttribute(Attribute attribute)
                throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        }

        @Override
        public AttributeList getAttributes(String[] attributes) {
            return mock(AttributeList.class);
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes) {
            return attributes;
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature)
                throws MBeanException, ReflectionException {
            return new Object();
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            return new MBeanInfo(CacheMBeanStub.class.getName(), "description", null, null, null, null);
        }

    }

}
