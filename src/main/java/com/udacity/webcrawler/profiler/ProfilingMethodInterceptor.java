package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

    private final Clock clock;
    private final Object callMethod;
    private final ProfilingState state;

    ProfilingMethodInterceptor(Clock clock, Object callMethod, ProfilingState state) {
        this.clock = Objects.requireNonNull(clock);
        this.callMethod = callMethod;
        this.state = state;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Instant startTimer = null;
        Object returnProxy;

        boolean hasProfileAnnotation = method.getAnnotation(Profiled.class) != null;
        if (hasProfileAnnotation) {
            startTimer = clock.instant();
        }

        try {
            returnProxy = method.invoke(callMethod, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            if (hasProfileAnnotation)
                state.record(callMethod.getClass(),
                        method,
                        Duration.between(Objects.requireNonNull(startTimer), clock.instant()));
        }

        return returnProxy;
    }
}
