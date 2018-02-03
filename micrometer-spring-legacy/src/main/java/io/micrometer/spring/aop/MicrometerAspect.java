package io.micrometer.spring.aop;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

import javax.annotation.Nonnull;

import static org.springframework.util.StringUtils.isEmpty;

/**
 * AspectJ aspect for intercepting types or method annotated with @Timed.
 *
 * @author <a href="mailto:david@davidkarlsen.com">David J. M. Karlsen</a>
 */
@Aspect
@ManagedResource
public class MicrometerAspect {
    private static final String DEFAULT_ID = "method_monitor";

    private final MeterRegistry meterRegistry;
    private final String description = getClass().getCanonicalName() + " monitor";

    private boolean isActive = true;

    public MicrometerAspect(@Nonnull MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ManagedAttribute
    public void setActive(boolean active) {
        isActive = active;
    }

    @ManagedAttribute
    public boolean isActive() {
        return isActive;
    }

    @Pointcut("execution( public * *(..) ) && (@annotation(timed) || @within(timed))")
    public void pointcut(Timed timed) {
    }

    /**
     * To be used when pointcut marker is @Timed, either at type or method-level.
     *
     * @param proceedingJoinPoint
     * @param timed
     * @return
     * @throws Throwable
     */
    @Around("pointcut(timed)")
    public Object aroundMethod(ProceedingJoinPoint proceedingJoinPoint, Timed timed) throws Throwable {
        return monitor(proceedingJoinPoint, getTimer(timed));
    }

    /**
     * To be used when pointcut is application-defined
     *
     * @param proceedingJoinPoint
     * @return
     * @throws Throwable
     */
    public Object monitor(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        return monitor(proceedingJoinPoint, Timer.builder(DEFAULT_ID));
    }

    private Object monitor(ProceedingJoinPoint proceedingJoinPoint, Timer.Builder timer) throws Throwable {
        if (!isActive) {
            return proceedingJoinPoint.proceed();
        }

        return timer
            .tags(getTags(proceedingJoinPoint))
            .description(description)
            .register(meterRegistry).recordCallable(() -> {
                try {
                    return proceedingJoinPoint.proceed();
                } catch (Exception | Error e) {
                    throw e;
                } catch (Throwable t) {
                    // will never happen
                    throw new IllegalStateException(t);
                }
            });
    }

    private Tags getTags(ProceedingJoinPoint proceedingJoinPoint) {
        return Tags.of(
            Tag.of("class", proceedingJoinPoint.getStaticPart().getSignature().getDeclaringTypeName()),
            Tag.of("method", proceedingJoinPoint.getStaticPart().getSignature().getName()));
    }

    private Timer.Builder getTimer(Timed timed) {
        Timer.Builder builder = Timer.builder(isEmpty(timed.value()) ? DEFAULT_ID : timed.value())
            .tags(timed.extraTags());

        if (timed.percentiles().length > 0) {
            builder.publishPercentiles(timed.percentiles());
        }

        return builder;
    }
}
