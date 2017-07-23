/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument;

import io.micrometer.core.instrument.util.MeterId;

/**
 * @author Dmitry Poluyanov
 * @since 23.07.17
 */
public abstract class AbstractObserverHolder implements ObserverHolder {
    protected final MeterId id;
    private Observer[] observers;

    protected AbstractObserverHolder(MeterId id, Observer... observers){
        this.id = id;
        this.observers = observers;
    }

    @Override
    public void observe(double value) {
        for (Observer observer : observers) {
            observer.observe(value);
        }
    }
}
