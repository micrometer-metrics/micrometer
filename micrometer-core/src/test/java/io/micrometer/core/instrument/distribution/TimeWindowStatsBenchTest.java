/**
 * Copyright 2020.
 */

package io.micrometer.core.instrument.distribution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Clock;

public class TimeWindowStatsBenchTest {

	TimeWindowMax max = new TimeWindowMax(Clock.SYSTEM, 100, 10);
	TimeWindowSum sum = new TimeWindowSum(10, Duration.ofMillis(100));
	TimeWindowStats stats = new TimeWindowStats(Clock.SYSTEM, 100, 10);
	TimeWindowStatsMax statsMax = new TimeWindowStatsMax(Clock.SYSTEM, 100, 10);
	TimeWindowBencher current = null;
	
	boolean started = true;
	
	static {
		try  {
		TimeWindowStatsBenchTest t = new TimeWindowStatsBenchTest();
		t.testMax();
		t.testStats();
		t.testStatsMax();
		t.testSum();
		} catch (Exception e) {}
		System.out.println();
	}

    @Test
    void testMax() throws Exception {
    	current = new TimeWindowBencher() {
			@Override
			public void record(double sample) {
				max.record(sample);
			}
			@Override
			public double poll() {
				return max.poll();
			}
		};
		System.out.println("Starting testMax.");
    	pTest();
		System.out.println("Ending testMax.");
    }

    @Test
    void testStats() throws Exception {
    	current = new TimeWindowBencher() {
			@Override
			public void record(double sample) {
				stats.record(sample);
			}
			@Override
			public double poll() {
				return stats.max();
			}
		};
		System.out.println("Starting testStats.");
    	pTest();
		System.out.println("Ending testStats.");
    }

    @Test
    void testStatsMax() throws Exception {
    	current = new TimeWindowBencher() {
			@Override
			public void record(double sample) {
				statsMax.record(sample);
			}
			@Override
			public double poll() {
				return statsMax.max();
			}
		};
		System.out.println("Starting testStatsMax.");
    	pTest();
		System.out.println("Ending testStatsMax.");
    }

    @Test
    void testSum() throws Exception {
//    	current = new TimeWindowBencher() {
//			@Override
//			public void record(double sample) {
//				sum.record((long) sample);
//			}
//			@Override
//			public double poll() {
//				return sum.poll();
//			}
//		};
//		System.out.println("Starting testSum.");
//    	pTest();
//		System.out.println("Ending testSum.");
    }

    private void pTest() throws Exception {
		System.out.println("Preparing threads.");
		
    	Thread poller = new Thread(new PollerThread());
		final ArrayList<Thread> allThreads = new ArrayList<Thread>(8);
		for (int i = 0 ; i < 8 ; i++) {
			allThreads.add(new Thread(new Recorder()));
		}

		long l = System.nanoTime();
		System.out.println("Starting threads.");
    	poller.start();
		for (final Thread t: allThreads) {
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		}

		System.out.println("Waiting threads.");
		for (final Thread t: allThreads) {
			t.join();
		}

		System.out.println("OK : " + (System.nanoTime() - l) / 1_000_000 + " ms");
    }
    
    private interface TimeWindowBencher {
		public double poll();
		public void record(double sample);
    }
    
    private class Recorder implements Runnable {
		@Override
		public void run() {
			int cnt = 0;
			while (cnt < 10_000_000) {
				current.record((cnt % 9) + (cnt / 100_000));
				++cnt;
			}
		}
    }
    
