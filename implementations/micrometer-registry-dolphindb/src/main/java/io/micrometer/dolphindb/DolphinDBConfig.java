package io.micrometer.dolphindb;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.push.PushRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

public interface DolphinDBConfig extends PushRegistryConfig {

    DolphinDBConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "io/micrometer/dolphindb";
    }

    default String ip() {
        return getUrlString(this, "ip").orElse("localhost");
    }

    default int port() {
        return getInteger(this, "port").orElse(8848);
    }

    default String userName() {
        return getSecret(this, "userName").orElse("admin");
    }

    default String password() {
        return getSecret(this, "password").orElse("123456");
    }

    default String directory() {
        return getString(this, "directory").orElse("dfs://mydb");
    }

    @Nullable
    default String engine() {
        return getString(this, "engine").orElse("TSDB");
    }

    default String partitionType() {
        return getString(this, "partitionType").orElse("VALUE");
    }

    default String partitionSchema() {
        return getString(this, "partitionSchema").orElse("DATE");
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> PushRegistryConfig.validate(c),
                checkRequired("ip", DolphinDBConfig::ip),
                checkRequired("directory", DolphinDBConfig::directory),
                checkRequired("partitionType", DolphinDBConfig::partitionType),
                checkRequired("partitionType", DolphinDBConfig::partitionType),
                checkRequired("engine", DolphinDBConfig::engine));
    }

}
