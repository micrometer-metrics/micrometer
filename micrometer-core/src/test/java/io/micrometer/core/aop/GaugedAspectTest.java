package io.micrometer.core.aop;

import io.micrometer.core.annotation.Gauged;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for {@link GaugedAspect} class, which provides comprehensive testing of the aspect-oriented
 * metrics gathering functionality using the {@link Gauged} annotation.
 *
 * @see GaugedAspect
 * @see Gauged
 * @see MeterRegistry
 * @see SimpleMeterRegistry
 */
class GaugedAspectTest {

    /** Registry for collecting and verifying metrics during tests */
    private MeterRegistry registry;

    /** The aspect instance being tested */
    private GaugedAspect aspect;

    /** Mock for the proceeding join point used in AOP */
    @Mock
    private ProceedingJoinPoint pjp;

    /** Mock for method signature information */
    @Mock
    private MethodSignature methodSignature;

    /** Mock for the static part of join point */
    @Mock
    private JoinPoint.StaticPart staticPart;

    /** Default metric name used when none is specified */
    private static final String DEFAULT_METRIC_NAME = "method.active.count";

    @BeforeEach
    void setUp() {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            registry = new SimpleMeterRegistry();
            aspect = new GaugedAspect(registry);
            setupMethodSignature();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Configures the mock method signature with default test values.
     * Sets up the necessary mock behaviors for method interception.
     */
    private void setupMethodSignature() {
        try {
            Method testMethod = TestService.class.getDeclaredMethod("annotatedMethod");
            when(methodSignature.getMethod()).thenReturn(testMethod);
            when(methodSignature.getDeclaringTypeName()).thenReturn(testMethod.getDeclaringClass().getName());
            when(methodSignature.getName()).thenReturn(testMethod.getName());

            when(staticPart.getSignature()).thenReturn(methodSignature);
            when(pjp.getSignature()).thenReturn(methodSignature);
            when(pjp.getStaticPart()).thenReturn(staticPart);
            when(pjp.getTarget()).thenReturn(new TestService());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Tests that method-level @Gauged annotations are properly handled.
     * Verifies gauge creation and value tracking for a simple method invocation.
     */
    @Test
    void shouldHandleMethodLevelGauge() throws Throwable {
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedMethod(pjp);

        Gauge gauge = registry.get("test.method.metric").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);
        verify(pjp).proceed();
    }

    /**
     * Tests that class-level @Gauged annotations are properly handled.
     * Verifies gauge creation and value tracking for methods in annotated classes.
     */
    @Test
    void shouldHandleClassLevelGauge() throws Throwable {
        // Setup test service and method
        GaugedService service = new GaugedService();
        Method method = GaugedService.class.getDeclaredMethod("someMethod");

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedClass(pjp);

        // Verify gauge creation and value
        Gauge gauge = registry.get("test.class.metric").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);
        verify(pjp).proceed();
    }

    /**
     * Tests the aspect's behavior under concurrent access.
     * Verifies thread safety and gauge value consistency when multiple threads
     * access the gauged method simultaneously.
     */
    @Test
    void shouldHandleConcurrentRequests() throws Throwable {
        int concurrentRequests = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        try (ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequests)) {
            when(pjp.proceed()).thenAnswer(invocation -> "result");

            // Launch concurrent requests
            for (int i = 0; i < concurrentRequests; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        aspect.gaugedMethod(pjp);
                        completionLatch.countDown();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // Coordinate test execution
            startLatch.countDown();
            assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // Cleanup executor
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(100, TimeUnit.MILLISECONDS);
            if (!terminated) {
                terminated = executorService.awaitTermination(1, TimeUnit.SECONDS);
            }
            assertThat(terminated).isTrue();

            // Verify gauge value after concurrent access
            Gauge gauge = registry.get("test.method.metric").gauge();
            assertThat(gauge.value()).isEqualTo(0.0);
        }
    }

    /**
     * Tests that the gauge properly handles exceptions during method execution.
     * Verifies that:
     * 1. The original exception is propagated
     * 2. The gauge value is properly decremented after exception
     * 3. The gauge maintains consistent state
     */
    @Test
    void shouldHandleExceptionAndDecrementGauge() throws Throwable {
        // Create and configure expected exception
        RuntimeException expectedException = new RuntimeException("test error");
        when(pjp.proceed()).thenThrow(expectedException);

        // Verify exception propagation
        assertThatThrownBy(() -> aspect.gaugedMethod(pjp))
            .isSameAs(expectedException);

        // Verify gauge was properly decremented
        Gauge gauge = registry.get("test.method.metric").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    /**
     * Tests the gauge creation skip predicate functionality.
     * Verifies that when the skip predicate returns true, no gauge is created
     * while still allowing the method to execute normally.
     */
    @Test
    void shouldSkipGaugeCreationWhenPredicateMatches() throws Throwable {
        // Create aspect with always-true skip predicate
        GaugedAspect aspectWithSkip = new GaugedAspect(registry, joinPoint -> true);
        when(pjp.proceed()).thenReturn("result");

        aspectWithSkip.gaugedMethod(pjp);

        // Verify gauge wasn't created but method was executed
        assertThat(registry.find("test.method.metric").gauge()).isNull();
        verify(pjp).proceed();
    }

    /**
     * Tests that methods annotated with @Gauged without a specified name
     * use the default metric name properly.
     */
    @Test
    void shouldUseDefaultMetricName() throws Throwable {
        // Setup method and service with default named gauge
        Method method = DefaultNameService.class.getDeclaredMethod("methodWithDefaultName");
        DefaultNameService service = new DefaultNameService();

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedMethod(pjp);

        // Verify gauge was created with default name
        assertThat(registry.get(DEFAULT_METRIC_NAME).gauge()).isNotNull();
    }

    /**
     * Tests that custom descriptions provided in @Gauged annotations
     * are properly applied to the created gauge.
     */
    @Test
    void shouldRespectCustomDescription() throws Throwable {
        // Setup method and service with custom description
        Method method = DescriptionService.class.getDeclaredMethod("methodWithDescription");
        DescriptionService service = new DescriptionService();

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedMethod(pjp);

        // Verify custom description was applied
        Gauge gauge = registry.get("test.description.metric").gauge();
        assertThat(gauge.getId().getDescription()).isEqualTo("Custom description for test");
    }

    /**
     * Tests that extra tags specified in @Gauged annotations are properly
     * applied to the created gauge. Verifies both tag presence and values.
     */
    @Test
    void shouldHandleExtraTags() throws Throwable {
        // Setup method and service with extra tags
        Method method = TaggedService.class.getDeclaredMethod("methodWithTags");
        TaggedService service = new TaggedService();

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedMethod(pjp);

        // Verify tags were properly applied
        Gauge gauge = registry.get("test.tags.metric").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getTags())
            .contains(Tag.of("region", "us-east-1"))
            .contains(Tag.of("env", "prod"));
    }

    /**
     * Tests that @Gauged annotations are properly inherited from parent classes.
     * Verifies that a child class inherits the gauge configuration from its parent.
     */
    @Test
    void shouldHandleInheritedClassAnnotations() throws Throwable {
        // Create actual instance of child service
        ChildService service = new ChildService();

        // Get actual method from child class
        Method childMethod = ChildService.class.getDeclaredMethod("annotatedMethod");

        // Setup mocks to simulate inheritance scenario
        when(methodSignature.getMethod()).thenReturn(childMethod);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.getSignature()).thenReturn(methodSignature);
        when(pjp.proceed()).thenReturn("result");

        // Mock the declaring class check to simulate parent class lookup
        Method spyChildMethod = spy(childMethod);
        doReturn(ParentService.class).when(spyChildMethod).getDeclaringClass();
        when(methodSignature.getMethod()).thenReturn(spyChildMethod);

        aspect.gaugedClass(pjp);

        // Verify inherited gauge was created
        assertThat(registry.get("test.inherited.metric").gauge()).isNotNull();
    }

