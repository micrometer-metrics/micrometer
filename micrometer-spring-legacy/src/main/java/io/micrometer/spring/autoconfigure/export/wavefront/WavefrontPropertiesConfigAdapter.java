package io.micrometer.spring.autoconfigure.export.wavefront;

import io.micrometer.spring.autoconfigure.export.PropertiesConfigAdapter;
import io.micrometer.wavefront.WavefrontConfig;

/**
 * Adapter to convert {@link WavefrontProperties} to a {@link WavefrontConfig}.
 *
 * @author Jon Schneider
 */
public class WavefrontPropertiesConfigAdapter extends PropertiesConfigAdapter<WavefrontProperties> implements WavefrontConfig {

    public WavefrontPropertiesConfigAdapter(WavefrontProperties properties) {
        super(properties);
    }

    @Override
    public String get(String k) {
        return null;
    }

    @Override
    public String host() {
        return get(WavefrontProperties::getHost, WavefrontConfig.super::host);
    }

    @Override
    public String port() {
        return get(WavefrontProperties::getPort, WavefrontConfig.super::port);
    }

    @Override
    public String source() {
        return get(WavefrontProperties::getSource, WavefrontConfig.super::source);
    }

    @Override
    public boolean enableHistograms() {
        return get(WavefrontProperties::getEnableHistograms, WavefrontConfig.super::enableHistograms);
    }
}
