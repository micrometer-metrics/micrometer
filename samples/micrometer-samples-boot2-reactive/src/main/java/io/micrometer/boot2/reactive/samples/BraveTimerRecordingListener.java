/**
 * Copyright 2021 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.boot2.reactive.samples;

import brave.Span;
import brave.Tracer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Context;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.TimerRecordingListener;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class BraveTimerRecordingListener implements TimerRecordingListener {
    final Tracer tracer;
    // TODO not specific to this listener but leaks are possible where onStart is called but onStop is not
    ConcurrentMap<Timer.Sample, SpanContext> contextMap = new ConcurrentHashMap<>();

    BraveTimerRecordingListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onStart(Sample sample, Context context) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onError(Sample sample, Context context, Throwable throwable) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onRestore(Sample sample, Context context) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onStop(Sample sample, Context context, Timer timer, Duration duration) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean supportsContext(Context context) {
        // TODO Auto-generated method stub
        return false;
    }

   
}
