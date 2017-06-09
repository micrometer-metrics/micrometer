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
package org.springframework.metrics.instrument.stats.quantile;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class is an implementation of the Greenwald-Khanna algorithm for computing
 * epsilon-approximate quantiles of large data sets. In its pure form it is an offline
 * algorithm. But it is used as a black box by many online algorithms for computing
 * epsilon-approximate quantiles on data streams.<br>
 * Our implementation widely adapts the original idea published by <i>Michael Greenwald
 * </i> and <i>Sanjeev Khanna</i> in their paper <i>"Space-Efficient Online Computation
 * of Quantile Summaries"</i>. Contrary to their idea this implementation uses a list
 * rather than a tree structure to maintain the elements.
 *
 * @author Markus Kokott, Carsten Przyluczky
 * @author Jon Schneider
 */
public class GKQuantiles implements Quantiles {
    /**
     * This implementation, unlike others, monitors all potential quantiles equally, but
     * we need somewhere to store the state of what we INTEND to ask later of it
     */
    private Collection<Double> monitored;

    private List<Tuple> summary;
    private double minimum;
    private double maximum;
    private int stepsUntilMerge;

    /**
     * GK needs 1 / (2 * epsilon) elements to complete it's initial phase
     */
    private boolean initialPhase;
    private Integer count;

    /**
     * This value specifies the error bound.
     */
    private double epsilon;

    /**
     * Creates a new GKQuantiles object that computes epsilon-approximate quantiles.
     *
     * @param epsilon The maximum error bound for quantile estimation.
     */
    public GKQuantiles(Collection<Double> monitored, double epsilon) {
        this.monitored = monitored;

        if (epsilon <= 0 || epsilon >= 1) {
            throw new RuntimeException("An appropriate epsilon value must lay between 0 and 1.");
        }

        setEpsilon(epsilon);
    }

    @Override
    public Collection<Double> monitored() {
        return monitored;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
        this.minimum = Double.MAX_VALUE;
        this.maximum = Double.MIN_VALUE;
        Double mergingSteps = Math.floor(1.0 / (2.0 * epsilon));
        this.stepsUntilMerge = mergingSteps.intValue();
        this.summary = new CopyOnWriteArrayList<>();
        this.count = 0;
        this.initialPhase = true;
    }

    @Override
    public void observe(double value) {
        insertItem(value);
        incrementCount();
        if (count % stepsUntilMerge == 0 && !initialPhase) {
            compress();
        }
    }

    /**
     * Estimates appropriate quantiles (i.e. values that holds epsilon accuracy). Note that if
     * the query parameter doesn't lay in [0,1] <code>Double.NaN</code> is returned! The same
     * result will be returned if an empty instance of GK is queried.
     *
     * @param q a <code>float</code> value
     * @return an estimated quantile represented by a {@link Double}. Will return {@link Double#NaN}
     * if <code>phi</code> isn't between 0 and 1 or this instance of <code>GKQuantiles</code> is empty.
     */
    @Override
    public Double get(double q) {
        /*--------------------------------------------------------
         * special cases if some queries occur in a very early state
         */
        if (count == 0 || q < 0 || q > 1) {
            return Double.NaN;
        }
        if (count == 1) {
            return minimum;
        }
        if (count == 2) {
            if (q < 0.5) {
                return minimum;
            }
            if (q >= 0.5) {
                return maximum;
            }
        }
        //---------------------------------------------------------


        int wantedRank = (int) ((q * count.floatValue()));
        int currentMinRank = 0;
        int currentMaxRank;
        Double tolerance = (epsilon * count.doubleValue());

        // if the wanted range is as most epsilon * count ranks smaller than the maximum the maximum
        // will always be an appropriate estimate
        if (wantedRank > count - tolerance) {
            return maximum;
        }

        // if the wanted range is as most epsilon * count ranks greater than the minimum the minimum
        // will always be an appropriate estimate
        if (wantedRank < tolerance) {
            return minimum;
        }

        Tuple lastTuple = summary.get(0);

        Object[] copyOfSummary = summary.toArray();

        // usually a range is estimated during this loop. it's element's value will be returned
        for (Object aCopyOfSummary : copyOfSummary) {
            Tuple currentTuple = (Tuple) aCopyOfSummary;
            currentMinRank += currentTuple.getOffset();
            currentMaxRank = currentMinRank + currentTuple.getRange();

            if (currentMaxRank - wantedRank <= tolerance) {
                lastTuple = currentTuple;
                if (wantedRank - currentMinRank <= tolerance) {

                    return currentTuple.getValue();
                }
            }
        }

        return lastTuple.getValue();
    }

