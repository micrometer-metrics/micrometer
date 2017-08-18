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

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Modified from: https://github.com/mayconbordin/streaminer#quantiles
 *
 * <p>
 * This class offers a easy to use sliding window implementation. The size of the window is fixed
 * and can't be modified after initializing the <code>SlidingWindow</code>. You can insert elements
 * of the same type of objects into the window. This objects may vary in size (e.g. if you want to
 * save space by adding only new elements to the window whose values vary at least a given
 * threshold, you would add some kind of summary for all the elements that can be covered using one
 * value. In this case the element's size would be total number of elements that fall into the
 * summary).<br>
 * Because of moving the borders is left to the user this implementation can be used for managing
 * windows that age per time unit as well as per seen element.
 *
 * @param <T> any kind of object
 * @author Markus Kokott
 * @author Jon Schneider
 */
public class SlidingWindow<T> implements Serializable {
    private static final long serialVersionUID = 5971578246884329784L;
    private LinkedList<Element<T>> elements;
    private Integer windowSize;

    /**
     * The window size is fixed after initiating an instance of <code>SlidingWindow</code>
     *
     * @param windowSize a (positive) {@link Integer} value.
     */
    public SlidingWindow(Integer windowSize) {
        if (windowSize < 0) {
            windowSize *= -1;
        }
        this.windowSize = windowSize;
        this.elements = new LinkedList<>();
    }

    /**
     * Adds a new element to the window. The size of this elements is the default size (i.e. 1).
     * <br>
     * Please note, that no older elements will be discarded until you slide the window manually!
     *
     * @param element
     */
    public void add(T element) {
        Element<T> newElement = new Element<>(element, this.windowSize, 0);
        this.elements.addFirst(newElement);
    }

    /**
     * Adds a new element to the window. The size of this element is specified by the user.<br>
     * Please note, that no older elements will be discarded until you slide the window manually!
     *
     * @param element
     * @param size    - {@link Integer} value that specifies the <code>element</code>s size
     * @throws RuntimeException if <code>size</code> is greater than {@link #windowSize} or a negative
     *                          {@link Integer}
     */
    public void add(T element, Integer size) {

        if (size > this.windowSize) {
            throw new RuntimeException("Size of element exceeds the size of the sliding window.");
        }
        if (size < 0) {
            throw new RuntimeException("The size of an element can't be a negative integer.");
        }

        Element<T> newElement = new Element<>(element, this.windowSize - size, size);
        this.elements.addFirst(newElement);
    }

    /**
     * Just moves the border of the window by one position.
     */
    public void slideWindowByOnePosition() {

        this.refreshWindow();
        this.shiftElementsByOne();
    }

    /**
     * Moves the window a given number of positions.
     *
     * @param positions - a {@link Integer} value representing the elapsed "time"
     * @throws RuntimeException if <code>positions</code> is a negative value
     */
    public void slideWindow(Integer positions) {

        if (positions < 0) {
            throw new RuntimeException("You can't go back in time...");
        }

        this.refreshWindow();
        this.shiftElements(positions);
    }

    /**
     * Returns the element at position <code>index</code>
     *
     * @param index
     * @return element of type <b>T</b>
     */
    public T get(Integer index) {
        return this.elements.get(index).getElement();
    }

    /**
     * Returns the oldest element in the window.
     *
     * @return element of type <b>T</b>
     */
    public T getOldestElement() {

        if (this.elements.isEmpty()) {
            return null;
        }
        return this.elements.getLast().getElement();
    }

    /**
     * Returns the newest element in the window.
     *
     * @return element of type <b>T</b>
     */
    public T getNewestElement() {

        if (this.elements.isEmpty()) {
            return null;
        }
        return this.elements.getFirst().getElement();
    }

    /**
     * Returns a collection containing each element in the window
     *
     * @return a {@link Collection} of element of type <b>T</b>
     */
    public Collection<T> getAll() {
        Collection<T> elements = new LinkedList<>();

        for (int i = 0; i < this.elements.size(); i++) {
            elements.add(this.elements.get(i).getElement());
        }

        return elements;
    }

    /**
     * Returns a collection containing all elements' life times.
     *
     * @return a {@link Collection} of element of type {@link Integer}
     */
    public Collection<Integer> getAllLifeTimes() {
        Collection<Integer> lifeTimes = new LinkedList<>();

        for (int i = 0; i < this.elements.size(); i++) {
            lifeTimes.add(this.elements.get(i).getTimeToLive());
        }

        return lifeTimes;
    }

    /**
     * Returns the remaining life time of the specified element.
     *
     * @param index
     * @return the life time of an element
     */
    public Integer getLifeTime(Integer index) {
        return this.elements.get(index).getTimeToLive();
    }

    /**
     * Returns the size of this sliding window.
     *
     * @return an {@link Integer} that stands for the size of the window
     */
    public Integer getWindowSize() {
        return this.windowSize;
    }

    /**
     * Returns the element's size at position <code>index</code>
     *
     * @param index
     * @return {@link Integer} value
     */
    public Integer getSize(Integer index) {
        return this.elements.get(index).getSize();
    }

    /**
     * Checks whether the <code>SlidingWindow</code> is empty
     *
     * @return <code>true</code> if the <code>SlindingWindow</code> contains any element or
     * <code>false</code> if at least one element in the window is active.
     */
    public boolean isEmpty() {
        return this.elements.isEmpty();
    }

    /**
     * Removes elements, that crossed the border and therefore are out dated.
     */
    private void refreshWindow() {

        while (!this.elements.isEmpty() && this.elements.getLast().getTimeToLive() < 0) {
            elements.removeLast();
        }
    }

    /**
     * Internally called to decrease the elements' life time by one
     */
    private void shiftElementsByOne() {

        for (int i = 0; i < this.elements.size(); i++) {
            this.elements.get(i).decrementTimeToLiveByOne();
        }
    }

    /**
     * Internally called to decrease the elements' life time by a given number.
     *
     * @param positions
     */
    private void shiftElements(Integer positions) {

        for (int i = 0; i < this.elements.size(); i++) {
            this.elements.get(i).decrementTimeToLive(positions);
        }
    }

    /**
     * Wrapper class that contains the elements and their life times.
     *
     * @param <E>
     */

    private class Element<E> implements Serializable {
        /**
         *
         */
        private static final long serialVersionUID = 3908165358308721662L;
        private Integer timeToLive;
        private Integer size;
        private E element;

        Element(E element, Integer timeToLive, Integer size) {
            this.element = element;
            this.timeToLive = timeToLive;
            this.size = size;
        }

        void decrementTimeToLive(Integer amount) {
            this.timeToLive -= amount;
        }

        void decrementTimeToLiveByOne() {
            this.timeToLive--;
        }

        Integer getTimeToLive() {
            return this.timeToLive;
        }

        E getElement() {
            return this.element;
        }

        public Integer getSize() {
            return size;
        }

        @Override
        public String toString() {
            return "[ TTL: " + this.timeToLive + ", size: " + this.size + ", element: " + this.element + " ]";
        }
    }
}
