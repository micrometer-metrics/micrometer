/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd.internal;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultNameResolver;
import io.netty.util.concurrent.EventExecutor;

import java.net.InetSocketAddress;

/**
 * A {@link DefaultNameResolver} that resolves using JDK's built-in domain name lookup mechanism.
 * Note that this resolver performs a blocking name lookup from the caller thread.
 */

public final class StatsdAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    public static final io.micrometer.statsd.internal.StatsdAddressResolverGroup INSTANCE = new io.micrometer.statsd.internal.StatsdAddressResolverGroup();

    public StatsdAddressResolverGroup() { }

    @Override
    public AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        AddressResolver<InetSocketAddress> inetSocketAddressAddressResolver = new DefaultNameResolver(executor).asAddressResolver();
        return inetSocketAddressAddressResolver;
    }

}