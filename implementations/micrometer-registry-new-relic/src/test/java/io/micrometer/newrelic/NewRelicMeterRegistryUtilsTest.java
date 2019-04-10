package io.micrometer.newrelic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class NewRelicMeterRegistryUtilsTest {

    private static class Checker<T> implements Consumer<List<T>> {
        final int[] batches;
        int position = 0;

        Checker(int[] batches) {
            this.batches = batches;
        }

        @Override
        public void accept(List<T> t) {
            System.out.println(t.size()+" "+t);
            assertEquals(batches[position],t.size());
            position+=1;
        }

        void checkComplete(){
            assertEquals(batches.length,position);
        }
    }

    @Test
    void sendInBatches() {
        Checker<String> oneChecker = new Checker<>(new int[]{1});
        NewRelicMeterRegistry.sendInBatches(2, Collections.singletonList("0"),oneChecker);
        oneChecker.checkComplete();

        Checker<String> evenChecker = new Checker<>(new int[]{2, 2});
        NewRelicMeterRegistry.sendInBatches(2, Arrays.asList("0","1","2","3"),evenChecker);
        evenChecker.checkComplete();

        Checker<String> oddChecker = new Checker<>(new int[]{2, 1});
        NewRelicMeterRegistry.sendInBatches(2, Arrays.asList("0","1","2"),oddChecker);
        oddChecker.checkComplete();

        Checker<String> emptyChecker = new Checker<>(new int[]{});
        NewRelicMeterRegistry.sendInBatches(2, Collections.emptyList(),emptyChecker);
        emptyChecker.checkComplete();
    }
}