    /**
     * Checks whether <code>item</code> is a new extreme value (i.e. minimum or maximum) or lays between those values
     * and calls the appropriate insert method.
     *
     * @param item {@link Double} value of current element
     */
    private void insertItem(Double item) {
        if (item < minimum) {
            insertAsNewMinimum(item);
            return;
        }

        if (item >= maximum) {
            insertAsNewMaximum(item);
            return;
        }

        insertInBetween(item);
    }

    /**
     * This method will be called every time an element arrives whose value is smaller than the value
     * of the current minimum. Contrary to "normal" elements, the minimum's range have to be zero.
     *
     * @param item - new element with a {@link Double} value smaller than the current minimum of the summary.
     */
    private void insertAsNewMinimum(Double item) {
        minimum = item;
        Tuple newTuple = new Tuple(item, 1, 0);
        summary.add(0, newTuple);
    }

    /**
     * This method will be called every time an element arrives whose value is greater than the value
     * of the current maximum. Contrary to "normal" elements, the maximum's range have to be zero.
     *
     * @param item - new element with a {@link Double} value greater than the current maximum of the summary.
     */
    private void insertAsNewMaximum(Double item) {
        if (item == maximum) {
            Tuple newTuple = new Tuple(item, 1, computeRangeForNewTuple(summary.get(summary.size() - 1)));
            summary.add(summary.size() - 2, newTuple);
        } else {
            maximum = item;
            Tuple newTuple = new Tuple(item, 1, 0);
            summary.add(newTuple);
        }
    }

    /**
     * Every time a new element gets processed this method is called to insert this element into
     * the summary. During initial phase element's ranges have to be zero. After this phase every
     * new element's range depends on its successor.
     *
     * @param item - a new arrived element represented by a {@link Double} value.
     */
    private void insertInBetween(Double item) {
        Tuple newTuple = new Tuple(item, 1, 0);

        for (int i = 0; i < summary.size() - 1; i++) {
            Tuple current = summary.get(i);
            Tuple next = summary.get(i + 1);

            if (item >= current.getValue() && item < next.getValue()) {
                // while GK have seen less than 1 / (2*epsilon) elements, all elements must have an
                // offset of 0
                if (!initialPhase) {
                    newTuple.setRange(computeRangeForNewTuple(next));
                }

                summary.add(i + 1, newTuple);
                return;
            }
        }
    }

    /**
     * Increments <code>count</code> and ends the initial phase if enough elements have been seen.
     */
    private void incrementCount() {
        count++;
        if (count.equals(stepsUntilMerge)) {
            initialPhase = false;
        }
    }

    /**
     * Due to space efficiency the summary is compressed periodically
     */
    private void compress() {
        List<List<Tuple>> partitions = getPartitionsOfSummary();
        List<Tuple> mergedSummary = new CopyOnWriteArrayList<>();

        // just merge tuples per partition and concatenate the single resulting working sets

        mergedSummary.addAll(partitions.get(partitions.size() - 1));

        for (int i = partitions.size() - 2; i > 0; i--) {
            mergedSummary.addAll(mergeWorkingSet(partitions.get(i)));
        }

        mergedSummary.addAll(partitions.get(0));

        mergedSummary = sortWorkingSet(mergedSummary);
        summary = mergedSummary;
    }

