/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.db;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.Timer;
import io.micrometer.api.instrument.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import org.jooq.ExecuteContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DefaultExecuteListener;

import java.util.function.Supplier;

class JooqExecuteListener extends DefaultExecuteListener {
    private final MeterRegistry registry;
    private final Iterable<Tag> tags;
    private final Supplier<Iterable<Tag>> queryTagsSupplier;

    private final Object sampleLock = new Object();
    private final Map<ExecuteContext, Timer.Sample> sampleByExecuteContext = new HashMap<>();

    public JooqExecuteListener(MeterRegistry registry, Iterable<Tag> tags, Supplier<Iterable<Tag>> queryTags) {
        this.registry = registry;
        this.tags = tags;
        this.queryTagsSupplier = queryTags;
    }

    @Override
    public void start(ExecuteContext ctx) {
        startTimer(ctx);
    }

    @Override
    public void executeStart(ExecuteContext ctx) {
        startTimer(ctx);
    }

    private void startTimer(ExecuteContext ctx) {
        Timer.Sample started = Timer.start(registry);
        synchronized (sampleLock) {
            sampleByExecuteContext.put(ctx, started);
        }
    }

    @Override
    public void executeEnd(ExecuteContext ctx) {
        stopTimerIfStillRunning(ctx);
    }

    @Override
    public void end(ExecuteContext ctx) {
        stopTimerIfStillRunning(ctx);
    }

    private void stopTimerIfStillRunning(ExecuteContext ctx) {
        Iterable<Tag> queryTags = queryTagsSupplier.get();
        if (queryTags == null) return;

        Timer.Sample sample;
        synchronized (sampleLock) {
            sample = sampleByExecuteContext.remove(ctx);
        }
        if (sample == null) return;

        String exceptionName = "none";
        String exceptionSubclass = "none";

        Exception exception = ctx.exception();
        if (exception != null) {
            if (exception instanceof DataAccessException) {
                DataAccessException dae = (DataAccessException) exception;
                exceptionName = dae.sqlStateClass().name().toLowerCase().replace('_', ' ');
                exceptionSubclass = dae.sqlStateSubclass().name().toLowerCase().replace('_', ' ');
                if (exceptionSubclass.contains("no subclass")) {
                    exceptionSubclass = "none";
                }
            } else {
                String simpleName = exception.getClass().getSimpleName();
                exceptionName = StringUtils.isNotBlank(simpleName) ? simpleName : exception.getClass().getName();
            }
        }

        //noinspection unchecked
        sample.stop(Timer.builder("jooq.query")
                .description("Execution time of a SQL query performed with JOOQ")
                .tags(queryTags)
                .tag("type", ctx.type().name().toLowerCase())
                .tag("exception", exceptionName)
                .tag("exception.subclass", exceptionSubclass)
                .tags(tags)
                .register(registry)
        );
    }
}
