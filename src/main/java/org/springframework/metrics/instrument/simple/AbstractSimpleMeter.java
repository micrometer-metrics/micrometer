package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.internal.MeterId;

public abstract class AbstractSimpleMeter implements Meter {
    protected final MeterId id;

    AbstractSimpleMeter(MeterId id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return id.getTags();
    }
}