    private class PollerThread implements Runnable {
		@Override
		public void run() {
			while (started) {
				current.poll();
				synchronized (this) {
					try {
						this.wait(250);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
    }
    
    
	// Just for the test ...
	public static class TimeWindowStatsMax {
	    private static final long MAX_DOUBLE_TO_LONG = 0x7ff0000000000000L - 1;
	    
	    private final AtomicInteger rotatingUpdater = new AtomicInteger();
	
	    private final Clock clock;
	    private final long durationBetweenRotatesMillis;
	    private final AtomicHolder[] ringBuffer;
	    private int currentBucket;
	    private volatile long lastRotateTimestampMillis;
	
	    private final TimeWindowStatsMaxSnapshot snapshot = new TimeWindowStatsMaxSnapshot() {
	        @Override
	        public double max() {
	            return TimeWindowStatsMax.this.max();
	        }
	    };
	
	    public TimeWindowStatsMax(final Clock clock, final DistributionStatisticConfig config) {
	        this(clock, config.getExpiry().toMillis(), config.getBufferLength());
	    }
	
	    public TimeWindowStatsMax(final Clock clock, final long rotateFrequencyMillis, final int bufferLength) {
	        this.clock = clock;
	        durationBetweenRotatesMillis = rotateFrequencyMillis;
	        lastRotateTimestampMillis = clock.wallTime();
	        currentBucket = 0;
	
	        ringBuffer = new AtomicHolder[bufferLength];
	        for (int i = 0; i < bufferLength; i++) {
	            ringBuffer[i] = new AtomicHolder();
	        }
	        ringBuffer[currentBucket].age = lastRotateTimestampMillis;
	    }
	
	    private static class AtomicHolder {
	        long age = 0L;
	        AtomicLong max = new AtomicLong();
	
	        @Override
	        public String toString() {
	            return "AtomicHolder [age=" + age + ", max=" + max + "]";
	        }
	    }
	
	    /**
	     * Increments counter.
	     * @param sample Sample to record
	     */
	    public void record(final double sample) {
	        if (sample >= 0) {
	            rotate();
	            //No need to synchronize here
	            long s = Double.doubleToLongBits(sample);
	            //ringBuffer[currentBucket].max.updateAndGet((curMax) -> Math.max(curMax, s));
	            updateMax(ringBuffer[currentBucket].max, s);
	        }
	    }
	
	    private void updateMax(AtomicLong max, long sample) {
	        for (; ; ) {
	            long curMax = max.get();
	            if (curMax >= sample || max.compareAndSet(curMax, sample))
	                break;
	        }
	    }
	
	    /**
	     * @return max on time window.
	     */
	    public double max() {
	        rotate();
	        long max = 0;
	        synchronized (this) {
	            for (final AtomicHolder element : ringBuffer) {
	                if (element.age > 0) {
	                    max = Math.max(max, element.max.get());
	                }
	            }
	        }
	        return Double.longBitsToDouble(max);
	    }
	
	    /**
	     * @return age of the oldest time window's sample in milliseconds.
	     */
	    public long age() {
	        rotate();
	        long minAge = Long.MAX_VALUE;
	        synchronized (this) {
	            for (final AtomicHolder element : ringBuffer) {
	                if (element.age > 0) {
	                    minAge = Long.min(minAge, element.age);
	                }
	            }
	        }
	        return clock.wallTime() - minAge;
	    }
	
	    /**
	     * @return Unmodifiable Snapshot.
	     */
	    public TimeWindowStatsMaxSnapshot getSnapshot() {
	        return snapshot;
	    }
	
	    private void rotate() {
	        final long wallTime = clock.wallTime();
	        long timeSinceLastRotateMillis = wallTime - lastRotateTimestampMillis;
	        if (timeSinceLastRotateMillis < durationBetweenRotatesMillis) {
	            // Need to wait more for next rotation.
	            return;
	        }
	
	        if (!rotatingUpdater.compareAndSet(0, 1)) {
	            // Being rotated by other thread already.
	            return;
	        }
	
	        try {
	            int iterations = 0;
	            synchronized (this) {
	                do {
	                	int tmpCurrentBucket = currentBucket + 1;
	                    if (tmpCurrentBucket >= ringBuffer.length) {
	                        currentBucket = 0;
	                    } else {
	                    	currentBucket = tmpCurrentBucket;
	                    }
	
	                    //Init old buffers
	                    if (ringBuffer[currentBucket].age > 0L) {
		                    ringBuffer[currentBucket].age = 0L;
		                    ringBuffer[currentBucket].max.set(0L);
	                    }
	
	                    timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
	                    lastRotateTimestampMillis += durationBetweenRotatesMillis;
	                } while (timeSinceLastRotateMillis >= durationBetweenRotatesMillis && ++iterations < ringBuffer.length);
	
	                //New buffer starts now
	                ringBuffer[currentBucket].age = wallTime;
	            }
	        } finally {
	            rotatingUpdater.set(0);
	        }
	    }
	
	    /**
	     * Provides a way to expose a readonly snapshot of stats.
	     */
	    public interface TimeWindowStatsMaxSnapshot {
	        /**
	         * @return max on time window.
	         */
	        public double max();
	    }
	}
}
