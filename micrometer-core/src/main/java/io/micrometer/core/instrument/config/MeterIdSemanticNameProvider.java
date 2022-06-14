package io.micrometer.core.instrument.config;

import io.micrometer.common.docs.SemanticNameProvider;
import io.micrometer.core.instrument.Meter;

/**
 * A {@link Meter.Id} aware {@link SemanticNameProvider}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface MeterIdSemanticNameProvider extends SemanticNameProvider<Meter.Id> {


}
