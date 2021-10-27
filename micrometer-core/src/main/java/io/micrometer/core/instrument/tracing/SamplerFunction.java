/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.tracing;

import io.micrometer.core.lang.Nullable;

/**
 * This API was heavily influenced by Brave. Parts of its documentation were taken
 * directly from Brave.
 *
 * Decides whether to start a new trace based on request properties such as an HTTP path.
 *
 * @param <T> type of the input, for example a request or method
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
@SuppressWarnings("unchecked")
public interface SamplerFunction<T> {

	/**
	 * Always deferring {@link SamplerFunction}.
	 *
	 * @param <T> type of the input, for example a request or method
	 * @return decision deferring sampler function
	 */
	static <T> SamplerFunction<T> deferDecision() {
		return (SamplerFunction<T>) Constants.DEFER_DECISION;
	}

	/**
	 * Never sampling {@link SamplerFunction}.
	 *
	 * @param <T> type of the input, for example a request or method
	 * @return never sampling sampler function
	 */
	static <T> SamplerFunction<T> neverSample() {
		return (SamplerFunction<T>) Constants.NEVER_SAMPLE;
	}

	/**
	 * Always sampling {@link SamplerFunction}.
	 *
	 * @param <T> type of the input, for example a request or method
	 * @return always sampling sampler function
	 */
	static <T> SamplerFunction<T> alwaysSample() {
		return (SamplerFunction<T>) Constants.ALWAYS_SAMPLE;
	}

	/**
	 * Returns an overriding sampling decision for a new trace.
	 *
	 * @param arg parameter to evaluate for a sampling decision. {@code null} input
	 * results in a {@code null} result
	 * @return {@code true} to sample a new trace or {@code false} to deny. {@code null}
	 * defers the decision
	 */
	@Nullable
	Boolean trySample(@Nullable T arg);

	/**
	 * Constant {@link SamplerFunction}.
	 */
	enum Constants implements SamplerFunction<Object> {

		/**
		 * Always defers sampling decision.
		 */
		DEFER_DECISION {
			@Override
			public Boolean trySample(Object request) {
				return null;
			}

			@Override
			public String toString() {
				return "DeferDecision";
			}
		},

		/**
		 * Will never sample this trace.
		 */
		NEVER_SAMPLE {
			@Override
			public Boolean trySample(Object request) {
				return false;
			}

			@Override
			public String toString() {
				return "NeverSample";
			}
		},

		/**
		 * Will always sample this trace.
		 */
		ALWAYS_SAMPLE {
			@Override
			public Boolean trySample(Object request) {
				return true;
			}

			@Override
			public String toString() {
				return "AlwaysSample";
			}
		}

	}

}
