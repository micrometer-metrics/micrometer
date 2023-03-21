/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.dynatrace.types;

import java.util.concurrent.TimeUnit;

/**
 * Interface for retrieving a {@link DynatraceSummarySnapshot}.
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
public interface DynatraceSummarySnapshotSupport {

    /**
     * @deprecated since 1.9.8. This method might lead to problems with a race condition
     * if values are added to the summary after reading the number of values already
     * recorded. Take a snapshot and use {@link DynatraceSummarySnapshot#getCount()}
     * instead.
     */
    @Deprecated
    boolean hasValues();

    DynatraceSummarySnapshot takeSummarySnapshot();

    DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit unit);

    DynatraceSummarySnapshot takeSummarySnapshotAndReset();

    DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit);

}
