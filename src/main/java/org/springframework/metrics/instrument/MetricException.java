package org.springframework.metrics.instrument;

public class MetricException extends RuntimeException {

    public MetricException(String msg, Throwable e) {
        super(msg, e);
    }


    public MetricException(Throwable e) {
        super(e);
    }

}