    /**
     * merges a whole partition and therefore saves space.
     *
     * @param workingSet a partition (created by {@link #getPartitionsOfSummary()}) or parts of it
     * @return a {@link LinkedList} of {@link Tuple} containing the merged working set.
     */
    private List<Tuple> mergeWorkingSet(List<Tuple> workingSet) {
        // recursion stops here
        if (workingSet.size() < 2) {
            return workingSet;
        }

        LinkedList<Tuple> mergedWorkingSet = new LinkedList<>();            // resulting working set
        LinkedList<Tuple> currentWorkingSet = new LinkedList<>();            // elements for this step of recursion
        LinkedList<Tuple> remainingWorkingSet = new LinkedList<>();        // remaining elements after this step of recursion
        remainingWorkingSet.addAll(workingSet);

        int index = 1;
        int bandOfChildren = computeBandOfTuple(workingSet.get(0));
        int bandOfParent = computeBandOfTuple(workingSet.get(index));
        currentWorkingSet.add(workingSet.get(0));
        remainingWorkingSet.removeFirst();

        // we are looking for the next tuple that have a greater band than the first element because that
        // element will be the limit for the first element to get merged into
        while (bandOfChildren == bandOfParent && workingSet.size() - 1 > index) {
            // the working set will be partitioned into a working set for the current step of recursion and
            // a partition that contains all elements that have to be processed in later steps
            currentWorkingSet.add(workingSet.get(index));
            remainingWorkingSet.remove(workingSet.get(index));

            index++;
            bandOfParent = computeBandOfTuple(workingSet.get(index));
        }
        Tuple parent = workingSet.get(index);

        // there is no real parent. all elements have the same band
        if (bandOfParent == bandOfChildren) {
            currentWorkingSet.add(parent);
            mergedWorkingSet.addAll(mergeSiblings(currentWorkingSet));
            return mergedWorkingSet;
        }

        int capacityOfParent = computeCapacityOfTuple(parent);

        // an element can be merged into it's parent if the resulting tuple isn't full (i.e. capacityOfParent > 1 after merging)
        while (capacityOfParent > currentWorkingSet.getLast().getOffset() && currentWorkingSet.size() > 1) {
            merge(currentWorkingSet.getLast(), parent);
            currentWorkingSet.removeLast();
            capacityOfParent = computeCapacityOfTuple(parent);
        }

        // checking whether all children were merged into parent or some were left over
        if (currentWorkingSet.isEmpty()) {
            mergedWorkingSet.addAll(mergeWorkingSet(remainingWorkingSet));
        }
        // if there are some children left, some of them can probably be merged into siblings.
        // if there is any child left over, parent can't be merged into any other tuple, so it must be removed
        // from the elements in the remaining working set.
        else {
            remainingWorkingSet.remove(parent);
            mergedWorkingSet.addAll(mergeSiblings(currentWorkingSet));
            mergedWorkingSet.add(parent);
            mergedWorkingSet.addAll(mergeWorkingSet(remainingWorkingSet));
        }

        return mergedWorkingSet;
    }

    /**
     * this method merges elements that have the same band
     *
     * @param workingSet - a {@link LinkedList} of {@link Tuple}
     * @return a {@link LinkedList} of {@link Tuple} with smallest possible size in respect to
     * GKs merging operation.
     */
    private LinkedList<Tuple> mergeSiblings(LinkedList<Tuple> workingSet) {
        // nothing left to merge
        if (workingSet.size() < 2) {
            return workingSet;
        }

        LinkedList<Tuple> mergedSiblings = new LinkedList<>();

        // it is only possible to merge an element into a sibling, if this sibling is the element's
        // direct neighbor to the right
        Tuple lastSibling = workingSet.getLast();
        workingSet.removeLast();
        boolean canStillMerge = true;

        // as long as the rightmost element can absorb elements, it will absorb his sibling to the left
        while (canStillMerge && !workingSet.isEmpty()) {
            if (this.areMergeable(workingSet.getLast(), lastSibling)) {
                merge(workingSet.getLast(), lastSibling);
                workingSet.removeLast();
            } else {
                canStillMerge = false;
            }
        }
        mergedSiblings.add(lastSibling);

        // recursion
        mergedSiblings.addAll(mergeSiblings(workingSet));

        return mergedSiblings;
    }

    /**
     * call this method to merge the element <code>left</code> into the element <code>right</code>.
     * Please note, that only elements with smaller value and a band not greater than <code>right
     * </code> can be element <code>left</code>.
     *
     * @param left  - element the will be deleted after merging
     * @param right - element that will contain the offset of element <code>left</code> after merging
     */
    private void merge(Tuple left, Tuple right) {
        right.setOffset(right.getOffset() + left.getOffset());
    }

    /**
     * The range of an element depends on range and offset of it's succeeding element.
     * This methods computes the current element's range.
     *
     * @return range of current element as {@link Integer} value
     */
    private Integer computeRangeForNewTuple(Tuple successor) {
        if (initialPhase) {
            return 0;
        }

        //this is how it's done during algorithm detail in the paper
        Double range = 2.0 * epsilon * count.doubleValue();
        range = Math.floor(range);

        //this is the more adequate version presented at section "empirical measurements"
        int successorRange = successor.getRange();
        int successorOffset = successor.getOffset();
        if (successorRange + successorOffset - 1 >= 0) {
            return (successorRange + successorOffset - 1);
        }

        return range.intValue();
    }

