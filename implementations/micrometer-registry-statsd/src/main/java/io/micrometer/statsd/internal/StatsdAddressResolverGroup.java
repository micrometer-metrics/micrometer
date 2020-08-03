package io.micrometer.statsd.internal;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultNameResolver;
import io.netty.util.concurrent.EventExecutor;

import java.net.InetSocketAddress;

public final class StatsdAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    public static final io.micrometer.statsd.internal.StatsdAddressResolverGroup INSTANCE = new io.micrometer.statsd.internal.StatsdAddressResolverGroup();

    public StatsdAddressResolverGroup() { }

    @Override
    public AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        AddressResolver<InetSocketAddress> inetSocketAddressAddressResolver = new DefaultNameResolver(executor).asAddressResolver();
        return inetSocketAddressAddressResolver;
    }

}