package org.springframework.metrics.instrument.stats;

/**
 * Copied from https://github.com/mayconbordin/streaminer#quantiles
 */
public class PowerOfTwo {
    /**
     * Rounds the given value to the next power of two that is greater than the value.
     * @param value
     * @return
     */
    public static int ceilToNext(int value){
        Double powerOfTwo = Math.pow(2, Math.floor( Math.log10(value) / Math.log10(2) ));
        return powerOfTwo.intValue();
    }

    /**
     * Rounds the given value to the next power of two that is smaller than the value.
     * @param value
     * @return
     */
    public static Float floorToNext(Float value){
        Double powerOfTwo = Math.pow(2, Math.ceil( Math.log10(value) / Math.log10(2) ));
        return powerOfTwo.floatValue();
    }
    
    /**
     * Rounds the given value to the next power of two that is smaller than the value.
     * @param value
     * @return
     */
    public static double floorToNext(double value){
        return Math.pow(2, Math.ceil( Math.log10(value) / Math.log10(2) ));
    }
}
