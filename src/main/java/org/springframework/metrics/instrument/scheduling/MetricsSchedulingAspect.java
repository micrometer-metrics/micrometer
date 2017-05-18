package org.springframework.metrics.instrument.scheduling;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.annotation.Timed;

import java.lang.reflect.Method;

@Aspect
public class MetricsSchedulingAspect {
    protected final Log logger = LogFactory.getLog(MetricsSchedulingAspect.class);

    private final MeterRegistry registry;

    public MetricsSchedulingAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
    public Object timeScheduledOperation(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        String signature = pjp.getSignature().toShortString();

        if (method.getDeclaringClass().isInterface()) {
            try {
                method = pjp.getTarget().getClass().getDeclaredMethod(pjp.getSignature().getName(),
                        method.getParameterTypes());
            } catch (final SecurityException | NoSuchMethodException e) {
                logger.warn("Unable to perform metrics timing on " + signature, e);
                return pjp.proceed();
            }
        }

        Timed timed = method.getAnnotation(Timed.class);

        if (timed == null) {
            logger.debug("Skipping metrics timing on " + signature + ": no @Timed annotation is present on the method");
            return pjp.proceed();
        }

        if (timed.value().isEmpty()) {
            logger.warn("Unable to perform metrics timing on " + signature + ": @Timed annotation must have a value used to name the metric");
            return pjp.proceed();
        }

        return registry.timer(timed.value()).recordThrowable(pjp::proceed);
    }
}
