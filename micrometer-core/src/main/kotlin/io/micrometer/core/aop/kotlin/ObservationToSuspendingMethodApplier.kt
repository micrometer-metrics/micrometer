package io.micrometer.core.aop.kotlin

import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import io.micrometer.observation.aop.applier.ObservationApplier
import org.aspectj.lang.ProceedingJoinPoint
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.util.Arrays
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

object ObservationToSuspendingMethodApplier : ObservationApplier {
    /**
     * Using such a simplistic implementation of [kotlin.coroutines.Continuation] interface is only enough,
     * because of how AopUtils#invokeJoinpointUsingReflection() in Spring Framework is implemented.
     * This means that this whole implementation is only usable in conjunction with Spring Framework.
     * @see <a href="https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/support/AopUtils.java">AopUtils.java</a>
     * @see <a href="https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/main/java/org/springframework/core/CoroutinesUtils.java">CoroutinesUtils.java</a>
     */
    private class DelegatingContinuation<T>(
        override val context: CoroutineContext,
        private val underlying: Continuation<T>
    ) : Continuation<T> {
        override fun resumeWith(result: Result<T>) {
            underlying.resumeWith(result)
        }
    }

    override fun isApplicable(pjp: ProceedingJoinPoint, method: Method): Boolean {
        return method.parameterTypes.isNotEmpty()
            && Continuation::class.java.isAssignableFrom(method.parameterTypes.last())
    }

    override fun applyAndProceed(pjp: ProceedingJoinPoint, method: Method, observation: Observation): Any {
        observation.start()
        return try {
            val continuation = pjp.args.last() as Continuation<*>
            val coroutineContext = continuation.context

            val newCoroutineContext = coroutineContext + observation.openScope().use {
                observation.observationRegistry.asContextElement()
            }

            val newArgs = Arrays.copyOf(pjp.args, pjp.args.size)
            newArgs[newArgs.size - 1] = DelegatingContinuation(newCoroutineContext, continuation)

            @Suppress("ReactiveStreamsUnusedPublisher")
            (pjp.proceed(newArgs) as Mono<*>).doOnError { error ->
                observation.error(error)
            }.doOnTerminate {
                observation.stop()
            }
        } catch (error: Throwable) {
            observation.error(error)
            observation.stop()
            throw error
        }
    }
}
