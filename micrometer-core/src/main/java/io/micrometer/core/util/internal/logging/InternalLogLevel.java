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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.micrometer.core.util.internal.logging;

/**
 * NOTE: This file has been copied from {io.netty.util.internal.logging}.
 *
 * The log level that {@link InternalLogger} can log at.
 *
 * @deprecated Please use {@link io.micrometer.common.util.internal.logging.InternalLogLevel} instead.
 */
@Deprecated
public enum InternalLogLevel {
    /**
     * 'TRACE' log level.
     */
    TRACE,
    /**
     * 'DEBUG' log level.
     */
    DEBUG,
    /**
     * 'INFO' log level.
     */
    INFO,
    /**
     * 'WARN' log level.
     */
    WARN,
    /**
     * 'ERROR' log level.
     */
    ERROR
}