    /**
     * Tests that gauges properly handle null descriptions in annotations.
     * Verifies that the gauge is created successfully with a null description.
     */
    @Test
    void shouldHandleNullDescription() throws Throwable {
        // Setup method and service
        Method method = NoDescriptionService.class.getDeclaredMethod("methodWithoutDescription");
        NoDescriptionService service = new NoDescriptionService();

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedMethod(pjp);

        // Verify gauge was created with null description
        Gauge gauge = registry.get("test.no.description.metric").gauge();
        assertThat(gauge.getId().getDescription()).isNull();
    }

    /**
     * Tests that multiple gauges within the same service class operate independently.
     * Verifies that each method's gauge is unique and properly tracked.
     */
    @Test
    void shouldHandleMultipleGaugesIndependently() throws Throwable {
        MultiGaugeService service = new MultiGaugeService();

        // Setup and test first method
        Method method1 = MultiGaugeService.class.getDeclaredMethod("method1");
        when(methodSignature.getMethod()).thenReturn(method1);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result1");

        aspect.gaugedMethod(pjp);
        Gauge gauge1 = registry.get("test.method1.metric").gauge();

        // Setup and test second method
        Method method2 = MultiGaugeService.class.getDeclaredMethod("method2");
        when(methodSignature.getMethod()).thenReturn(method2);
        when(pjp.proceed()).thenReturn("result2");

        aspect.gaugedMethod(pjp);
        Gauge gauge2 = registry.get("test.method2.metric").gauge();

        // Verify gauges are independent
        assertThat(gauge1)
            .isNotNull()
            .isNotSameAs(gauge2);
        assertThat(gauge2).isNotNull();
    }

