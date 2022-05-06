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

import java.util.logging.Logger;

/**
 * NOTE: This file has been copied and slightly modified from
 * {io.netty.util.internal.logging}.
 *
 * Logger factory which creates a <a href=
 * "https://docs.oracle.com/javase/8/docs/technotes/guides/logging/">java.util.logging</a>
 * logger.
 *
 * @deprecated Please use {@code io.micrometer.common.util.internal.logging.JdkLogger}
 * instead.
 */
@Deprecated
public class JdkLoggerFactory extends InternalLoggerFactory {

    public static final InternalLoggerFactory INSTANCE = new JdkLoggerFactory();

    private JdkLoggerFactory() {
    }

    @Override
    public InternalLogger newInstance(String name) {
        return new JdkLogger(Logger.getLogger(name));
    }

}
