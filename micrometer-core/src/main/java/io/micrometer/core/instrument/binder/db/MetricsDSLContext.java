/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.db;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DefaultDSLContext;

import java.util.Arrays;
import java.util.Collection;

/**
 * Time SQL queries passing through jOOQ.
 *
 * Timing of batch operations and with statements not supported.
 * <p>
 * This can be used as the regular jOOQ {@link DSLContext} but queries will be timed and
 * tags can be set for the query timed. For example: <pre>
 * <code>
 *     MetricsDSLContext jooq = MetricsDSLContext.withMetrics(DSL.using(configuration), meterRegistry, Tags.empty());
 *     jooq.tag("name", "selectAllAuthors").select(asterisk()).from("author").fetch();
 * </code> </pre>
 *
 * This requires jOOQ 3.14.0 or later.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
public class MetricsDSLContext extends DefaultDSLContext {

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    private static final ThreadLocal<Iterable<Tag>> contextTags = new ThreadLocal<>();

    private static ExecuteListenerProvider defaultExecuteListenerProvider;

    public static MetricsDSLContext withMetrics(DSLContext jooq, MeterRegistry registry, Iterable<Tag> tags) {
        defaultExecuteListenerProvider = () -> new JooqExecuteListener(registry, tags, () -> {
            Iterable<Tag> queryTags = contextTags.get();
            contextTags.remove();
            return queryTags;
        });
        Configuration configuration = jooq.configuration().derive();
        Configuration derivedConfiguration = derive(configuration, defaultExecuteListenerProvider);
        return new MetricsDSLContext(derivedConfiguration, registry, tags);
    }

    MetricsDSLContext(Configuration configuration, MeterRegistry registry, Iterable<Tag> tags) {
        super(configuration);
        this.registry = registry;
        this.tags = tags;
    }

    public <Q extends Query> Q time(Q q) {
        q.attach(time(q.configuration()));
        return q;
    }

    public Configuration time(Configuration c) {
        Iterable<Tag> queryTags = contextTags.get();
        contextTags.remove();
        return derive(c, () -> new JooqExecuteListener(registry, tags, () -> queryTags));
    }

    private static Configuration derive(Configuration configuration, ExecuteListenerProvider executeListenerProvider) {
        ExecuteListenerProvider[] providers = configuration.executeListenerProviders();
        for (int i = 0; i < providers.length; i++) {
            if (providers[i] == defaultExecuteListenerProvider) {
                ExecuteListenerProvider[] newProviders = Arrays.copyOf(providers, providers.length);
                newProviders[i] = executeListenerProvider;
                return configuration.derive(newProviders);
            }
        }
        ExecuteListenerProvider[] newProviders = Arrays.copyOf(providers, providers.length + 1);
        newProviders[providers.length] = executeListenerProvider;
        return configuration.derive(newProviders);
    }

    @SuppressWarnings("unchecked")
    public <O> O timeCoercable(Object o) {
        return (O) time((Query) o);
    }

    public DSLContext tag(String key, String name) {
        return tags(Tags.of(key, name));
    }

    public DSLContext tag(Tag tag) {
        return tags(Tags.of(tag));
    }

    public DSLContext tags(Iterable<Tag> tags) {
        contextTags.set(tags);
        return this;
    }

    @Override
    public <R extends Record> SelectWhereStep<R> selectFrom(Table<R> table) {
        return time(super.selectFrom(table));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(Name table) {
        return time(super.selectFrom(table));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(SQL sql) {
        return time(super.selectFrom(sql));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(String sql) {
        return time(super.selectFrom(sql));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(String sql, Object... bindings) {
        return time(super.selectFrom(sql, bindings));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(String sql, QueryPart... parts) {
        return time(super.selectFrom(sql, parts));
    }

    @Override
    public SelectSelectStep<Record> select(Collection<? extends SelectFieldOrAsterisk> fields) {
        return time(super.select(fields));
    }

    @Override
    public SelectSelectStep<Record> select(SelectFieldOrAsterisk... fields) {
        return time(super.select(fields));
    }

    @Override
    public <T1> SelectSelectStep<Record1<T1>> select(SelectField<T1> field1) {
        return time(super.select(field1));
    }

    @Override
    public <T1, T2> SelectSelectStep<Record2<T1, T2>> select(SelectField<T1> field1, SelectField<T2> field2) {
        return time(super.select(field1, field2));
    }

    @Override
    public <T1, T2, T3> SelectSelectStep<Record3<T1, T2, T3>> select(SelectField<T1> field1, SelectField<T2> field2,
            SelectField<T3> field3) {
        return time(super.select(field1, field2, field3));
    }

    @Override
    public <T1, T2, T3, T4> SelectSelectStep<Record4<T1, T2, T3, T4>> select(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4) {
        return time(super.select(field1, field2, field3, field4));
    }

    @Override
    public <T1, T2, T3, T4, T5> SelectSelectStep<Record5<T1, T2, T3, T4, T5>> select(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5) {
        return time(super.select(field1, field2, field3, field4, field5));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> SelectSelectStep<Record6<T1, T2, T3, T4, T5, T6>> select(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5,
            SelectField<T6> field6) {
        return time(super.select(field1, field2, field3, field4, field5, field6));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> SelectSelectStep<Record7<T1, T2, T3, T4, T5, T6, T7>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> SelectSelectStep<Record8<T1, T2, T3, T4, T5, T6, T7, T8>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectSelectStep<Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectSelectStep<Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> SelectSelectStep<Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11) {
        return time(
                super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10, field11));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> SelectSelectStep<Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> SelectSelectStep<Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> SelectSelectStep<Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> SelectSelectStep<Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> SelectSelectStep<Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> SelectSelectStep<Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> SelectSelectStep<Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> SelectSelectStep<Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> SelectSelectStep<Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> SelectSelectStep<Record21<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20,
            SelectField<T21> field21) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> SelectSelectStep<Record22<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20,
            SelectField<T21> field21, SelectField<T22> field22) {
        return time(super.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21,
                field22));
    }

    @Override
    public SelectSelectStep<Record> selectDistinct(Collection<? extends SelectFieldOrAsterisk> fields) {
        return time(super.selectDistinct(fields));
    }

    @Override
    public SelectSelectStep<Record> selectDistinct(SelectFieldOrAsterisk... fields) {
        return time(super.selectDistinct(fields));
    }

    @Override
    public <T1> SelectSelectStep<Record1<T1>> selectDistinct(SelectField<T1> field1) {
        return time(super.selectDistinct(field1));
    }

    @Override
    public <T1, T2> SelectSelectStep<Record2<T1, T2>> selectDistinct(SelectField<T1> field1, SelectField<T2> field2) {
        return time(super.selectDistinct(field1, field2));
    }

    @Override
    public <T1, T2, T3> SelectSelectStep<Record3<T1, T2, T3>> selectDistinct(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3) {
        return time(super.selectDistinct(field1, field2, field3));
    }

    @Override
    public <T1, T2, T3, T4> SelectSelectStep<Record4<T1, T2, T3, T4>> selectDistinct(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4) {
        return time(super.selectDistinct(field1, field2, field3, field4));
    }

    @Override
    public <T1, T2, T3, T4, T5> SelectSelectStep<Record5<T1, T2, T3, T4, T5>> selectDistinct(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> SelectSelectStep<Record6<T1, T2, T3, T4, T5, T6>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> SelectSelectStep<Record7<T1, T2, T3, T4, T5, T6, T7>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> SelectSelectStep<Record8<T1, T2, T3, T4, T5, T6, T7, T8>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectSelectStep<Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectSelectStep<Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10) {
        return time(
                super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> SelectSelectStep<Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> SelectSelectStep<Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> SelectSelectStep<Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> SelectSelectStep<Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> SelectSelectStep<Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> SelectSelectStep<Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> SelectSelectStep<Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> SelectSelectStep<Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> SelectSelectStep<Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> SelectSelectStep<Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> SelectSelectStep<Record21<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20,
            SelectField<T21> field21) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20,
                field21));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> SelectSelectStep<Record22<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20,
            SelectField<T21> field21, SelectField<T22> field22) {
        return time(super.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20,
                field21, field22));
    }

    @Override
    public SelectSelectStep<Record1<Integer>> selectZero() {
        return time(super.selectZero());
    }

    @Override
    public SelectSelectStep<Record1<Integer>> selectOne() {
        return time(super.selectOne());
    }

    @Override
    public SelectSelectStep<Record1<Integer>> selectCount() {
        return time(super.selectCount());
    }

    @Override
    public SelectQuery<Record> selectQuery() {
        return time(super.selectQuery());
    }

    @Override
    public <R extends Record> SelectQuery<R> selectQuery(TableLike<R> table) {
        return time(super.selectQuery(table));
    }

    @Override
    public <R extends Record> InsertQuery<R> insertQuery(Table<R> into) {
        return time(super.insertQuery(into));
    }

    @Override
    public <R extends Record> InsertSetStep<R> insertInto(Table<R> into) {
        return timeCoercable(super.insertInto(into));
    }

    @Override
    public <R extends Record, T1> InsertValuesStep1<R, T1> insertInto(Table<R> into, Field<T1> field1) {
        return time(super.insertInto(into, field1));
    }

    @Override
    public <R extends Record, T1, T2> InsertValuesStep2<R, T1, T2> insertInto(Table<R> into, Field<T1> field1,
            Field<T2> field2) {
        return time(super.insertInto(into, field1, field2));
    }

    @Override
    public <R extends Record, T1, T2, T3> InsertValuesStep3<R, T1, T2, T3> insertInto(Table<R> into, Field<T1> field1,
            Field<T2> field2, Field<T3> field3) {
        return time(super.insertInto(into, field1, field2, field3));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4> InsertValuesStep4<R, T1, T2, T3, T4> insertInto(Table<R> into,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4) {
        return time(super.insertInto(into, field1, field2, field3, field4));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5> InsertValuesStep5<R, T1, T2, T3, T4, T5> insertInto(Table<R> into,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6> InsertValuesStep6<R, T1, T2, T3, T4, T5, T6> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7> InsertValuesStep7<R, T1, T2, T3, T4, T5, T6, T7> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8> InsertValuesStep8<R, T1, T2, T3, T4, T5, T6, T7, T8> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9> InsertValuesStep9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> InsertValuesStep10<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> InsertValuesStep11<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> InsertValuesStep12<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> InsertValuesStep13<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> InsertValuesStep14<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> InsertValuesStep15<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> InsertValuesStep16<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> InsertValuesStep17<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> InsertValuesStep18<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> InsertValuesStep19<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> InsertValuesStep20<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> InsertValuesStep21<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20,
            Field<T21> field21) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20,
                field21));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> InsertValuesStep22<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20,
            Field<T21> field21, Field<T22> field22) {
        return time(super.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20,
                field21, field22));
    }

    @Override
    public <R extends Record> InsertValuesStepN<R> insertInto(Table<R> into, Field<?>... fields) {
        return time(super.insertInto(into, fields));
    }

    @Override
    public <R extends Record> InsertValuesStepN<R> insertInto(Table<R> into, Collection<? extends Field<?>> fields) {
        return time(super.insertInto(into, fields));
    }

    @Override
    public <R extends Record> UpdateQuery<R> updateQuery(Table<R> table) {
        return time(super.updateQuery(table));
    }

    @Override
    public <R extends Record> UpdateSetFirstStep<R> update(Table<R> table) {
        return timeCoercable(super.update(table));
    }

}
