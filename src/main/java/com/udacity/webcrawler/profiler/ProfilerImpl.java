package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {
    private final Clock clock;
    private final ProfilingState state = new ProfilingState();
    private final ZonedDateTime startTime;

    @Inject
    ProfilerImpl(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.startTime = ZonedDateTime.now(clock);
    }

    @Override
    public <T> T wrap(Class<T> klass, T delegate) {
        Objects.requireNonNull(klass);
        boolean matched = Arrays.stream(klass.getDeclaredMethods())
                .filter(Objects::nonNull)
                .anyMatch(x -> x.getAnnotation(Profiled.class) != null);

        // See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.
        // A bit dicey but let's attempt
        if (matched) {
            Object proxy = Proxy.newProxyInstance(
                    ProfilerImpl.class.getClassLoader(),
                    new Class[]{klass},
                    new ProfilingMethodInterceptor(clock, delegate, state));

            return klass.cast(proxy);
        }

        throw new IllegalArgumentException(
                klass.getName() + " methods are not decorated/annotated with the Profiled annotation");
    }

    @Override
    public void writeData(Path path) throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(
                path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writeData(writer);
        }
    }

    @Override
    public void writeData(Writer writer) throws IOException {
        writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
        writer.write(System.lineSeparator());
        state.write(writer);
        writer.write(System.lineSeparator());
    }
}