    /**
     * Partitions a list into {@link LinkedList}s of {@link Tuple}, so that bands of elements
     * in a single {@link LinkedList} are monotonically increasing.
     *
     * @return a {@link LinkedList} containing {@link LinkedList}s of {@link Double} which are
     * the partitions of {@link #summary}
     */
    private List<List<Tuple>> getPartitionsOfSummary() {
        List<List<Tuple>> partitions = new LinkedList<>();
        List<Tuple> workingSet = summary;

        // assuring that the minimum and maximum won't appear in a partition with other elements
        Tuple minimum = workingSet.get(0);
        Tuple maximum = workingSet.get(workingSet.size() - 1);
        workingSet.remove(0);

        if(!workingSet.isEmpty())
            workingSet.remove(workingSet.size() - 1);

        // adding the minimum as the first element into partitions
        LinkedList<Tuple> currentPartition = new LinkedList<>();
        currentPartition.add(minimum);
        partitions.add(currentPartition);
        currentPartition = new LinkedList<>();

        // nothing left to partitioning
        if (workingSet.size() < 2) {
            partitions.add(workingSet);
            // adding the maximum as the very last element into partitions
            currentPartition = new LinkedList<>();
            currentPartition.add(maximum);
            partitions.add(currentPartition);
            return partitions;
        }

        // we process the working set from the very last element to the very first one 
        while (workingSet.size() >= 2) {
            Tuple lastTuple = workingSet.get(workingSet.size() - 1);
            Tuple lastButOneTuple = workingSet.get(workingSet.size() - 2);
            currentPartition.addFirst(lastTuple);

            // every time we find an element whose band is greater than the current one the current partition
            // ended and we have to add a new partition to the resulting list
            if (isPartitionBorder(lastButOneTuple, lastTuple)) {
                partitions.add(currentPartition);
                currentPartition = new LinkedList<>();
            } else {
                // here got's the last element inserted into an partition
                if (workingSet.size() == 2) {
                    currentPartition.addFirst(lastButOneTuple);
                }
            }
            workingSet.remove(workingSet.size() - 1);
        }

        partitions.add(currentPartition);

        // adding the maximum as a partition of it's own at the very last position
        currentPartition = new LinkedList<>();
        currentPartition.add(maximum);
        partitions.add(currentPartition);

        return partitions;
    }

    /**
     * Call this method to get the current capacity of an element.
     *
     * @param tuple - a {@link Tuple}
     * @return {@link Integer} value representing the <code>tuple</code>'s capacity
     */
    private Integer computeCapacityOfTuple(Tuple tuple) {
        Integer offset = tuple.getOffset();
        Double currentMaxCapacity = Math.floor(2.0 * epsilon * count);
        return (currentMaxCapacity.intValue() - offset);
    }

    /**
     * A tuple's band depend on the number of seen elements (<code>count</code>) and the
     * tuple's range.
     * <ul>
     * <li> While GK hasn't finished it's initial phase, all elements have to be put into a
     * band of their own. This is done using a band -1.
     * <li> If count and range are logarithmically equal the tuple's band will be 0
     * <li> Else the tuple's band will be a value between 1 and <i>log(2*epsilon*count)</i>
     * </ul>
     * Please refer to the paper if you are interested in the formula for computing bands.
     *
     * @param tuple - a {@link Tuple}
     * @return {@link Integer} value specifying <code>tuple</code>'s band
     */
    private Integer computeBandOfTuple(Tuple tuple) {
        Double p = Math.floor(2 * epsilon * count);

        // this will be true for new tuples
        if (areLogarithmicallyEqual(p, tuple.getRange().doubleValue())) {
            return 0;
        }

        // initial phase
        if (tuple.getRange() == 0) {
            return -1;
        }

        double alpha = 0;
        double lowerBound;
        double upperBound;

        while (alpha < (Math.log(p) / Math.log(2))) {
            alpha++;
            lowerBound = p - Math.pow(2, alpha) - (p % Math.pow(2, alpha));

            if (lowerBound <= tuple.getRange()) {
                upperBound = p - Math.pow(2, alpha - 1) - (p % Math.pow(2, alpha - 1));

                if (upperBound >= tuple.getRange()) {
                    return (int) alpha;
                }
            }
        }

        return (int) alpha;
    }

