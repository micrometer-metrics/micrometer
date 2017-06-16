package org.springframework.metrics.export.atlas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import com.netflix.spectator.atlas.impl.MeasurementSerializer;
import com.netflix.spectator.atlas.impl.PublishPayload;
import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.sandbox.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

public class AtlasUtils {
    private static Logger logger = LoggerFactory.getLogger(AtlasUtils.class);

    /**
     * Convenience method for building an Atlas configuration for a registry that does NOT poll.
     */
    public static AtlasConfig pushConfig(String uri) {
        return new AtlasConfig() {
            @Override
            public String uri() {
                return uri;
            }

            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public String get(String k) {
                return System.getProperty(k);
            }
        };
    }

    /**
     * Convenience method for building an Atlas configuration that polls on a {@code step} interval.
     */
    public static AtlasConfig pollingConfig(String uri, Duration step) {
        return new AtlasConfig() {
            @Override
            public String uri() {
                return uri;
            }

            @Override
            public Duration step() {
                return step;
            }

            @Override
            public String get(String k) {
                return System.getProperty(k);
            }
        };
    }

    /**
     * Sends all measurements in one  batch to Atlas.
     */
    public static void atlasPublish(SpectatorMeterRegistry registry, AtlasConfig config) {
        // TODO much of this is duplicated from AtlasRegistry... would be nice if that was public API
        AsciiSet charset = AsciiSet.fromPattern(config.validTagCharacters());
        Map<String, AsciiSet> overrides = config.validTagValueCharacters()
                .keySet().stream()
                .collect(Collectors.toMap(k -> k, AsciiSet::fromPattern));

        SimpleModule module = new SimpleModule()
                .addSerializer(Measurement.class, new MeasurementSerializer(charset, overrides));
        ObjectMapper smileMapper = new ObjectMapper(new SmileFactory()).registerModule(module);

        if(registry.getSpectatorRegistry() instanceof AtlasRegistry) {
            AtlasRegistry atlasRegistry = (AtlasRegistry) registry.getSpectatorRegistry();
            List<Measurement> ms = atlasRegistry.stream()
                    .flatMap(m -> stream(m.measure().spliterator(), false))
                    .collect(Collectors.toList());

            PublishPayload p = new PublishPayload(config.commonTags(), ms);
            try {
                HttpClient.DEFAULT.newRequest("spectator-reg-atlas", URI.create(config.uri()))
                        .withMethod("POST")
                        .withConnectTimeout((int) config.connectTimeout().toMillis())
                        .withReadTimeout((int) config.readTimeout().toMillis())
                        .withContent("application/x-jackson-smile", smileMapper.writeValueAsBytes(p))
                        .send();
            } catch (IOException e) {
                logger.error("Unable to publish Atlas metrics", e);
            }
        }
    }
}
