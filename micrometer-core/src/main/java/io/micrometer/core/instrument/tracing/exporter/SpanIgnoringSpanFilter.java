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

package io.micrometer.core.instrument.tracing.exporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.micrometer.core.instrument.util.StringUtils;

/**
 * {@link SpanFilter} that ignores spans via names.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class SpanIgnoringSpanFilter implements SpanFilter {

	static final Map<String, Pattern> cache = new ConcurrentHashMap<>();

    private static final Log log = LogFactory.getLog(SpanIgnoringSpanFilter.class);

	private final List<String> spanNamePatternsToSkip;

	private final List<String> additionalSpanNamePatternsToIgnore;

	/**
	 * Creates a new instance of {@link SpanIgnoringSpanFilter}.
	 *
	 * @param spanNamePatternsToSkip default span name patterns to skip
	 * @param additionalSpanNamePatternsToIgnore additional span name patterns to ignore
	 */
	public SpanIgnoringSpanFilter(List<String> spanNamePatternsToSkip,
			List<String> additionalSpanNamePatternsToIgnore) {
		this.spanNamePatternsToSkip = spanNamePatternsToSkip;
		this.additionalSpanNamePatternsToIgnore = additionalSpanNamePatternsToIgnore;
	}

	private List<Pattern> spanNamesToIgnore() {
		return spanNames().stream().map(regex -> cache.computeIfAbsent(regex, Pattern::compile))
				.collect(Collectors.toList());
	}

	private List<String> spanNames() {
		List<String> spanNamesToIgnore = new ArrayList<>(this.spanNamePatternsToSkip);
		spanNamesToIgnore.addAll(this.additionalSpanNamePatternsToIgnore);
		return spanNamesToIgnore;
	}

	@Override
	public boolean isExportable(FinishedSpan span) {
		List<Pattern> spanNamesToIgnore = spanNamesToIgnore();
		String name = span.getName();
        if (StringUtils.isNotBlank(name) && spanNamesToIgnore.stream().anyMatch(p -> p.matcher(name).matches())) {
            if (log.isDebugEnabled()) {
                log.debug("Will ignore a span with name [" + name + "]");
            }
			return false;
		}
		return true;
	}

}