    /**
     * Checks if two given values are logarithmically equal, i.e. the floored logarithm of
     * <code>valueOne</code> equals the floored logarithm of <code>valueTwo</code>.
     *
     * @param valueOne - a {@link Double} representing a {@link Tuple}s band
     * @param valueTwo - a {@link Double} representing a {@link Tuple}s band
     * @return <code>true</code> if both values are logarithmically equal
     */
    private boolean areLogarithmicallyEqual(Double valueOne, Double valueTwo) {
        return Math.floor(Math.log(valueOne)) == Math.floor(Math.log(valueTwo));
    }

    /**
     * To check whether a pair of elements are mergeable or not you should use this method. Its
     * decision takes into account the bands and values of the given elements.
     *
     * @param tuple  The element that will be deleted after merging.
     * @param parent The element that will absorb <code>tuple</code> during merge.
     * @return <code>true</code> if given elements are mergeable or <code>false</code> else.
     */
    private boolean areMergeable(Tuple tuple, Tuple parent) {
        int capacityOfParent = computeCapacityOfTuple(parent);

        // return true if parent's capacity suffices to absorb tuple and tuple's band isn't greater than parent's
        return capacityOfParent > tuple.getOffset() && computeBandOfTuple(parent) >= computeBandOfTuple(tuple);
    }

    /**
     * Bands of elements in a partition are monotonically increasing from the first to the last element.
     * So a partition border is found if a preceding element has a greater band than the current
     * element. This method checks this condition for given elements.
     *
     * @param left  preceding element.
     * @param right current element.
     * @return <code>true</code> if a partition boarder exists between the given elements or <code>
     * false</code> else.
     */
    private boolean isPartitionBorder(Tuple left, Tuple right) {
        return computeBandOfTuple(left) > computeBandOfTuple(right);
    }

    /**
     * Sorts a {@link LinkedList} of {@link Tuple}.
     *
     * @param workingSet - partitions of summary as a {@link LinkedList} of {@link Tuple}.
     * @return the given working set in ascending order.
     */
    private List<Tuple> sortWorkingSet(List<Tuple> workingSet) {
        List<Tuple> sortedWorkingSet = new CopyOnWriteArrayList<>();

        while (workingSet.size() > 1) {
            Tuple currentMinimum = workingSet.get(0);

            for (Tuple aWorkingSet : workingSet) {
                if (currentMinimum.getValue() > aWorkingSet.getValue()) {
                    currentMinimum = aWorkingSet;
                }
            }
            workingSet.remove(currentMinimum);
            sortedWorkingSet.add(currentMinimum);
        }

        sortedWorkingSet.add(workingSet.get(0));
        return sortedWorkingSet;
    }

    public Integer getCount() {
        return this.count;
    }


    @Override
    public String toString() {
        return getClass().getCanonicalName() + " { epsilon=" + epsilon + " }";
    }

    /**
     * This is just a wrapper class to hold all needed informations of an element. It contains the following
     * informations:
     * <ul>
     * <li><b>value</b>: the value of the element</li>
     * <li><b>offset</b>: the difference between the least rank of this element and the rank of the preceding
     * element.</li>
     * <li><b>range</b>: the span between this elements least and most rank</li>
     * <ul>
     */
    private class Tuple implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double value;
        private Integer offset;
        private Integer range;

        Tuple(Double value, Integer offset, Integer range) {
            this.value = value;
            this.offset = offset;
            this.range = range;
        }

        public Double getValue() {
            return value;
        }

        Integer getOffset() {
            return offset;
        }

        void setOffset(Integer offset) {
            this.offset = offset;
        }

        Integer getRange() {
            return range;
        }

        void setRange(Integer range) {
            this.range = range;
        }

        @Override
        public String toString() {
            return "( " + value + ", " + offset + ", " + range + " )";
        }
    }

    public static Builder quantiles(double... quantiles) {
        return new Builder().quantiles(quantiles);
    }

    public static class Builder {
        private Collection<Double> monitored = new ArrayList<>();
        private double error = 0.05;

        public Builder quantiles(double... quantiles) {
            for (double quantile : quantiles) {
                monitored.add(quantile);
            }
            return this;
        }

        public Builder error(double epsilon) {
            this.error = epsilon;
            return this;
        }

        public GKQuantiles create() {
            return new GKQuantiles(monitored, error);
        }
    }
}