    /**
     * Tests that custom tag functions are properly applied to gauges.
     * Verifies that programmatically added tags are present on the gauge.
     */
    @Test
    void shouldHandleCustomTagFunction() throws Throwable {
        // Create aspect with custom tag function
        GaugedAspect customAspect = new GaugedAspect(
            registry,
            joinPoint -> Tags.of("custom", "tag"),
            p -> false
        );
        when(pjp.proceed()).thenReturn("result");

        customAspect.gaugedMethod(pjp);

        // Verify custom tag was applied
        assertThat(registry.get("test.method.metric")
            .tag("custom", "tag")
            .gauge()).isNotNull();
    }

    /**
     * Tests the aspect's resilience to errors in tag functions.
     * Verifies that gauge creation proceeds even if tag function throws an exception.
     */
    @Test
    void shouldHandleTagFunctionError() throws Throwable {
        // Create aspect with failing tag function
        GaugedAspect customAspect = new GaugedAspect(
            registry,
            joinPoint -> { throw new RuntimeException("Tag function error"); },
            p -> false
        );
        when(pjp.proceed()).thenReturn("result");

        customAspect.gaugedMethod(pjp);

        // Verify gauge was created without tags
        Gauge gauge = registry.get("test.method.metric").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getTags()).isEmpty();
    }

    /**
     * Tests that overridden methods properly maintain their gauge configuration.
     * Verifies that method overriding doesn't affect gauge creation or tracking.
     */
    @Test
    void shouldHandleOverriddenMethods() throws Throwable {
        // Setup overridden method
        Method method = OverrideService.class.getDeclaredMethod("annotatedMethod");
        OverrideService service = new OverrideService();

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn("result");

        aspect.gaugedMethod(pjp);

        // Verify gauge was created for overridden method
        assertThat(registry.get("test.override.metric").gauge()).isNotNull();
    }

    /**
     * Tests that gauges properly handle long-running methods.
     * Verifies gauge consistency for methods with extended execution times.
     */
    @Test
    void shouldHandleLongRunningMethods() throws Throwable {
        when(pjp.proceed()).thenAnswer(invocation -> "result");

        aspect.gaugedMethod(pjp);

        // Verify gauge value after long-running method
        Gauge gauge = registry.get("test.method.metric").gauge();
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    /**
     * Tests that gauges properly handle recursive method calls.
     * Verifies gauge consistency when methods call themselves recursively.
     */
    @Test
    void shouldHandleRecursiveCalls() throws Throwable {
        // Setup recursive method
        RecursiveService service = new RecursiveService();
        Method method = RecursiveService.class.getDeclaredMethod("recursiveMethod", int.class);

        // Configure mocks
        when(methodSignature.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(service);
        when(pjp.proceed()).thenReturn(0);

        aspect.gaugedMethod(pjp);

        // Verify gauge value after recursive calls
        assertThat(registry.get("test.recursive.metric").gauge().value()).isEqualTo(0.0);
    }

    /**
     * Service class demonstrating class-level @Gauged annotation usage
     */
    @Gauged("test.class.metric")
    static class GaugedService {
        public String someMethod() {
            return "result";
        }
    }

    /**
     * Service class demonstrating method-level @Gauged annotation usage
     */
    static class TestService {
        /**
         * Method with @Gauged annotation.
         * The gauge should use the specified metric name for this method.
         *
         * @return A test result string
         */
        @Gauged("test.method.metric")
        public String annotatedMethod() {
            return "result";
        }
    }

    /**
     * Service class demonstrating @Gauged annotation with default naming
     */
    static class DefaultNameService {
        /**
         * Method with @Gauged annotation but no explicit name.
         * The gauge should use the default metric name for this method.
         *
         * @return A test result string
         */
        @Gauged
        public String methodWithDefaultName() {
            return "result";
        }
    }

    /**
     * Test service demonstrating custom description support in @Gauged annotation.
     * Used to verify that metric descriptions are properly captured and applied.
     */
    static class DescriptionService {
        /**
         * Method with a custom description in its gauge configuration.
         * The gauge created for this method should include the specified description.
         *
         * @return A test result string
         */
        @Gauged(value = "test.description.metric", description = "Custom description for test")
        public String methodWithDescription() {
            return "result";
        }
    }

    /**
     * Test service demonstrating @Gauged annotation without description.
     * Used to verify proper handling of metrics without explicit descriptions.
     */
    static class NoDescriptionService {
        /**
         * Method without an explicit description in its gauge configuration.
         * The gauge created for this method should handle null description gracefully.
         *
         * @return A test result string
         */
        @Gauged("test.no.description.metric")
        public String methodWithoutDescription() {
            return "result";
        }
    }

    /**
     * Test service demonstrating extra tags support in @Gauged annotation.
     * Used to verify that custom metric tags are properly applied.
     */
    static class TaggedService {
        /**
         * Method with extra tags in its gauge configuration.
         * The gauge should include region and environment tags with specified values.
         *
         * @return A test result string
         */
        @Gauged(value = "test.tags.metric", extraTags = {"region", "us-east-1", "env", "prod"})
        public String methodWithTags() {
            return "result";
        }
    }

    /**
     * Parent service class demonstrating class-level @Gauged annotation inheritance.
     * Used as the parent class in inheritance tests to verify annotation inheritance.
     */
    @Gauged("test.inherited.metric")
    static class ParentService {
        /**
         * Base implementation of annotated method.
         * The gauge configuration from the class-level annotation should apply.
         *
         * @return A parent-specific result string
         */
        public String annotatedMethod() {
            return "parent result";
        }
    }

    /**
     * Child service class extending ParentService to test annotation inheritance.
     * Used to verify that class-level @Gauged annotations are properly inherited.
     */
    static class ChildService extends ParentService {
        /**
         * Overridden implementation of the parent's annotated method.
         * Should inherit the gauge configuration from the parent class.
         *
         * @return A child-specific result string
         */
        @Override
        public String annotatedMethod() {
            return "child result";
        }
    }

    /**
     * Test service demonstrating multiple @Gauged annotations within a single class.
     * Used to verify that multiple gauges can operate independently within the same class.
     */
    static class MultiGaugeService {
        /**
         * First gauged method with its own metric name.
         * Should create and maintain an independent gauge.
         *
         * @return A method-specific result string
         */
        @Gauged("test.method1.metric")
        public String method1() {
            return "result1";
        }

        /**
         * Second gauged method with a different metric name.
         * Should create and maintain an independent gauge separate from method1.
         *
         * @return A method-specific result string
         */
        @Gauged("test.method2.metric")
        public String method2() {
            return "result2";
        }
    }

    /**
     * Test service demonstrating @Gauged annotation on an overridden method.
     * Used to verify gauge behavior with method overriding.
     */
    static class OverrideService {
        /**
         * Method with its own gauge configuration.
         * Demonstrates gauge behavior in method override scenarios.
         *
         * @return An override-specific result string
         */
        @Gauged("test.override.metric")
        public String annotatedMethod() {
            return "override result";
        }
    }

    /**
     * Test service demonstrating @Gauged annotation with recursive method calls.
     * Used to verify gauge behavior in recursive scenarios.
     */
    static class RecursiveService {
        /**
         * Recursive method with gauge tracking.
         * Tests gauge consistency during recursive method invocations.
         *
         * @param n The recursion depth parameter
         * @return The final result after recursion completes
         */
        @Gauged("test.recursive.metric")
        public int recursiveMethod(int n) {
            if (n <= 0) return 0;
            return recursiveMethod(n - 1);
        }
    }
}
