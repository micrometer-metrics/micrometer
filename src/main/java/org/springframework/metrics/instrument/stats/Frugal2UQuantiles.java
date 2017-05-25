package org.springframework.metrics.instrument.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Modified from: https://github.com/mayconbordin/streaminer/blob/master/src/main/java/org/streaminer/stream/quantile/Frugal2U.java
 *
 * Implementation of the Frugal2U Algorithm.
 *
 * Reference:
 *  Ma, Qiang, S. Muthukrishnan, and Mark Sandler. "Frugal Streaming for
 *    Estimating Quantiles." Space-Efficient Data Structures, Streams, and
 *    Algorithms. Springer Berlin Heidelberg, 2013. 77-96.
 * Available at: http://arxiv.org/abs/1407.1121
 *
 * Original code: <https://github.com/dgryski/go-frugal>
 * More info: http://blog.aggregateknowledge.com/2013/09/16/sketch-of-the-day-frugal-streaming/
 *
 * @author Maycon Viana Bordin <mayconbordin@gmail.com>
 * @author Jon Schneider
 */
public class Frugal2UQuantiles implements Quantiles {

    private final Quantile quantiles[];

    public Frugal2UQuantiles(Quantile[] quantiles) {
        this.quantiles = quantiles;
    }

    public Frugal2UQuantiles(double[] quantiles, double initialEstimate) {
        this.quantiles = new Quantile[quantiles.length];
        for (int i=0; i<quantiles.length; i++) {
            this.quantiles[i] = new Quantile(quantiles[i], initialEstimate);
        }
    }

    @Override
    public void offer(double value) {
        for (Quantile q : quantiles) {
            q.insert(value);
        }
    }

    @Override
    public double get(double q) {
        for (Quantile quantile : quantiles) {
            if (quantile.q == q)
                return quantile.m;
        }

        return 0.0;
    }

    public static class Quantile {
        double m;
        double q;
        int step = 1;
        int sign = 0;
        Random r = new Random(new Random().nextInt());

        public Quantile(double quantile, double estimate) {
            m = estimate;
            q = quantile;
        }

        void insert(double s) {
            if (sign == 0) {
                m = s;
                sign = 1;
                return;
            }

            double rnd = r.nextDouble();

            if (s > m && rnd > 1-q) {
                step += sign * f(step);

                if (step > 0) {
                    m += step;
                } else {
                    m += 1;
                }

                if (m > s) {
                    step += (s - m);
                    m = s;
                }

                if (sign < 0 && step > 1) {
                    step = 1;
                }

                sign = 1;
            } else if (s < m && rnd > q) {
                step += -sign * f(step);

                if (step > 0) {
                    m -= step;
                } else {
                    m--;
                }

                if (m < s) {
                    step += (m - s);
                    m = s;
                }

                if (sign > 0 && step > 1) {
                    step = 1;
                }

                sign = -1;
            }
        }

        int f(int step) {
            return 1;
        }
    }

    public Quantile[] getQuantiles() {
        return quantiles;
    }

    public static Builder build() {
        return new Builder();
    }

    public static class Builder {
        List<Quantile> quantiles = new ArrayList<>();

        public Builder quantile(double quantile, double estimate) {
            quantiles.add(new Quantile(quantile, estimate));
            return this;
        }

        public Frugal2UQuantiles create() {
            return new Frugal2UQuantiles(quantiles.toArray(new Quantile[quantiles.size()]));
        }
    }
}