/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.http.HttpClientRequest;
import io.micrometer.observation.transport.http.HttpClientResponse;
import io.micrometer.observation.transport.http.tags.HttpClientKeyValuesConvention;
import okhttp3.EventListener;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;

/**
 * {@link EventListener} for collecting metrics from {@link OkHttpClient}.
 * <p>
 * {@literal uri} tag is usually limited to URI patterns to mitigate tag cardinality
 * explosion but {@link OkHttpClient} doesn't provide URI patterns. We provide
 * {@value OkHttpMetricsEventListener#URI_PATTERN} header to support {@literal uri} tag or
 * you can configure a {@link Builder#uriMapper(Function) URI mapper} to provide your own
 * tag values for {@literal uri} tag.
 *
 * @author Bjarte S. Karlsen
 * @author Jon Schneider
 * @author Nurettin Yilmaz
 * @author Johnny Lim
 */
@NonNullApi
@NonNullFields
public class OkHttpMetricsEventListener extends EventListener {

    /**
     * Header name for URI patterns which will be used for tag values.
     */
    public static final String URI_PATTERN = "URI_PATTERN";

    private final MeterRegistry registry;

    private final ObservationRegistry observationRegistry;

    private final Observation.KeyValuesProvider<OkHttpContext> keyValuesProvider;

    private final String requestsMetricName;

    private final Function<Request, String> urlMapper;

    private final Iterable<Tag> extraTags;

    private final Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags;

    private final Iterable<Tag> unknownRequestTags;

    private final boolean includeHostTag;

    private final boolean observationRegistryNoOp;

    // VisibleForTesting
    final ConcurrentMap<Call, CallState> callState = new ConcurrentHashMap<>();

    protected OkHttpMetricsEventListener(MeterRegistry registry, String requestsMetricName,
            Function<Request, String> urlMapper, Iterable<Tag> extraTags,
            Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags) {
        this(registry, ObservationRegistry.NOOP, new DefaultOkHttpKeyValuesProvider(), requestsMetricName, urlMapper, extraTags, contextSpecificTags, emptyList(), true);
    }

    OkHttpMetricsEventListener(MeterRegistry registry, ObservationRegistry observationRegistry, Observation.KeyValuesProvider<OkHttpContext> keyValuesProvider, String requestsMetricName, Function<Request, String> urlMapper,
            Iterable<Tag> extraTags, Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags,
            Iterable<String> requestTagKeys, boolean includeHostTag) {
        this.registry = registry;
        this.observationRegistry = observationRegistry;
        this.observationRegistryNoOp = observationRegistry.isNoOp();
        this.keyValuesProvider = keyValuesProvider;
        this.requestsMetricName = requestsMetricName;
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.includeHostTag = includeHostTag;

        List<Tag> unknownRequestTags = new ArrayList<>();
        for (String requestTagKey : requestTagKeys) {
            unknownRequestTags.add(Tag.of(requestTagKey, "UNKNOWN"));
        }
        this.unknownRequestTags = unknownRequestTags;
    }

    public static Builder builder(MeterRegistry registry, String name) {
        return new Builder(registry, name);
    }

    @Override
    public void callStart(Call call) {
        CallState callState = new CallState(registry.config().clock().monotonicTime(), call.request());
        OkHttpContext okHttpContext = new OkHttpContext(callState, urlMapper, extraTags, contextSpecificTags, unknownRequestTags, includeHostTag);
        if (callState.request != null) {
            okHttpContext.setRequest(new HttpClientRequest() {

                Set<KeyValue> headers = StreamSupport.stream(callState.request.headers().spliterator(), false)
                        .map(pair -> KeyValue.of(pair.getFirst(), pair.getSecond()))
                        .collect(Collectors.toCollection(HashSet::new));

                @Override
                public void header(String name, String value) {
                    headers.add(KeyValue.of(name, value));
                }

                @Override
                public String method() {
                    return callState.request.method();
                }

                @Override
                public String path() {
                    return callState.request.url().pathSegments().get(0);
                }

                @Override
                public String url() {
                    return callState.request.url().toString();
                }

                @Override
                public String header(String name) {
                    return headers.stream().filter(keyValue -> keyValue.getKey().equals(name)).findFirst().map(KeyValue::getValue).orElse(null);
                }

                @Override
                public Collection<String> headerNames() {
                    return headers.stream().map(KeyValue::getKey).collect(Collectors.toList());
                }

                @Override
                public Object unwrap() {
                    return callState.request;
                }
            });
        }
        Observation observation = Observation.createNotStarted(this.requestsMetricName, okHttpContext, this.observationRegistry)
                .keyValuesProvider(this.keyValuesProvider)
                .start();
        callState.setContext(okHttpContext);
        callState.setObservation(observation);
        this.callState.put(call, callState);
    }

    @Override
    public void callFailed(Call call, IOException e) {
        CallState state = callState.remove(call);
        if (state != null) {
            state.exception = e;
            time(state);
        }
    }

    @Override
    public void callEnd(Call call) {
        callState.remove(call);
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        CallState state = callState.remove(call);
        if (state != null) {
            state.response = response;
            time(state);
        }
    }

