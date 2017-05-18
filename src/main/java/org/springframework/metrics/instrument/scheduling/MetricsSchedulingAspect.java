package org.springframework.metrics.instrument.scheduling;

import com.google.common.base.Functions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.metrics.instrument.LongTaskTimer;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tags;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.annotation.Timed;
import org.springframework.metrics.instrument.internal.TimedUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
public class MetricsSchedulingAspect {
    private static final Log logger = LogFactory.getLog(MetricsSchedulingAspect.class);

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

        Map<Boolean, Timed> timedAnnots = TimedUtils.findTimed(method)
                .filter(t -> !t.value().isEmpty())
                .collect(Collectors.toMap(Timed::longTask, Functions.identity()));

        Timed shortTaskTimerAnnot = timedAnnots.get(false);
        Timer shortTaskTimer = null;
        if(shortTaskTimerAnnot != null)
            shortTaskTimer = registry.timer(shortTaskTimerAnnot.value(), Tags.tagList(shortTaskTimerAnnot.extraTags()));

        Timed longTaskTimerAnnot = timedAnnots.get(true);
        LongTaskTimer longTaskTimer = null;
        if(longTaskTimerAnnot != null)
            longTaskTimer = registry.longTaskTimer(longTaskTimerAnnot.value(), Tags.tagList(longTaskTimerAnnot.extraTags()));

        if(shortTaskTimer != null && longTaskTimer != null) {
            final Timer finalTimer = shortTaskTimer;
            return longTaskTimer.recordThrowable(() -> finalTimer.recordThrowable(pjp::proceed));
        }
        else if(shortTaskTimer != null) {
            return shortTaskTimer.recordThrowable(pjp::proceed);
        }
        else if(longTaskTimer != null) {
            return longTaskTimer.recordThrowable(pjp::proceed);
        }

        return pjp.proceed();
    }
}
