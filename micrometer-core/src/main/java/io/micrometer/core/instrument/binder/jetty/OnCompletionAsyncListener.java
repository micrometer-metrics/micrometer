/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jetty;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;

/**
 * {@link AsyncListener} that calls back to the handler. This class uses only object
 * references to work around
 * <a href="https://issues.redhat.com/browse/WFLY-13345">WFLY-13345</a>
 */
class OnCompletionAsyncListener implements AsyncListener {

    private final Object handler;

    OnCompletionAsyncListener(Object handler) {
        this.handler = handler;
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        ((TimedHandler) handler).onAsyncTimeout(event);
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
        event.getAsyncContext().addListener(this);
    }

    @Override
    public void onError(AsyncEvent event) {
    }

    @Override
    public void onComplete(AsyncEvent event) {
        ((TimedHandler) handler).onAsyncComplete(event);
    }

}