    // VisibleForTesting
    void time(CallState state) {
        OkHttpContext okHttpContext = state.context;
        if (observationRegistry.isNoOp()) {
            // TODO: We're going first from tags to key values and then back - maybe it doesn't make a lot of sense?
            KeyValues lowCardinalityKeyValues = keyValuesProvider.getLowCardinalityKeyValues(okHttpContext);
            Timer.builder(this.requestsMetricName).tags(lowCardinalityKeyValues.stream().map(keyValue -> Tag.of(keyValue.getKey(), keyValue.getValue())).collect(Collectors.toList())).description("Timer of OkHttp operation").register(registry)
                    .record(registry.config().clock().monotonicTime() - state.startTime, TimeUnit.NANOSECONDS);
        } else {
            state.observation.error(state.exception);
            if (state.response != null) {
                okHttpContext.setResponse(new HttpClientResponse() {
                    @Override
                    public int statusCode() {
                        return state.response.code();
                    }

                    @Override
                    public Collection<String> headerNames() {
                        return state.response.headers().names();
                    }

                    @Override
                    public Object unwrap() {
                        return state.response;
                    }
                });
            }
            state.observation.stop();
        }
    }

    // VisibleForTesting
    static class CallState {

        final long startTime;

        Observation observation;

        @Nullable
        final Request request;

        OkHttpContext context;

        @Nullable
        Response response;

        @Nullable
        IOException exception;

        CallState(long startTime, @Nullable Request request) {
            this.startTime = startTime;
            this.request = request;
        }

        void setContext(OkHttpContext context) {
            this.context = context;
        }

        void setObservation(Observation observation) {
            this.observation = observation;
        }
    }

    public static class Builder {

        private final MeterRegistry registry;

        private final String name;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private Function<Request, String> uriMapper = (request) -> Optional.ofNullable(request.header(URI_PATTERN))
                .orElse("none");

        private Tags tags = Tags.empty();

        private Collection<BiFunction<Request, Response, Tag>> contextSpecificTags = new ArrayList<>();

        private boolean includeHostTag = true;

        private Iterable<String> requestTagKeys = Collections.emptyList();

        private OkHttpKeyValuesProvider keyValuesProvider;

        Builder(MeterRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
        }

        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        public Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        public Builder keyValuesProvider(OkHttpKeyValuesProvider keyValuesProvider) {
            this.keyValuesProvider = keyValuesProvider;
            return this;
        }

        /**
         * Add a {@link Tag} to any already configured tags on this Builder.
         * @param tag tag to add
         * @return this builder
         * @since 1.5.0
         */
        public Builder tag(Tag tag) {
            this.tags = this.tags.and(tag);
            return this;
        }

        /**
         * Add a context-specific tag.
         * @param contextSpecificTag function to create a context-specific tag
         * @return this builder
         * @since 1.5.0
         */
        public Builder tag(BiFunction<Request, Response, Tag> contextSpecificTag) {
            this.contextSpecificTags.add(contextSpecificTag);
            return this;
        }

        public Builder uriMapper(Function<Request, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        /**
         * Historically, OkHttp Metrics provided by {@link OkHttpMetricsEventListener}
         * included a {@code host} tag for the target host being called. To align with
         * other HTTP client metrics, this was changed to {@code target.host}, but to
         * maintain backwards compatibility the {@code host} tag can also be included. By
         * default, {@code includeHostTag} is {@literal true} so both tags are included.
         * @param includeHostTag whether to include the {@code host} tag
         * @return this builder
         * @since 1.5.0
         */
        public Builder includeHostTag(boolean includeHostTag) {
            this.includeHostTag = includeHostTag;
            return this;
        }

        /**
         * Tag keys for {@link Request#tag()} or {@link Request#tag(Class)}.
         *
         * These keys will be added with {@literal UNKNOWN} values when {@link Request} is
         * {@literal null}. Note that this is required only for Prometheus as it requires
         * tag match for the same metric.
         * @param requestTagKeys request tag keys
         * @return this builder
         * @since 1.3.9
         */
        public Builder requestTagKeys(String... requestTagKeys) {
            return requestTagKeys(Arrays.asList(requestTagKeys));
        }

        /**
         * Tag keys for {@link Request#tag()} or {@link Request#tag(Class)}.
         *
         * These keys will be added with {@literal UNKNOWN} values when {@link Request} is
         * {@literal null}. Note that this is required only for Prometheus as it requires
         * tag match for the same metric.
         * @param requestTagKeys request tag keys
         * @return this builder
         * @since 1.3.9
         */
        public Builder requestTagKeys(Iterable<String> requestTagKeys) {
            this.requestTagKeys = requestTagKeys;
            return this;
        }

        @SuppressWarnings("unchecked")
        public OkHttpMetricsEventListener build() {
            Observation.KeyValuesProvider provider = null;
            if (this.keyValuesProvider != null) {
                provider = this.keyValuesProvider;
            }
            else if (observationRegistry.isNoOp() || observationRegistry.observationConfig().getKeyValuesConfiguration() == ObservationRegistry.KeyValuesConfiguration.LEGACY) {
                provider = new DefaultOkHttpKeyValuesProvider();
            }
            else if (observationRegistry.observationConfig().getKeyValuesConfiguration() == ObservationRegistry.KeyValuesConfiguration.STANDARDIZED) {
                // TODO: Isn't this too much - maybe we should just require the user to set this manually?
                provider = new StandardizedOkHttpKeyValuesProvider(observationRegistry.observationConfig().getKeyValuesConvention(HttpClientKeyValuesConvention.class));
            }
            else {
                provider = new Observation.KeyValuesProvider.CompositeKeyValuesProvider(new DefaultOkHttpKeyValuesProvider(), new StandardizedOkHttpKeyValuesProvider(observationRegistry.observationConfig().getKeyValuesConvention(HttpClientKeyValuesConvention.class)));
            }

            return new OkHttpMetricsEventListener(registry, observationRegistry, provider, name, uriMapper, tags, contextSpecificTags, requestTagKeys,
                    includeHostTag);
        }

    }

}
