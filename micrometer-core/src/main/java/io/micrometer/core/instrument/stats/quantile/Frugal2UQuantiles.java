/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.stats.quantile;

import java.util.ArrayList;
import java.util.Collection;
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
 * Original code: &lt;https://github.com/dgryski/go-frugal&gt;
 * More info: http://blog.aggregateknowledge.com/2013/09/16/sketch-of-the-day-frugal-streaming/
 *
 * @author Maycon Viana Bordin &lt;mayconbordin@gmail.com&gt;
 * @author Jon Schneider
 */
public class Frugal2UQuantiles implements Quantiles {

    private final Quantile quantiles[];
    private final Collection<Double> registered;

    Frugal2UQuantiles(Quantile[] quantiles) {
        this.quantiles = quantiles;

        registered = new ArrayList<>();
        for (Quantile quantile : quantiles) {
            registered.add(quantile.q);
        }
    }

    @Override
    public void observe(double value) {
        for (Quantile q : quantiles) {
            q.insert(value);
        }
    }

    @Override
    public Double get(double q) {
        for (Quantile quantile : quantiles) {
            if (quantile.q == q)
                return quantile.m;
        }
        return 0.0;
    }

    public Collection<Double> monitored() {
        return registered;
    }

    static class Quantile {
        double m;
        double q;
        int step = 1;
        int sign = 0;
        Random r = new Random(new Random().nextInt());

        Quantile(double quantile, double estimate) {
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

    public static Builder quantile(double quantile, double estimate) {
        return new Builder().quantile(quantile, estimate);
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