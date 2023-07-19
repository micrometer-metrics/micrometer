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
import org.jooq.conf.Settings;
import org.jooq.exception.*;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockCallable;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockRunnable;
import org.jooq.util.xml.jaxb.InformationSchema;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

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
public class MetricsDSLContext implements DSLContext {

    private final DSLContext context;

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    private final ThreadLocal<Iterable<Tag>> contextTags = new ThreadLocal<>();

    private final ExecuteListenerProvider defaultExecuteListenerProvider;

    public static MetricsDSLContext withMetrics(DSLContext jooq, MeterRegistry registry, Iterable<Tag> tags) {
        return new MetricsDSLContext(jooq, registry, tags);
    }

    MetricsDSLContext(DSLContext context, MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;

        this.defaultExecuteListenerProvider = () -> new JooqExecuteListener(registry, tags, () -> {
            Iterable<Tag> queryTags = contextTags.get();
            contextTags.remove();
            return queryTags;
        });
        Configuration configuration = context.configuration().derive();
        Configuration derivedConfiguration = derive(configuration, this.defaultExecuteListenerProvider);

        this.context = DSL.using(derivedConfiguration);
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

    private Configuration derive(Configuration configuration, ExecuteListenerProvider executeListenerProvider) {
        ExecuteListenerProvider[] providers = configuration.executeListenerProviders();
        for (int i = 0; i < providers.length; i++) {
            if (providers[i] == this.defaultExecuteListenerProvider) {
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
    public Schema map(Schema schema) {
        return context.map(schema);
    }

    @Override
    public <R extends Record> Table<R> map(Table<R> table) {
        return context.map(table);
    }

    @Override
    public Parser parser() {
        return context.parser();
    }

    @Override
    public Connection parsingConnection() {
        return context.parsingConnection();
    }

    @Override
    public DataSource parsingDataSource() {
        return context.parsingDataSource();
    }

    @Override
    public Connection diagnosticsConnection() {
        return context.diagnosticsConnection();
    }

    @Override
    public DataSource diagnosticsDataSource() {
        return context.diagnosticsDataSource();
    }

    @Override
    public Version version(String id) {
        return context.version(id);
    }

    @Override
    public Migration migrateTo(Version to) {
        return context.migrateTo(to);
    }

    @Override
    public Meta meta() {
        return context.meta();
    }

    @Override
    public Meta meta(DatabaseMetaData meta) {
        return context.meta(meta);
    }

    @Override
    public Meta meta(Catalog... catalogs) {
        return context.meta(catalogs);
    }

    @Override
    public Meta meta(Schema... schemas) {
        return context.meta(schemas);
    }

    @Override
    public Meta meta(Table<?>... tables) {
        return context.meta(tables);
    }

    @Override
    public Meta meta(InformationSchema schema) {
        return context.meta(schema);
    }

    @Override
    public Meta meta(String... sources) {
        return context.meta(sources);
    }

    @Override
    @Internal
    public Meta meta(Source... scripts) {
        return context.meta(scripts);
    }

    @Override
    public Meta meta(Query... queries) {
        return context.meta(queries);
    }

    @Override
    public InformationSchema informationSchema(Catalog catalog) {
        return context.informationSchema(catalog);
    }

    @Override
    public InformationSchema informationSchema(Catalog... catalogs) {
        return context.informationSchema(catalogs);
    }

    @Override
    public InformationSchema informationSchema(Schema schema) {
        return context.informationSchema(schema);
    }

    @Override
    public InformationSchema informationSchema(Schema... schemas) {
        return context.informationSchema(schemas);
    }

    @Override
    public InformationSchema informationSchema(Table<?> table) {
        return context.informationSchema(table);
    }

    @Override
    public InformationSchema informationSchema(Table<?>... table) {
        return context.informationSchema(table);
    }

    @Override
    public Explain explain(Query query) {
        return context.explain(query);
    }

    @Override
    public <T> T transactionResult(TransactionalCallable<T> transactional) {
        return context.transactionResult(transactional);
    }

    @Override
    public <T> T transactionResult(ContextTransactionalCallable<T> transactional) throws ConfigurationException {
        return context.transactionResult(transactional);
    }

    @Override
    public void transaction(TransactionalRunnable transactional) {
        context.transaction(transactional);
    }

    @Override
    public void transaction(ContextTransactionalRunnable transactional) throws ConfigurationException {
        context.transaction(transactional);
    }

    @Override
    public <T> CompletionStage<T> transactionResultAsync(TransactionalCallable<T> transactional)
            throws ConfigurationException {
        return context.transactionResultAsync(transactional);
    }

    @Override
    public CompletionStage<Void> transactionAsync(TransactionalRunnable transactional) throws ConfigurationException {
        return context.transactionAsync(transactional);
    }

    @Override
    public <T> CompletionStage<T> transactionResultAsync(Executor executor, TransactionalCallable<T> transactional)
            throws ConfigurationException {
        return context.transactionResultAsync(executor, transactional);
    }

    @Override
    public CompletionStage<Void> transactionAsync(Executor executor, TransactionalRunnable transactional)
            throws ConfigurationException {
        return context.transactionAsync(executor, transactional);
    }

    @Override
    public <T> T connectionResult(ConnectionCallable<T> callable) {
        return context.connectionResult(callable);
    }

    @Override
    public void connection(ConnectionRunnable runnable) {
        context.connection(runnable);
    }

    @Override
    public <T> T mockResult(MockDataProvider provider, MockCallable<T> mockable) {
        return context.mockResult(provider, mockable);
    }

    @Override
    public void mock(MockDataProvider provider, MockRunnable mockable) {
        context.mock(provider, mockable);
    }

    @Override
    @Internal
    @Deprecated
    public RenderContext renderContext() {
        return context.renderContext();
    }

    @Override
    public String render(QueryPart part) {
        return context.render(part);
    }

    @Override
    public String renderNamedParams(QueryPart part) {
        return context.renderNamedParams(part);
    }

    @Override
    public String renderNamedOrInlinedParams(QueryPart part) {
        return context.renderNamedOrInlinedParams(part);
    }

    @Override
    public String renderInlined(QueryPart part) {
        return context.renderInlined(part);
    }

    @Override
    public List<Object> extractBindValues(QueryPart part) {
        return context.extractBindValues(part);
    }

    @Override
    public Map<String, Param<?>> extractParams(QueryPart part) {
        return context.extractParams(part);
    }

    @Override
    public Param<?> extractParam(QueryPart part, String name) {
        return context.extractParam(part, name);
    }

    @Override
    @Internal
    @Deprecated
    public BindContext bindContext(PreparedStatement stmt) {
        return context.bindContext(stmt);
    }

    @Override
    @Deprecated
    public int bind(QueryPart part, PreparedStatement stmt) {
        return context.bind(part, stmt);
    }

    @Override
    public void attach(Attachable... attachables) {
        context.attach(attachables);
    }

    @Override
    public void attach(Collection<? extends Attachable> attachables) {
        context.attach(attachables);
    }

    @Override
    public <R extends Record> LoaderOptionsStep<R> loadInto(Table<R> table) {
        return context.loadInto(table);
    }

    @Override
    public Queries queries(Query... queries) {
        return context.queries(queries);
    }

    @Override
    public Queries queries(Collection<? extends Query> queries) {
        return context.queries(queries);
    }

    @Override
    public Block begin(Statement... statements) {
        return context.begin(statements);
    }

    @Override
    public Block begin(Collection<? extends Statement> statements) {
        return context.begin(statements);
    }

    @Override
    public RowCountQuery query(SQL sql) {
        return context.query(sql);
    }

    @Override
    public RowCountQuery query(String sql) {
        return context.query(sql);
    }

    @Override
    public RowCountQuery query(String sql, Object... bindings) {
        return context.query(sql, bindings);
    }

    @Override
    public RowCountQuery query(String sql, QueryPart... parts) {
        return context.query(sql, parts);
    }

    @Override
    public Result<Record> fetch(SQL sql) throws DataAccessException {
        return context.fetch(sql);
    }

    @Override
    public Result<Record> fetch(String sql) throws DataAccessException {
        return context.fetch(sql);
    }

    @Override
    public Result<Record> fetch(String sql, Object... bindings) throws DataAccessException {
        return context.fetch(sql, bindings);
    }

    @Override
    public Result<Record> fetch(String sql, QueryPart... parts) throws DataAccessException {
        return context.fetch(sql, parts);
    }

    @Override
    public Cursor<Record> fetchLazy(SQL sql) throws DataAccessException {
        return context.fetchLazy(sql);
    }

    @Override
    public Cursor<Record> fetchLazy(String sql) throws DataAccessException {
        return context.fetchLazy(sql);
    }

    @Override
    public Cursor<Record> fetchLazy(String sql, Object... bindings) throws DataAccessException {
        return context.fetchLazy(sql, bindings);
    }

    @Override
    public Cursor<Record> fetchLazy(String sql, QueryPart... parts) throws DataAccessException {
        return context.fetchLazy(sql, parts);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(SQL sql) {
        return context.fetchAsync(sql);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(String sql) {
        return context.fetchAsync(sql);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(String sql, Object... bindings) {
        return context.fetchAsync(sql, bindings);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(String sql, QueryPart... parts) {
        return context.fetchAsync(sql, parts);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, SQL sql) {
        return context.fetchAsync(executor, sql);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, String sql) {
        return context.fetchAsync(executor, sql);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, String sql, Object... bindings) {
        return context.fetchAsync(executor, sql, bindings);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, String sql, QueryPart... parts) {
        return context.fetchAsync(executor, sql, parts);
    }

    @Override
    public Stream<Record> fetchStream(SQL sql) throws DataAccessException {
        return context.fetchStream(sql);
    }

    @Override
    public Stream<Record> fetchStream(String sql) throws DataAccessException {
        return context.fetchStream(sql);
    }

    @Override
    public Stream<Record> fetchStream(String sql, Object... bindings) throws DataAccessException {
        return context.fetchStream(sql, bindings);
    }

    @Override
    public Stream<Record> fetchStream(String sql, QueryPart... parts) throws DataAccessException {
        return context.fetchStream(sql, parts);
    }

    @Override
    public Results fetchMany(SQL sql) throws DataAccessException {
        return context.fetchMany(sql);
    }

    @Override
    public Results fetchMany(String sql) throws DataAccessException {
        return context.fetchMany(sql);
    }

    @Override
    public Results fetchMany(String sql, Object... bindings) throws DataAccessException {
        return context.fetchMany(sql, bindings);
    }

    @Override
    public Results fetchMany(String sql, QueryPart... parts) throws DataAccessException {
        return context.fetchMany(sql, parts);
    }

    @Override
    public Record fetchOne(SQL sql) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(sql);
    }

    @Override
    public Record fetchOne(String sql) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(sql);
    }

    @Override
    public Record fetchOne(String sql, Object... bindings) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(sql, bindings);
    }

    @Override
    public Record fetchOne(String sql, QueryPart... parts) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(sql, parts);
    }

    @Override
    public Record fetchSingle(SQL sql) throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(sql);
    }

    @Override
    public Record fetchSingle(String sql) throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(sql);
    }

    @Override
    public Record fetchSingle(String sql, Object... bindings)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(sql, bindings);
    }

    @Override
    public Record fetchSingle(String sql, QueryPart... parts)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(sql, parts);
    }

    @Override
    public Optional<Record> fetchOptional(SQL sql) throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(sql);
    }

    @Override
    public Optional<Record> fetchOptional(String sql) throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(sql);
    }

    @Override
    public Optional<Record> fetchOptional(String sql, Object... bindings)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(sql, bindings);
    }

    @Override
    public Optional<Record> fetchOptional(String sql, QueryPart... parts)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(sql, parts);
    }

    @Override
    public Object fetchValue(SQL sql) throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(sql);
    }

    @Override
    public Object fetchValue(String sql) throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(sql);
    }

    @Override
    public Object fetchValue(String sql, Object... bindings)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(sql, bindings);
    }

    @Override
    public Object fetchValue(String sql, QueryPart... parts)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(sql, parts);
    }

    @Override
    public Optional<?> fetchOptionalValue(SQL sql)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(sql);
    }

    @Override
    public Optional<?> fetchOptionalValue(String sql)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(sql);
    }

    @Override
    public Optional<?> fetchOptionalValue(String sql, Object... bindings)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(sql, bindings);
    }

    @Override
    public Optional<?> fetchOptionalValue(String sql, QueryPart... parts)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(sql, parts);
    }

    @Override
    public List<?> fetchValues(SQL sql) throws DataAccessException, InvalidResultException {
        return context.fetchValues(sql);
    }

    @Override
    public List<?> fetchValues(String sql) throws DataAccessException, InvalidResultException {
        return context.fetchValues(sql);
    }

    @Override
    public List<?> fetchValues(String sql, Object... bindings) throws DataAccessException, InvalidResultException {
        return context.fetchValues(sql, bindings);
    }

    @Override
    public List<?> fetchValues(String sql, QueryPart... parts) throws DataAccessException, InvalidResultException {
        return context.fetchValues(sql, parts);
    }

    @Override
    public int execute(SQL sql) throws DataAccessException {
        return context.execute(sql);
    }

    @Override
    public int execute(String sql) throws DataAccessException {
        return context.execute(sql);
    }

    @Override
    public int execute(String sql, Object... bindings) throws DataAccessException {
        return context.execute(sql, bindings);
    }

    @Override
    public int execute(String sql, QueryPart... parts) throws DataAccessException {
        return context.execute(sql, parts);
    }

    @Override
    public ResultQuery<Record> resultQuery(SQL sql) {
        return context.resultQuery(sql);
    }

    @Override
    public ResultQuery<Record> resultQuery(String sql) {
        return context.resultQuery(sql);
    }

    @Override
    public ResultQuery<Record> resultQuery(String sql, Object... bindings) {
        return context.resultQuery(sql, bindings);
    }

    @Override
    public ResultQuery<Record> resultQuery(String sql, QueryPart... parts) {
        return context.resultQuery(sql, parts);
    }

    @Override
    public Result<Record> fetch(ResultSet rs) throws DataAccessException {
        return context.fetch(rs);
    }

    @Override
    public Result<Record> fetch(ResultSet rs, Field<?>... fields) throws DataAccessException {
        return context.fetch(rs, fields);
    }

    @Override
    public Result<Record> fetch(ResultSet rs, DataType<?>... types) throws DataAccessException {
        return context.fetch(rs, types);
    }

    @Override
    public Result<Record> fetch(ResultSet rs, Class<?>... types) throws DataAccessException {
        return context.fetch(rs, types);
    }

    @Override
    public Record fetchOne(ResultSet rs) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(rs);
    }

    @Override
    public Record fetchOne(ResultSet rs, Field<?>... fields) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(rs, fields);
    }

    @Override
    public Record fetchOne(ResultSet rs, DataType<?>... types) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(rs, types);
    }

    @Override
    public Record fetchOne(ResultSet rs, Class<?>... types) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(rs, types);
    }

    @Override
    public Record fetchSingle(ResultSet rs) throws DataAccessException, TooManyRowsException {
        return context.fetchSingle(rs);
    }

    @Override
    public Record fetchSingle(ResultSet rs, Field<?>... fields)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(rs, fields);
    }

    @Override
    public Record fetchSingle(ResultSet rs, DataType<?>... types)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(rs, types);
    }

    @Override
    public Record fetchSingle(ResultSet rs, Class<?>... types)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(rs, types);
    }

    @Override
    public Optional<Record> fetchOptional(ResultSet rs)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchOptional(rs);
    }

    @Override
    public Optional<Record> fetchOptional(ResultSet rs, Field<?>... fields)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(rs, fields);
    }

    @Override
    public Optional<Record> fetchOptional(ResultSet rs, DataType<?>... types)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(rs, types);
    }

    @Override
    public Optional<Record> fetchOptional(ResultSet rs, Class<?>... types)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(rs, types);
    }

    @Override
    public Object fetchValue(ResultSet rs) throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(rs);
    }

    @Override
    public <T> T fetchValue(ResultSet rs, Field<T> field)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(rs, field);
    }

    @Override
    public <T> T fetchValue(ResultSet rs, DataType<T> type)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(rs, type);
    }

    @Override
    public <T> T fetchValue(ResultSet rs, Class<T> type)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchValue(rs, type);
    }

    @Override
    public Optional<?> fetchOptionalValue(ResultSet rs)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(rs);
    }

    @Override
    public <T> Optional<T> fetchOptionalValue(ResultSet rs, Field<T> field)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(rs, field);
    }

    @Override
    public <T> Optional<T> fetchOptionalValue(ResultSet rs, DataType<T> type)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(rs, type);
    }

    @Override
    public <T> Optional<T> fetchOptionalValue(ResultSet rs, Class<T> type)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(rs, type);
    }

    @Override
    public List<?> fetchValues(ResultSet rs) throws DataAccessException, InvalidResultException {
        return context.fetchValues(rs);
    }

    @Override
    public <T> List<T> fetchValues(ResultSet rs, Field<T> field) throws DataAccessException, InvalidResultException {
        return context.fetchValues(rs, field);
    }

    @Override
    public <T> List<T> fetchValues(ResultSet rs, DataType<T> type) throws DataAccessException, InvalidResultException {
        return context.fetchValues(rs, type);
    }

    @Override
    public <T> List<T> fetchValues(ResultSet rs, Class<T> type) throws DataAccessException, InvalidResultException {
        return context.fetchValues(rs, type);
    }

    @Override
    public Cursor<Record> fetchLazy(ResultSet rs) throws DataAccessException {
        return context.fetchLazy(rs);
    }

    @Override
    public Cursor<Record> fetchLazy(ResultSet rs, Field<?>... fields) throws DataAccessException {
        return context.fetchLazy(rs, fields);
    }

    @Override
    public Cursor<Record> fetchLazy(ResultSet rs, DataType<?>... types) throws DataAccessException {
        return context.fetchLazy(rs, types);
    }

    @Override
    public Cursor<Record> fetchLazy(ResultSet rs, Class<?>... types) throws DataAccessException {
        return context.fetchLazy(rs, types);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(ResultSet rs) {
        return context.fetchAsync(rs);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(ResultSet rs, Field<?>... fields) {
        return context.fetchAsync(rs, fields);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(ResultSet rs, DataType<?>... types) {
        return context.fetchAsync(rs, types);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(ResultSet rs, Class<?>... types) {
        return context.fetchAsync(rs, types);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, ResultSet rs) {
        return context.fetchAsync(executor, rs);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, ResultSet rs, Field<?>... fields) {
        return context.fetchAsync(executor, rs, fields);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, ResultSet rs, DataType<?>... types) {
        return context.fetchAsync(executor, rs, types);
    }

    @Override
    public CompletionStage<Result<Record>> fetchAsync(Executor executor, ResultSet rs, Class<?>... types) {
        return context.fetchAsync(executor, rs, types);
    }

    @Override
    public Stream<Record> fetchStream(ResultSet rs) throws DataAccessException {
        return context.fetchStream(rs);
    }

    @Override
    public Stream<Record> fetchStream(ResultSet rs, Field<?>... fields) throws DataAccessException {
        return context.fetchStream(rs, fields);
    }

    @Override
    public Stream<Record> fetchStream(ResultSet rs, DataType<?>... types) throws DataAccessException {
        return context.fetchStream(rs, types);
    }

    @Override
    public Stream<Record> fetchStream(ResultSet rs, Class<?>... types) throws DataAccessException {
        return context.fetchStream(rs, types);
    }

    @Override
    public Result<Record> fetchFromTXT(String string) throws DataAccessException {
        return context.fetchFromTXT(string);
    }

    @Override
    public Result<Record> fetchFromTXT(String string, String nullLiteral) throws DataAccessException {
        return context.fetchFromTXT(string, nullLiteral);
    }

    @Override
    public Result<Record> fetchFromHTML(String string) throws DataAccessException {
        return context.fetchFromHTML(string);
    }

    @Override
    public Result<Record> fetchFromCSV(String string) throws DataAccessException {
        return context.fetchFromCSV(string);
    }

    @Override
    public Result<Record> fetchFromCSV(String string, char delimiter) throws DataAccessException {
        return context.fetchFromCSV(string, delimiter);
    }

    @Override
    public Result<Record> fetchFromCSV(String string, boolean header) throws DataAccessException {
        return context.fetchFromCSV(string, header);
    }

    @Override
    public Result<Record> fetchFromCSV(String string, boolean header, char delimiter) throws DataAccessException {
        return context.fetchFromCSV(string, header, delimiter);
    }

    @Override
    public Result<Record> fetchFromJSON(String string) {
        return context.fetchFromJSON(string);
    }

    @Override
    public Result<Record> fetchFromXML(String string) {
        return context.fetchFromXML(string);
    }

    @Override
    public Result<Record> fetchFromStringData(String[]... data) {
        return context.fetchFromStringData(data);
    }

    @Override
    public Result<Record> fetchFromStringData(List<String[]> data) {
        return context.fetchFromStringData(data);
    }

    @Override
    public Result<Record> fetchFromStringData(List<String[]> data, boolean header) {
        return context.fetchFromStringData(data, header);
    }

    @Override
    public WithAsStep with(String alias) {
        return context.with(alias);
    }

    @Override
    public WithAsStep with(String alias, String... fieldAliases) {
        return context.with(alias, fieldAliases);
    }

    @Override
    public WithAsStep with(String alias, Collection<String> fieldAliases) {
        return context.with(alias, fieldAliases);
    }

    @Override
    public WithAsStep with(Name alias) {
        return context.with(alias);
    }

    @Override
    public WithAsStep with(Name alias, Name... fieldAliases) {
        return context.with(alias, fieldAliases);
    }

    @Override
    public WithAsStep with(Name alias, Collection<? extends Name> fieldAliases) {
        return context.with(alias, fieldAliases);
    }

    @SuppressWarnings("deprecation")
    @Override
    public WithAsStep with(String alias, Function<? super Field<?>, ? extends String> fieldNameFunction) {
        return context.with(alias, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public WithAsStep with(String alias,
            BiFunction<? super Field<?>, ? super Integer, ? extends String> fieldNameFunction) {
        return context.with(alias, fieldNameFunction);
    }

    @Override
    public WithAsStep1 with(String alias, String fieldAlias1) {
        return context.with(alias, fieldAlias1);
    }

    @Override
    public WithAsStep2 with(String alias, String fieldAlias1, String fieldAlias2) {
        return context.with(alias, fieldAlias1, fieldAlias2);
    }

    @Override
    public WithAsStep3 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3);
    }

    @Override
    public WithAsStep4 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4);
    }

    @Override
    public WithAsStep5 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5);
    }

    @Override
    public WithAsStep6 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6);
    }

    @Override
    public WithAsStep7 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7);
    }

    @Override
    public WithAsStep8 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8);
    }

    @Override
    public WithAsStep9 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9);
    }

    @Override
    public WithAsStep10 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10);
    }

    @Override
    public WithAsStep11 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11);
    }

    @Override
    public WithAsStep12 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12);
    }

    @Override
    public WithAsStep13 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13);
    }

    @Override
    public WithAsStep14 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14);
    }

    @Override
    public WithAsStep15 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15);
    }

    @Override
    public WithAsStep16 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16);
    }

    @Override
    public WithAsStep17 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17);
    }

    @Override
    public WithAsStep18 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18);
    }

    @Override
    public WithAsStep19 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19);
    }

    @Override
    public WithAsStep20 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19, String fieldAlias20) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19, fieldAlias20);
    }

    @Override
    public WithAsStep21 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19, String fieldAlias20, String fieldAlias21) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19, fieldAlias20,
                fieldAlias21);
    }

    @Override
    public WithAsStep22 with(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19, String fieldAlias20, String fieldAlias21, String fieldAlias22) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19, fieldAlias20,
                fieldAlias21, fieldAlias22);
    }

    @Override
    public WithAsStep1 with(Name alias, Name fieldAlias1) {
        return context.with(alias, fieldAlias1);
    }

    @Override
    public WithAsStep2 with(Name alias, Name fieldAlias1, Name fieldAlias2) {
        return context.with(alias, fieldAlias1, fieldAlias2);
    }

    @Override
    public WithAsStep3 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3);
    }

    @Override
    public WithAsStep4 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4);
    }

    @Override
    public WithAsStep5 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5);
    }

    @Override
    public WithAsStep6 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6);
    }

    @Override
    public WithAsStep7 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7);
    }

    @Override
    public WithAsStep8 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8);
    }

    @Override
    public WithAsStep9 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9);
    }

    @Override
    public WithAsStep10 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10);
    }

    @Override
    public WithAsStep11 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11);
    }

    @Override
    public WithAsStep12 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12);
    }

    @Override
    public WithAsStep13 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13);
    }

    @Override
    public WithAsStep14 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14);
    }

    @Override
    public WithAsStep15 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15);
    }

    @Override
    public WithAsStep16 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16);
    }

    @Override
    public WithAsStep17 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16, Name fieldAlias17) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17);
    }

    @Override
    public WithAsStep18 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16, Name fieldAlias17, Name fieldAlias18) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18);
    }

    @Override
    public WithAsStep19 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19);
    }

    @Override
    public WithAsStep20 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19, Name fieldAlias20) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19, fieldAlias20);
    }

    @Override
    public WithAsStep21 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19, Name fieldAlias20,
            Name fieldAlias21) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19, fieldAlias20,
                fieldAlias21);
    }

    @Override
    public WithAsStep22 with(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9, Name fieldAlias10,
            Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14, Name fieldAlias15,
            Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19, Name fieldAlias20,
            Name fieldAlias21, Name fieldAlias22) {
        return context.with(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5, fieldAlias6,
                fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12, fieldAlias13,
                fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19, fieldAlias20,
                fieldAlias21, fieldAlias22);
    }

    @Override
    public WithStep with(CommonTableExpression<?>... tables) {
        return context.with(tables);
    }

    @Override
    public WithStep with(Collection<? extends CommonTableExpression<?>> tables) {
        return context.with(tables);
    }

    @Override
    public WithAsStep withRecursive(String alias) {
        return context.withRecursive(alias);
    }

    @Override
    public WithAsStep withRecursive(String alias, String... fieldAliases) {
        return context.withRecursive(alias, fieldAliases);
    }

    @Override
    public WithAsStep withRecursive(String alias, Collection<String> fieldAliases) {
        return context.withRecursive(alias, fieldAliases);
    }

    @Override
    public WithAsStep withRecursive(Name alias) {
        return context.withRecursive(alias);
    }

    @Override
    public WithAsStep withRecursive(Name alias, Name... fieldAliases) {
        return context.withRecursive(alias, fieldAliases);
    }

    @Override
    public WithAsStep withRecursive(Name alias, Collection<? extends Name> fieldAliases) {
        return context.withRecursive(alias, fieldAliases);
    }

    @SuppressWarnings("deprecation")
    @Override
    public WithAsStep withRecursive(String alias, Function<? super Field<?>, ? extends String> fieldNameFunction) {
        return context.withRecursive(alias, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public WithAsStep withRecursive(String alias,
            BiFunction<? super Field<?>, ? super Integer, ? extends String> fieldNameFunction) {
        return context.withRecursive(alias, fieldNameFunction);
    }

    @Override
    public WithAsStep1 withRecursive(String alias, String fieldAlias1) {
        return context.withRecursive(alias, fieldAlias1);
    }

    @Override
    public WithAsStep2 withRecursive(String alias, String fieldAlias1, String fieldAlias2) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2);
    }

    @Override
    public WithAsStep3 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3);
    }

    @Override
    public WithAsStep4 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4);
    }

    @Override
    public WithAsStep5 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5);
    }

    @Override
    public WithAsStep6 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6);
    }

    @Override
    public WithAsStep7 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7);
    }

    @Override
    public WithAsStep8 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8);
    }

    @Override
    public WithAsStep9 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9);
    }

    @Override
    public WithAsStep10 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10);
    }

    @Override
    public WithAsStep11 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11);
    }

    @Override
    public WithAsStep12 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12);
    }

    @Override
    public WithAsStep13 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13);
    }

    @Override
    public WithAsStep14 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14);
    }

    @Override
    public WithAsStep15 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15);
    }

    @Override
    public WithAsStep16 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16);
    }

    @Override
    public WithAsStep17 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17);
    }

    @Override
    public WithAsStep18 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18);
    }

    @Override
    public WithAsStep19 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19);
    }

    @Override
    public WithAsStep20 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19, String fieldAlias20) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19,
                fieldAlias20);
    }

    @Override
    public WithAsStep21 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19, String fieldAlias20, String fieldAlias21) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19,
                fieldAlias20, fieldAlias21);
    }

    @Override
    public WithAsStep22 withRecursive(String alias, String fieldAlias1, String fieldAlias2, String fieldAlias3,
            String fieldAlias4, String fieldAlias5, String fieldAlias6, String fieldAlias7, String fieldAlias8,
            String fieldAlias9, String fieldAlias10, String fieldAlias11, String fieldAlias12, String fieldAlias13,
            String fieldAlias14, String fieldAlias15, String fieldAlias16, String fieldAlias17, String fieldAlias18,
            String fieldAlias19, String fieldAlias20, String fieldAlias21, String fieldAlias22) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19,
                fieldAlias20, fieldAlias21, fieldAlias22);
    }

    @Override
    public WithAsStep1 withRecursive(Name alias, Name fieldAlias1) {
        return context.withRecursive(alias, fieldAlias1);
    }

    @Override
    public WithAsStep2 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2);
    }

    @Override
    public WithAsStep3 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3);
    }

    @Override
    public WithAsStep4 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4);
    }

    @Override
    public WithAsStep5 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5);
    }

    @Override
    public WithAsStep6 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6);
    }

    @Override
    public WithAsStep7 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7);
    }

    @Override
    public WithAsStep8 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8);
    }

    @Override
    public WithAsStep9 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3, Name fieldAlias4,
            Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9);
    }

    @Override
    public WithAsStep10 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10);
    }

    @Override
    public WithAsStep11 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11);
    }

    @Override
    public WithAsStep12 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12);
    }

    @Override
    public WithAsStep13 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13);
    }

    @Override
    public WithAsStep14 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14);
    }

    @Override
    public WithAsStep15 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15);
    }

    @Override
    public WithAsStep16 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16);
    }

    @Override
    public WithAsStep17 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16, Name fieldAlias17) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17);
    }

    @Override
    public WithAsStep18 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16, Name fieldAlias17, Name fieldAlias18) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18);
    }

    @Override
    public WithAsStep19 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19);
    }

    @Override
    public WithAsStep20 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19,
            Name fieldAlias20) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19,
                fieldAlias20);
    }

    @Override
    public WithAsStep21 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19,
            Name fieldAlias20, Name fieldAlias21) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19,
                fieldAlias20, fieldAlias21);
    }

    @Override
    public WithAsStep22 withRecursive(Name alias, Name fieldAlias1, Name fieldAlias2, Name fieldAlias3,
            Name fieldAlias4, Name fieldAlias5, Name fieldAlias6, Name fieldAlias7, Name fieldAlias8, Name fieldAlias9,
            Name fieldAlias10, Name fieldAlias11, Name fieldAlias12, Name fieldAlias13, Name fieldAlias14,
            Name fieldAlias15, Name fieldAlias16, Name fieldAlias17, Name fieldAlias18, Name fieldAlias19,
            Name fieldAlias20, Name fieldAlias21, Name fieldAlias22) {
        return context.withRecursive(alias, fieldAlias1, fieldAlias2, fieldAlias3, fieldAlias4, fieldAlias5,
                fieldAlias6, fieldAlias7, fieldAlias8, fieldAlias9, fieldAlias10, fieldAlias11, fieldAlias12,
                fieldAlias13, fieldAlias14, fieldAlias15, fieldAlias16, fieldAlias17, fieldAlias18, fieldAlias19,
                fieldAlias20, fieldAlias21, fieldAlias22);
    }

    @Override
    public WithStep withRecursive(CommonTableExpression<?>... tables) {
        return context.withRecursive(tables);
    }

    @Override
    public WithStep withRecursive(Collection<? extends CommonTableExpression<?>> tables) {
        return context.withRecursive(tables);
    }

    @Override
    public <R extends Record> SelectWhereStep<R> selectFrom(Table<R> table) {
        return time(context.selectFrom(table));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(Name table) {
        return time(context.selectFrom(table));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(SQL sql) {
        return time(context.selectFrom(sql));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(String sql) {
        return time(context.selectFrom(sql));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(String sql, Object... bindings) {
        return time(context.selectFrom(sql, bindings));
    }

    @Override
    public SelectWhereStep<Record> selectFrom(String sql, QueryPart... parts) {
        return time(context.selectFrom(sql, parts));
    }

    @Override
    public SelectSelectStep<Record> select(Collection<? extends SelectFieldOrAsterisk> fields) {
        return time(context.select(fields));
    }

    @Override
    public SelectSelectStep<Record> select(SelectFieldOrAsterisk... fields) {
        return time(context.select(fields));
    }

    @Override
    public <T1> SelectSelectStep<Record1<T1>> select(SelectField<T1> field1) {
        return time(context.select(field1));
    }

    @Override
    public <T1, T2> SelectSelectStep<Record2<T1, T2>> select(SelectField<T1> field1, SelectField<T2> field2) {
        return time(context.select(field1, field2));
    }

    @Override
    public <T1, T2, T3> SelectSelectStep<Record3<T1, T2, T3>> select(SelectField<T1> field1, SelectField<T2> field2,
            SelectField<T3> field3) {
        return time(context.select(field1, field2, field3));
    }

    @Override
    public <T1, T2, T3, T4> SelectSelectStep<Record4<T1, T2, T3, T4>> select(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4) {
        return time(context.select(field1, field2, field3, field4));
    }

    @Override
    public <T1, T2, T3, T4, T5> SelectSelectStep<Record5<T1, T2, T3, T4, T5>> select(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5) {
        return time(context.select(field1, field2, field3, field4, field5));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> SelectSelectStep<Record6<T1, T2, T3, T4, T5, T6>> select(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5,
            SelectField<T6> field6) {
        return time(context.select(field1, field2, field3, field4, field5, field6));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> SelectSelectStep<Record7<T1, T2, T3, T4, T5, T6, T7>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> SelectSelectStep<Record8<T1, T2, T3, T4, T5, T6, T7, T8>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectSelectStep<Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectSelectStep<Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> SelectSelectStep<Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> SelectSelectStep<Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> SelectSelectStep<Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> SelectSelectStep<Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> SelectSelectStep<Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> SelectSelectStep<Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> SelectSelectStep<Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> SelectSelectStep<Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> SelectSelectStep<Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> SelectSelectStep<Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>> select(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20) {
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
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
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
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
        return time(context.select(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21,
                field22));
    }

    @Override
    public SelectSelectStep<Record> selectDistinct(Collection<? extends SelectFieldOrAsterisk> fields) {
        return time(context.selectDistinct(fields));
    }

    @Override
    public SelectSelectStep<Record> selectDistinct(SelectFieldOrAsterisk... fields) {
        return time(context.selectDistinct(fields));
    }

    @Override
    public <T1> SelectSelectStep<Record1<T1>> selectDistinct(SelectField<T1> field1) {
        return time(context.selectDistinct(field1));
    }

    @Override
    public <T1, T2> SelectSelectStep<Record2<T1, T2>> selectDistinct(SelectField<T1> field1, SelectField<T2> field2) {
        return time(context.selectDistinct(field1, field2));
    }

    @Override
    public <T1, T2, T3> SelectSelectStep<Record3<T1, T2, T3>> selectDistinct(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3) {
        return time(context.selectDistinct(field1, field2, field3));
    }

    @Override
    public <T1, T2, T3, T4> SelectSelectStep<Record4<T1, T2, T3, T4>> selectDistinct(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4) {
        return time(context.selectDistinct(field1, field2, field3, field4));
    }

    @Override
    public <T1, T2, T3, T4, T5> SelectSelectStep<Record5<T1, T2, T3, T4, T5>> selectDistinct(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> SelectSelectStep<Record6<T1, T2, T3, T4, T5, T6>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> SelectSelectStep<Record7<T1, T2, T3, T4, T5, T6, T7>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> SelectSelectStep<Record8<T1, T2, T3, T4, T5, T6, T7, T8>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> SelectSelectStep<Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> SelectSelectStep<Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> SelectSelectStep<Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> SelectSelectStep<Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> SelectSelectStep<Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> SelectSelectStep<Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> SelectSelectStep<Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> SelectSelectStep<Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> SelectSelectStep<Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> SelectSelectStep<Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> SelectSelectStep<Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19));
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> SelectSelectStep<Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>> selectDistinct(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20) {
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
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
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
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
        return time(context.selectDistinct(field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20,
                field21, field22));
    }

    @Override
    public SelectSelectStep<Record1<Integer>> selectZero() {
        return time(context.selectZero());
    }

    @Override
    public SelectSelectStep<Record1<Integer>> selectOne() {
        return time(context.selectOne());
    }

    @Override
    public SelectSelectStep<Record1<Integer>> selectCount() {
        return time(context.selectCount());
    }

    @Override
    public SelectQuery<Record> selectQuery() {
        return time(context.selectQuery());
    }

    @Override
    public <R extends Record> SelectQuery<R> selectQuery(TableLike<R> table) {
        return time(context.selectQuery(table));
    }

    @Override
    public <R extends Record> InsertQuery<R> insertQuery(Table<R> into) {
        return time(context.insertQuery(into));
    }

    @Override
    public <R extends Record> InsertSetStep<R> insertInto(Table<R> into) {
        return timeCoercable(context.insertInto(into));
    }

    @Override
    public <R extends Record, T1> InsertValuesStep1<R, T1> insertInto(Table<R> into, Field<T1> field1) {
        return time(context.insertInto(into, field1));
    }

    @Override
    public <R extends Record, T1, T2> InsertValuesStep2<R, T1, T2> insertInto(Table<R> into, Field<T1> field1,
            Field<T2> field2) {
        return time(context.insertInto(into, field1, field2));
    }

    @Override
    public <R extends Record, T1, T2, T3> InsertValuesStep3<R, T1, T2, T3> insertInto(Table<R> into, Field<T1> field1,
            Field<T2> field2, Field<T3> field3) {
        return time(context.insertInto(into, field1, field2, field3));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4> InsertValuesStep4<R, T1, T2, T3, T4> insertInto(Table<R> into,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4) {
        return time(context.insertInto(into, field1, field2, field3, field4));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5> InsertValuesStep5<R, T1, T2, T3, T4, T5> insertInto(Table<R> into,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6> InsertValuesStep6<R, T1, T2, T3, T4, T5, T6> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7> InsertValuesStep7<R, T1, T2, T3, T4, T5, T6, T7> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8> InsertValuesStep8<R, T1, T2, T3, T4, T5, T6, T7, T8> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9> InsertValuesStep9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> InsertValuesStep10<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> InsertValuesStep11<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> InsertValuesStep12<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> InsertValuesStep13<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> InsertValuesStep14<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> InsertValuesStep15<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> InsertValuesStep16<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> InsertValuesStep17<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> InsertValuesStep18<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> InsertValuesStep19<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> InsertValuesStep20<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20));
    }

    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> InsertValuesStep21<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> insertInto(
            Table<R> into, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20,
            Field<T21> field21) {
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
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
        return time(context.insertInto(into, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10, field11, field12, field13, field14, field15, field16, field17, field18, field19, field20,
                field21, field22));
    }

    @Override
    public <R extends Record> InsertValuesStepN<R> insertInto(Table<R> into, Field<?>... fields) {
        return time(context.insertInto(into, fields));
    }

    @Override
    public <R extends Record> InsertValuesStepN<R> insertInto(Table<R> into, Collection<? extends Field<?>> fields) {
        return time(context.insertInto(into, fields));
    }

    @Override
    public <R extends Record> UpdateQuery<R> updateQuery(Table<R> table) {
        return time(context.updateQuery(table));
    }

    @Override
    public <R extends Record> UpdateSetFirstStep<R> update(Table<R> table) {
        return timeCoercable(context.update(table));
    }

    @Override
    public <R extends Record> MergeUsingStep<R> mergeInto(Table<R> table) {
        return context.mergeInto(table);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1> MergeKeyStep1<R, T1> mergeInto(Table<R> table, Field<T1> field1) {
        return context.mergeInto(table, field1);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2> MergeKeyStep2<R, T1, T2> mergeInto(Table<R> table, Field<T1> field1,
            Field<T2> field2) {
        return context.mergeInto(table, field1, field2);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3> MergeKeyStep3<R, T1, T2, T3> mergeInto(Table<R> table, Field<T1> field1,
            Field<T2> field2, Field<T3> field3) {
        return context.mergeInto(table, field1, field2, field3);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4> MergeKeyStep4<R, T1, T2, T3, T4> mergeInto(Table<R> table,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4) {
        return context.mergeInto(table, field1, field2, field3, field4);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5> MergeKeyStep5<R, T1, T2, T3, T4, T5> mergeInto(Table<R> table,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5) {
        return context.mergeInto(table, field1, field2, field3, field4, field5);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6> MergeKeyStep6<R, T1, T2, T3, T4, T5, T6> mergeInto(Table<R> table,
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7> MergeKeyStep7<R, T1, T2, T3, T4, T5, T6, T7> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8> MergeKeyStep8<R, T1, T2, T3, T4, T5, T6, T7, T8> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9> MergeKeyStep9<R, T1, T2, T3, T4, T5, T6, T7, T8, T9> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> MergeKeyStep10<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9,
                field10);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> MergeKeyStep11<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> MergeKeyStep12<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> MergeKeyStep13<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> MergeKeyStep14<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> MergeKeyStep15<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> MergeKeyStep16<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> MergeKeyStep17<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> MergeKeyStep18<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> MergeKeyStep19<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> MergeKeyStep20<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> MergeKeyStep21<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20,
            Field<T21> field21) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> MergeKeyStep22<R, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> mergeInto(
            Table<R> table, Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5,
            Field<T6> field6, Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10,
            Field<T11> field11, Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15,
            Field<T16> field16, Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20,
            Field<T21> field21, Field<T22> field22) {
        return context.mergeInto(table, field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21,
                field22);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record> MergeKeyStepN<R> mergeInto(Table<R> table, Field<?>... fields) {
        return context.mergeInto(table, fields);
    }

    @SuppressWarnings("deprecation")
    @Override
    public <R extends Record> MergeKeyStepN<R> mergeInto(Table<R> table, Collection<? extends Field<?>> fields) {
        return context.mergeInto(table, fields);
    }

    @Override
    public <R extends Record> DeleteQuery<R> deleteQuery(Table<R> table) {
        return context.deleteQuery(table);
    }

    @Override
    public <R extends Record> DeleteUsingStep<R> deleteFrom(Table<R> table) {
        return context.deleteFrom(table);
    }

    @Override
    public <R extends Record> DeleteUsingStep<R> delete(Table<R> table) {
        return context.delete(table);
    }

    @Override
    public void batched(BatchedRunnable runnable) {
        context.batched(runnable);
    }

    @Override
    public <T> T batchedResult(BatchedCallable<T> callable) {
        return context.batchedResult(callable);
    }

    @Override
    public Batch batch(Query... queries) {
        return context.batch(queries);
    }

    @Override
    public Batch batch(Queries queries) {
        return context.batch(queries);
    }

    @Override
    public Batch batch(String... queries) {
        return context.batch(queries);
    }

    @Override
    public Batch batch(Collection<? extends Query> queries) {
        return context.batch(queries);
    }

    @Override
    public BatchBindStep batch(Query query) {
        return context.batch(query);
    }

    @Override
    public BatchBindStep batch(String sql) {
        return context.batch(sql);
    }

    @Override
    public Batch batch(Query query, Object[]... bindings) {
        return context.batch(query, bindings);
    }

    @Override
    public Batch batch(String sql, Object[]... bindings) {
        return context.batch(sql, bindings);
    }

    @Override
    public Batch batchStore(UpdatableRecord<?>... records) {
        return context.batchStore(records);
    }

    @Override
    public Batch batchStore(Collection<? extends UpdatableRecord<?>> records) {
        return context.batchStore(records);
    }

    @Override
    public Batch batchInsert(TableRecord<?>... records) {
        return context.batchInsert(records);
    }

    @Override
    public Batch batchInsert(Collection<? extends TableRecord<?>> records) {
        return context.batchInsert(records);
    }

    @Override
    public Batch batchUpdate(UpdatableRecord<?>... records) {
        return context.batchUpdate(records);
    }

    @Override
    public Batch batchUpdate(Collection<? extends UpdatableRecord<?>> records) {
        return context.batchUpdate(records);
    }

    @Override
    public Batch batchMerge(UpdatableRecord<?>... records) {
        return context.batchMerge(records);
    }

    @Override
    public Batch batchMerge(Collection<? extends UpdatableRecord<?>> records) {
        return context.batchMerge(records);
    }

    @Override
    public Batch batchDelete(UpdatableRecord<?>... records) {
        return context.batchDelete(records);
    }

    @Override
    public Batch batchDelete(Collection<? extends UpdatableRecord<?>> records) {
        return context.batchDelete(records);
    }

    @Override
    public Queries ddl(Catalog catalog) {
        return context.ddl(catalog);
    }

    @Override
    public Queries ddl(Catalog schema, DDLExportConfiguration configuration) {
        return context.ddl(schema, configuration);
    }

    @Override
    public Queries ddl(Catalog schema, DDLFlag... flags) {
        return context.ddl(schema, flags);
    }

    @Override
    public Queries ddl(Schema schema) {
        return context.ddl(schema);
    }

    @Override
    public Queries ddl(Schema schema, DDLExportConfiguration configuration) {
        return context.ddl(schema, configuration);
    }

    @Override
    public Queries ddl(Schema schema, DDLFlag... flags) {
        return context.ddl(schema, flags);
    }

    @Override
    public Queries ddl(Table<?> table) {
        return context.ddl(table);
    }

    @Override
    public Queries ddl(Table<?> table, DDLExportConfiguration configuration) {
        return context.ddl(table, configuration);
    }

    @Override
    public Queries ddl(Table<?> table, DDLFlag... flags) {
        return context.ddl(table, flags);
    }

    @Override
    public Queries ddl(Table<?>... tables) {
        return context.ddl(tables);
    }

    @Override
    public Queries ddl(Table<?>[] tables, DDLExportConfiguration configuration) {
        return context.ddl(tables, configuration);
    }

    @Override
    public Queries ddl(Table<?>[] tables, DDLFlag... flags) {
        return context.ddl(tables, flags);
    }

    @Override
    public Queries ddl(Collection<? extends Table<?>> tables) {
        return context.ddl(tables);
    }

    @Override
    public Queries ddl(Collection<? extends Table<?>> tables, DDLFlag... flags) {
        return context.ddl(tables, flags);
    }

    @Override
    public Queries ddl(Collection<? extends Table<?>> tables, DDLExportConfiguration configuration) {
        return context.ddl(tables, configuration);
    }

    @Override
    public RowCountQuery setCatalog(String catalog) {
        return context.setCatalog(catalog);
    }

    @Override
    public RowCountQuery setCatalog(Name catalog) {
        return context.setCatalog(catalog);
    }

    @Override
    public RowCountQuery setCatalog(Catalog catalog) {
        return context.setCatalog(catalog);
    }

    @Override
    public RowCountQuery setSchema(String schema) {
        return context.setSchema(schema);
    }

    @Override
    public RowCountQuery setSchema(Name schema) {
        return context.setSchema(schema);
    }

    @Override
    public RowCountQuery setSchema(Schema schema) {
        return context.setSchema(schema);
    }

    @Override
    public RowCountQuery set(Name name, Param<?> param) {
        return context.set(name, param);
    }

    @Override
    public CreateDatabaseFinalStep createDatabase(String database) {
        return context.createDatabase(database);
    }

    @Override
    public CreateDatabaseFinalStep createDatabase(Name database) {
        return context.createDatabase(database);
    }

    @Override
    public CreateDatabaseFinalStep createDatabase(Catalog database) {
        return context.createDatabase(database);
    }

    @Override
    public CreateDatabaseFinalStep createDatabaseIfNotExists(String database) {
        return context.createDatabaseIfNotExists(database);
    }

    @Override
    public CreateDatabaseFinalStep createDatabaseIfNotExists(Name database) {
        return context.createDatabaseIfNotExists(database);
    }

    @Override
    public CreateDatabaseFinalStep createDatabaseIfNotExists(Catalog database) {
        return context.createDatabaseIfNotExists(database);
    }

    @Override
    public CreateDomainAsStep createDomain(String domain) {
        return context.createDomain(domain);
    }

    @Override
    public CreateDomainAsStep createDomain(Name domain) {
        return context.createDomain(domain);
    }

    @Override
    public CreateDomainAsStep createDomain(Domain<?> domain) {
        return context.createDomain(domain);
    }

    @Override
    public CreateDomainAsStep createDomainIfNotExists(String domain) {
        return context.createDomainIfNotExists(domain);
    }

    @Override
    public CreateDomainAsStep createDomainIfNotExists(Name domain) {
        return context.createDomainIfNotExists(domain);
    }

    @Override
    public CreateDomainAsStep createDomainIfNotExists(Domain<?> domain) {
        return context.createDomainIfNotExists(domain);
    }

    @Override
    public CommentOnIsStep commentOnTable(String tableName) {
        return context.commentOnTable(tableName);
    }

    @Override
    public CommentOnIsStep commentOnTable(Name tableName) {
        return context.commentOnTable(tableName);
    }

    @Override
    public CommentOnIsStep commentOnTable(Table<?> table) {
        return context.commentOnTable(table);
    }

    @Override
    public CommentOnIsStep commentOnView(String viewName) {
        return context.commentOnView(viewName);
    }

    @Override
    public CommentOnIsStep commentOnView(Name viewName) {
        return context.commentOnView(viewName);
    }

    @Override
    public CommentOnIsStep commentOnView(Table<?> view) {
        return context.commentOnView(view);
    }

    @Override
    public CommentOnIsStep commentOnColumn(Name columnName) {
        return context.commentOnColumn(columnName);
    }

    @Override
    public CommentOnIsStep commentOnColumn(Field<?> field) {
        return context.commentOnColumn(field);
    }

    @Override
    public CreateSchemaFinalStep createSchema(String schema) {
        return context.createSchema(schema);
    }

    @Override
    public CreateSchemaFinalStep createSchema(Name schema) {
        return context.createSchema(schema);
    }

    @Override
    public CreateSchemaFinalStep createSchema(Schema schema) {
        return context.createSchema(schema);
    }

    @Override
    public CreateSchemaFinalStep createSchemaIfNotExists(String schema) {
        return context.createSchemaIfNotExists(schema);
    }

    @Override
    public CreateSchemaFinalStep createSchemaIfNotExists(Name schema) {
        return context.createSchemaIfNotExists(schema);
    }

    @Override
    public CreateSchemaFinalStep createSchemaIfNotExists(Schema schema) {
        return context.createSchemaIfNotExists(schema);
    }

    @Override
    public CreateTableColumnStep createTable(String table) {
        return context.createTable(table);
    }

    @Override
    public CreateTableColumnStep createTable(Name table) {
        return context.createTable(table);
    }

    @Override
    public CreateTableColumnStep createTable(Table<?> table) {
        return context.createTable(table);
    }

    @Override
    public CreateTableColumnStep createTableIfNotExists(String table) {
        return context.createTableIfNotExists(table);
    }

    @Override
    public CreateTableColumnStep createTableIfNotExists(Name table) {
        return context.createTableIfNotExists(table);
    }

    @Override
    public CreateTableColumnStep createTableIfNotExists(Table<?> table) {
        return context.createTableIfNotExists(table);
    }

    @Override
    public CreateTableColumnStep createTemporaryTable(String table) {
        return context.createTemporaryTable(table);
    }

    @Override
    public CreateTableColumnStep createTemporaryTable(Name table) {
        return context.createTemporaryTable(table);
    }

    @Override
    public CreateTableColumnStep createTemporaryTable(Table<?> table) {
        return context.createTemporaryTable(table);
    }

    @Override
    public CreateTableColumnStep createTemporaryTableIfNotExists(String table) {
        return context.createTemporaryTableIfNotExists(table);
    }

    @Override
    public CreateTableColumnStep createTemporaryTableIfNotExists(Name table) {
        return context.createTemporaryTableIfNotExists(table);
    }

    @Override
    public CreateTableColumnStep createTemporaryTableIfNotExists(Table<?> table) {
        return context.createTemporaryTableIfNotExists(table);
    }

    @Override
    public CreateTableColumnStep createGlobalTemporaryTable(String table) {
        return context.createGlobalTemporaryTable(table);
    }

    @Override
    public CreateTableColumnStep createGlobalTemporaryTable(Name table) {
        return context.createGlobalTemporaryTable(table);
    }

    @Override
    public CreateTableColumnStep createGlobalTemporaryTable(Table<?> table) {
        return context.createGlobalTemporaryTable(table);
    }

    @Override
    public CreateViewAsStep<Record> createView(String view, String... fields) {
        return context.createView(view, fields);
    }

    @Override
    public CreateViewAsStep<Record> createView(Name view, Name... fields) {
        return context.createView(view, fields);
    }

    @Override
    public CreateViewAsStep<Record> createView(Table<?> view, Field<?>... fields) {
        return context.createView(view, fields);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createView(String view,
            Function<? super Field<?>, ? extends String> fieldNameFunction) {
        return context.createView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createView(String view,
            BiFunction<? super Field<?>, ? super Integer, ? extends String> fieldNameFunction) {
        return context.createView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createView(Name view,
            Function<? super Field<?>, ? extends Name> fieldNameFunction) {
        return context.createView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createView(Name view,
            BiFunction<? super Field<?>, ? super Integer, ? extends Name> fieldNameFunction) {
        return context.createView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createView(Table<?> view,
            Function<? super Field<?>, ? extends Field<?>> fieldNameFunction) {
        return context.createView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createView(Table<?> view,
            BiFunction<? super Field<?>, ? super Integer, ? extends Field<?>> fieldNameFunction) {
        return context.createView(view, fieldNameFunction);
    }

    @Override
    public CreateViewAsStep<Record> createOrReplaceView(String view, String... fields) {
        return context.createOrReplaceView(view, fields);
    }

    @Override
    public CreateViewAsStep<Record> createOrReplaceView(Name view, Name... fields) {
        return context.createOrReplaceView(view, fields);
    }

    @Override
    public CreateViewAsStep<Record> createOrReplaceView(Table<?> view, Field<?>... fields) {
        return context.createOrReplaceView(view, fields);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createOrReplaceView(String view,
            Function<? super Field<?>, ? extends String> fieldNameFunction) {
        return context.createOrReplaceView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createOrReplaceView(String view,
            BiFunction<? super Field<?>, ? super Integer, ? extends String> fieldNameFunction) {
        return context.createOrReplaceView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createOrReplaceView(Name view,
            Function<? super Field<?>, ? extends Name> fieldNameFunction) {
        return context.createOrReplaceView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createOrReplaceView(Name view,
            BiFunction<? super Field<?>, ? super Integer, ? extends Name> fieldNameFunction) {
        return context.createOrReplaceView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createOrReplaceView(Table<?> view,
            Function<? super Field<?>, ? extends Field<?>> fieldNameFunction) {
        return context.createOrReplaceView(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createOrReplaceView(Table<?> view,
            BiFunction<? super Field<?>, ? super Integer, ? extends Field<?>> fieldNameFunction) {
        return context.createOrReplaceView(view, fieldNameFunction);
    }

    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(String view, String... fields) {
        return context.createViewIfNotExists(view, fields);
    }

    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(Name view, Name... fields) {
        return context.createViewIfNotExists(view, fields);
    }

    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(Table<?> view, Field<?>... fields) {
        return context.createViewIfNotExists(view, fields);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(String view,
            Function<? super Field<?>, ? extends String> fieldNameFunction) {
        return context.createViewIfNotExists(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(String view,
            BiFunction<? super Field<?>, ? super Integer, ? extends String> fieldNameFunction) {
        return context.createViewIfNotExists(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(Name view,
            Function<? super Field<?>, ? extends Name> fieldNameFunction) {
        return context.createViewIfNotExists(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(Name view,
            BiFunction<? super Field<?>, ? super Integer, ? extends Name> fieldNameFunction) {
        return context.createViewIfNotExists(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(Table<?> view,
            Function<? super Field<?>, ? extends Field<?>> fieldNameFunction) {
        return context.createViewIfNotExists(view, fieldNameFunction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public CreateViewAsStep<Record> createViewIfNotExists(Table<?> view,
            BiFunction<? super Field<?>, ? super Integer, ? extends Field<?>> fieldNameFunction) {
        return context.createViewIfNotExists(view, fieldNameFunction);
    }

    @Override
    public CreateTypeStep createType(String type) {
        return context.createType(type);
    }

    @Override
    public CreateTypeStep createType(Name type) {
        return context.createType(type);
    }

    @Override
    public AlterTypeStep alterType(String type) {
        return context.alterType(type);
    }

    @Override
    public AlterTypeStep alterType(Name type) {
        return context.alterType(type);
    }

    @Override
    public DropTypeStep dropType(String type) {
        return context.dropType(type);
    }

    @Override
    public DropTypeStep dropType(Name type) {
        return context.dropType(type);
    }

    @Override
    public DropTypeStep dropType(String... type) {
        return context.dropType(type);
    }

    @Override
    public DropTypeStep dropType(Name... type) {
        return context.dropType(type);
    }

    @Override
    public DropTypeStep dropType(Collection<?> type) {
        return context.dropType(type);
    }

    @Override
    public DropTypeStep dropTypeIfExists(String type) {
        return context.dropTypeIfExists(type);
    }

    @Override
    public DropTypeStep dropTypeIfExists(Name type) {
        return context.dropTypeIfExists(type);
    }

    @Override
    public DropTypeStep dropTypeIfExists(String... type) {
        return context.dropTypeIfExists(type);
    }

    @Override
    public DropTypeStep dropTypeIfExists(Name... type) {
        return context.dropTypeIfExists(type);
    }

    @Override
    public DropTypeStep dropTypeIfExists(Collection<?> type) {
        return context.dropTypeIfExists(type);
    }

    @Override
    public CreateIndexStep createIndex() {
        return context.createIndex();
    }

    @Override
    public CreateIndexStep createIndex(String index) {
        return context.createIndex(index);
    }

    @Override
    public CreateIndexStep createIndex(Name index) {
        return context.createIndex(index);
    }

    @Override
    public CreateIndexStep createIndex(Index index) {
        return context.createIndex(index);
    }

    @Override
    public CreateIndexStep createIndexIfNotExists(String index) {
        return context.createIndexIfNotExists(index);
    }

    @Override
    public CreateIndexStep createIndexIfNotExists(Name index) {
        return context.createIndexIfNotExists(index);
    }

    @Override
    public CreateIndexStep createIndexIfNotExists(Index index) {
        return context.createIndexIfNotExists(index);
    }

    @Override
    public CreateIndexStep createUniqueIndex() {
        return context.createUniqueIndex();
    }

    @Override
    public CreateIndexStep createUniqueIndex(String index) {
        return context.createUniqueIndex(index);
    }

    @Override
    public CreateIndexStep createUniqueIndex(Name index) {
        return context.createUniqueIndex(index);
    }

    @Override
    public CreateIndexStep createUniqueIndex(Index index) {
        return context.createUniqueIndex(index);
    }

    @Override
    public CreateIndexStep createUniqueIndexIfNotExists(String index) {
        return context.createUniqueIndexIfNotExists(index);
    }

    @Override
    public CreateIndexStep createUniqueIndexIfNotExists(Name index) {
        return context.createUniqueIndexIfNotExists(index);
    }

    @Override
    public CreateIndexStep createUniqueIndexIfNotExists(Index index) {
        return context.createUniqueIndexIfNotExists(index);
    }

    @Override
    public CreateSequenceFlagsStep createSequence(String sequence) {
        return context.createSequence(sequence);
    }

    @Override
    public CreateSequenceFlagsStep createSequence(Name sequence) {
        return context.createSequence(sequence);
    }

    @Override
    public CreateSequenceFlagsStep createSequence(Sequence<?> sequence) {
        return context.createSequence(sequence);
    }

    @Override
    public CreateSequenceFlagsStep createSequenceIfNotExists(String sequence) {
        return context.createSequenceIfNotExists(sequence);
    }

    @Override
    public CreateSequenceFlagsStep createSequenceIfNotExists(Name sequence) {
        return context.createSequenceIfNotExists(sequence);
    }

    @Override
    public CreateSequenceFlagsStep createSequenceIfNotExists(Sequence<?> sequence) {
        return context.createSequenceIfNotExists(sequence);
    }

    @Override
    public AlterDatabaseStep alterDatabase(String database) {
        return context.alterDatabase(database);
    }

    @Override
    public AlterDatabaseStep alterDatabase(Name database) {
        return context.alterDatabase(database);
    }

    @Override
    public AlterDatabaseStep alterDatabase(Catalog database) {
        return context.alterDatabase(database);
    }

    @Override
    public AlterDatabaseStep alterDatabaseIfExists(String database) {
        return context.alterDatabaseIfExists(database);
    }

    @Override
    public AlterDatabaseStep alterDatabaseIfExists(Name database) {
        return context.alterDatabaseIfExists(database);
    }

    @Override
    public AlterDatabaseStep alterDatabaseIfExists(Catalog database) {
        return context.alterDatabaseIfExists(database);
    }

    @Override
    public <T> AlterDomainStep<T> alterDomain(String domain) {
        return context.alterDomain(domain);
    }

    @Override
    public <T> AlterDomainStep<T> alterDomain(Name domain) {
        return context.alterDomain(domain);
    }

    @Override
    public <T> AlterDomainStep<T> alterDomain(Domain<T> domain) {
        return context.alterDomain(domain);
    }

    @Override
    public <T> AlterDomainStep<T> alterDomainIfExists(String domain) {
        return context.alterDomainIfExists(domain);
    }

    @Override
    public <T> AlterDomainStep<T> alterDomainIfExists(Name domain) {
        return context.alterDomainIfExists(domain);
    }

    @Override
    public <T> AlterDomainStep<T> alterDomainIfExists(Domain<T> domain) {
        return context.alterDomainIfExists(domain);
    }

    @Override
    public AlterSequenceStep<BigInteger> alterSequence(String sequence) {
        return context.alterSequence(sequence);
    }

    @Override
    public AlterSequenceStep<BigInteger> alterSequence(Name sequence) {
        return context.alterSequence(sequence);
    }

    @Override
    public <T extends Number> AlterSequenceStep<T> alterSequence(Sequence<T> sequence) {
        return context.alterSequence(sequence);
    }

    @Override
    public AlterSequenceStep<BigInteger> alterSequenceIfExists(String sequence) {
        return context.alterSequenceIfExists(sequence);
    }

    @Override
    public AlterSequenceStep<BigInteger> alterSequenceIfExists(Name sequence) {
        return context.alterSequenceIfExists(sequence);
    }

    @Override
    public <T extends Number> AlterSequenceStep<T> alterSequenceIfExists(Sequence<T> sequence) {
        return context.alterSequenceIfExists(sequence);
    }

    @Override
    public AlterTableStep alterTable(String table) {
        return context.alterTable(table);
    }

    @Override
    public AlterTableStep alterTable(Name table) {
        return context.alterTable(table);
    }

    @Override
    public AlterTableStep alterTable(Table<?> table) {
        return context.alterTable(table);
    }

    @Override
    public AlterTableStep alterTableIfExists(String table) {
        return context.alterTableIfExists(table);
    }

    @Override
    public AlterTableStep alterTableIfExists(Name table) {
        return context.alterTableIfExists(table);
    }

    @Override
    public AlterTableStep alterTableIfExists(Table<?> table) {
        return context.alterTableIfExists(table);
    }

    @Override
    public AlterSchemaStep alterSchema(String schema) {
        return context.alterSchema(schema);
    }

    @Override
    public AlterSchemaStep alterSchema(Name schema) {
        return context.alterSchema(schema);
    }

    @Override
    public AlterSchemaStep alterSchema(Schema schema) {
        return context.alterSchema(schema);
    }

    @Override
    public AlterSchemaStep alterSchemaIfExists(String schema) {
        return context.alterSchemaIfExists(schema);
    }

    @Override
    public AlterSchemaStep alterSchemaIfExists(Name schema) {
        return context.alterSchemaIfExists(schema);
    }

    @Override
    public AlterSchemaStep alterSchemaIfExists(Schema schema) {
        return context.alterSchemaIfExists(schema);
    }

    @Override
    public DropDatabaseFinalStep dropDatabase(String database) {
        return context.dropDatabase(database);
    }

    @Override
    public DropDatabaseFinalStep dropDatabase(Name database) {
        return context.dropDatabase(database);
    }

    @Override
    public DropDatabaseFinalStep dropDatabase(Catalog database) {
        return context.dropDatabase(database);
    }

    @Override
    public DropDatabaseFinalStep dropDatabaseIfExists(String database) {
        return context.dropDatabaseIfExists(database);
    }

    @Override
    public DropDatabaseFinalStep dropDatabaseIfExists(Name database) {
        return context.dropDatabaseIfExists(database);
    }

    @Override
    public DropDatabaseFinalStep dropDatabaseIfExists(Catalog database) {
        return context.dropDatabaseIfExists(database);
    }

    @Override
    public DropDomainCascadeStep dropDomain(String domain) {
        return context.dropDomain(domain);
    }

    @Override
    public DropDomainCascadeStep dropDomain(Name domain) {
        return context.dropDomain(domain);
    }

    @Override
    public DropDomainCascadeStep dropDomain(Domain<?> domain) {
        return context.dropDomain(domain);
    }

    @Override
    public DropDomainCascadeStep dropDomainIfExists(String domain) {
        return context.dropDomainIfExists(domain);
    }

    @Override
    public DropDomainCascadeStep dropDomainIfExists(Name domain) {
        return context.dropDomainIfExists(domain);
    }

    @Override
    public DropDomainCascadeStep dropDomainIfExists(Domain<?> domain) {
        return context.dropDomainIfExists(domain);
    }

    @Override
    public AlterViewStep alterView(String view) {
        return context.alterView(view);
    }

    @Override
    public AlterViewStep alterView(Name view) {
        return context.alterView(view);
    }

    @Override
    public AlterViewStep alterView(Table<?> view) {
        return context.alterView(view);
    }

    @Override
    public AlterViewStep alterViewIfExists(String view) {
        return context.alterViewIfExists(view);
    }

    @Override
    public AlterViewStep alterViewIfExists(Name view) {
        return context.alterViewIfExists(view);
    }

    @Override
    public AlterViewStep alterViewIfExists(Table<?> view) {
        return context.alterViewIfExists(view);
    }

    @Override
    public AlterIndexOnStep alterIndex(String index) {
        return context.alterIndex(index);
    }

    @Override
    public AlterIndexOnStep alterIndex(Name index) {
        return context.alterIndex(index);
    }

    @Override
    public AlterIndexOnStep alterIndex(Index index) {
        return context.alterIndex(index);
    }

    @Override
    public AlterIndexStep alterIndexIfExists(String index) {
        return context.alterIndexIfExists(index);
    }

    @Override
    public AlterIndexStep alterIndexIfExists(Name index) {
        return context.alterIndexIfExists(index);
    }

    @Override
    public AlterIndexStep alterIndexIfExists(Index index) {
        return context.alterIndexIfExists(index);
    }

    @Override
    public DropSchemaStep dropSchema(String schema) {
        return context.dropSchema(schema);
    }

    @Override
    public DropSchemaStep dropSchema(Name schema) {
        return context.dropSchema(schema);
    }

    @Override
    public DropSchemaStep dropSchema(Schema schema) {
        return context.dropSchema(schema);
    }

    @Override
    public DropSchemaStep dropSchemaIfExists(String schema) {
        return context.dropSchemaIfExists(schema);
    }

    @Override
    public DropSchemaStep dropSchemaIfExists(Name schema) {
        return context.dropSchemaIfExists(schema);
    }

    @Override
    public DropSchemaStep dropSchemaIfExists(Schema schema) {
        return context.dropSchemaIfExists(schema);
    }

    @Override
    public DropViewFinalStep dropView(String view) {
        return context.dropView(view);
    }

    @Override
    public DropViewFinalStep dropView(Name view) {
        return context.dropView(view);
    }

    @Override
    public DropViewFinalStep dropView(Table<?> view) {
        return context.dropView(view);
    }

    @Override
    public DropViewFinalStep dropViewIfExists(String view) {
        return context.dropViewIfExists(view);
    }

    @Override
    public DropViewFinalStep dropViewIfExists(Name view) {
        return context.dropViewIfExists(view);
    }

    @Override
    public DropViewFinalStep dropViewIfExists(Table<?> view) {
        return context.dropViewIfExists(view);
    }

    @Override
    public DropTableStep dropTable(String table) {
        return context.dropTable(table);
    }

    @Override
    public DropTableStep dropTable(Name table) {
        return context.dropTable(table);
    }

    @Override
    public DropTableStep dropTable(Table<?> table) {
        return context.dropTable(table);
    }

    @Override
    public DropTableStep dropTableIfExists(String table) {
        return context.dropTableIfExists(table);
    }

    @Override
    public DropTableStep dropTableIfExists(Name table) {
        return context.dropTableIfExists(table);
    }

    @Override
    public DropTableStep dropTableIfExists(Table<?> table) {
        return context.dropTableIfExists(table);
    }

    @Override
    public DropTableStep dropTemporaryTable(String table) {
        return context.dropTemporaryTable(table);
    }

    @Override
    public DropTableStep dropTemporaryTable(Name table) {
        return context.dropTemporaryTable(table);
    }

    @Override
    public DropTableStep dropTemporaryTable(Table<?> table) {
        return context.dropTemporaryTable(table);
    }

    @Override
    public DropTableStep dropTemporaryTableIfExists(String table) {
        return context.dropTemporaryTableIfExists(table);
    }

    @Override
    public DropTableStep dropTemporaryTableIfExists(Name table) {
        return context.dropTemporaryTableIfExists(table);
    }

    @Override
    public DropTableStep dropTemporaryTableIfExists(Table<?> table) {
        return context.dropTemporaryTableIfExists(table);
    }

    @Override
    public DropIndexOnStep dropIndex(String index) {
        return context.dropIndex(index);
    }

    @Override
    public DropIndexOnStep dropIndex(Name index) {
        return context.dropIndex(index);
    }

    @Override
    public DropIndexOnStep dropIndex(Index index) {
        return context.dropIndex(index);
    }

    @Override
    public DropIndexOnStep dropIndexIfExists(String index) {
        return context.dropIndexIfExists(index);
    }

    @Override
    public DropIndexOnStep dropIndexIfExists(Name index) {
        return context.dropIndexIfExists(index);
    }

    @Override
    public DropIndexOnStep dropIndexIfExists(Index index) {
        return context.dropIndexIfExists(index);
    }

    @Override
    public DropSequenceFinalStep dropSequence(String sequence) {
        return context.dropSequence(sequence);
    }

    @Override
    public DropSequenceFinalStep dropSequence(Name sequence) {
        return context.dropSequence(sequence);
    }

    @Override
    public DropSequenceFinalStep dropSequence(Sequence<?> sequence) {
        return context.dropSequence(sequence);
    }

    @Override
    public DropSequenceFinalStep dropSequenceIfExists(String sequence) {
        return context.dropSequenceIfExists(sequence);
    }

    @Override
    public DropSequenceFinalStep dropSequenceIfExists(Name sequence) {
        return context.dropSequenceIfExists(sequence);
    }

    @Override
    public DropSequenceFinalStep dropSequenceIfExists(Sequence<?> sequence) {
        return context.dropSequenceIfExists(sequence);
    }

    @Override
    public TruncateIdentityStep<Record> truncate(String table) {
        return context.truncate(table);
    }

    @Override
    public TruncateIdentityStep<Record> truncate(Name table) {
        return context.truncate(table);
    }

    @Override
    public <R extends Record> TruncateIdentityStep<R> truncate(Table<R> table) {
        return context.truncate(table);
    }

    @Override
    public TruncateIdentityStep<Record> truncateTable(String table) {
        return context.truncateTable(table);
    }

    @Override
    public TruncateIdentityStep<Record> truncateTable(Name table) {
        return context.truncateTable(table);
    }

    @Override
    public <R extends Record> TruncateIdentityStep<R> truncateTable(Table<R> table) {
        return context.truncateTable(table);
    }

    @Override
    public GrantOnStep grant(Privilege privilege) {
        return context.grant(privilege);
    }

    @Override
    public GrantOnStep grant(Privilege... privileges) {
        return context.grant(privileges);
    }

    @Override
    public GrantOnStep grant(Collection<? extends Privilege> privileges) {
        return context.grant(privileges);
    }

    @Override
    public RevokeOnStep revoke(Privilege privilege) {
        return context.revoke(privilege);
    }

    @Override
    public RevokeOnStep revoke(Privilege... privileges) {
        return context.revoke(privileges);
    }

    @Override
    public RevokeOnStep revoke(Collection<? extends Privilege> privileges) {
        return context.revoke(privileges);
    }

    @Override
    public RevokeOnStep revokeGrantOptionFor(Privilege privilege) {
        return context.revokeGrantOptionFor(privilege);
    }

    @Override
    public RevokeOnStep revokeGrantOptionFor(Privilege... privileges) {
        return context.revokeGrantOptionFor(privileges);
    }

    @Override
    public RevokeOnStep revokeGrantOptionFor(Collection<? extends Privilege> privileges) {
        return context.revokeGrantOptionFor(privileges);
    }

    @Override
    public BigInteger lastID() throws DataAccessException {
        return context.lastID();
    }

    @Override
    public BigInteger nextval(String sequence) throws DataAccessException {
        return context.nextval(sequence);
    }

    @Override
    public BigInteger nextval(Name sequence) throws DataAccessException {
        return context.nextval(sequence);
    }

    @Override
    public <T extends Number> T nextval(Sequence<T> sequence) throws DataAccessException {
        return context.nextval(sequence);
    }

    @Override
    public <T extends Number> List<T> nextvals(Sequence<T> sequence, int size) throws DataAccessException {
        return context.nextvals(sequence, size);
    }

    @Override
    public BigInteger currval(String sequence) throws DataAccessException {
        return context.currval(sequence);
    }

    @Override
    public BigInteger currval(Name sequence) throws DataAccessException {
        return context.currval(sequence);
    }

    @Override
    public <T extends Number> T currval(Sequence<T> sequence) throws DataAccessException {
        return context.currval(sequence);
    }

    @Override
    public <R extends UDTRecord<R>> R newRecord(UDT<R> type) {
        return context.newRecord(type);
    }

    @Override
    public <R extends Record> R newRecord(Table<R> table) {
        return context.newRecord(table);
    }

    @Override
    public <R extends Record> R newRecord(Table<R> table, Object source) {
        return context.newRecord(table, source);
    }

    @Override
    public Record newRecord(Field<?>... fields) {
        return context.newRecord(fields);
    }

    @Override
    public Record newRecord(Collection<? extends Field<?>> fields) {
        return context.newRecord(fields);
    }

    @Override
    public <T1> Record1<T1> newRecord(Field<T1> field1) {
        return context.newRecord(field1);
    }

    @Override
    public <T1, T2> Record2<T1, T2> newRecord(Field<T1> field1, Field<T2> field2) {
        return context.newRecord(field1, field2);
    }

    @Override
    public <T1, T2, T3> Record3<T1, T2, T3> newRecord(Field<T1> field1, Field<T2> field2, Field<T3> field3) {
        return context.newRecord(field1, field2, field3);
    }

    @Override
    public <T1, T2, T3, T4> Record4<T1, T2, T3, T4> newRecord(Field<T1> field1, Field<T2> field2, Field<T3> field3,
            Field<T4> field4) {
        return context.newRecord(field1, field2, field3, field4);
    }

    @Override
    public <T1, T2, T3, T4, T5> Record5<T1, T2, T3, T4, T5> newRecord(Field<T1> field1, Field<T2> field2,
            Field<T3> field3, Field<T4> field4, Field<T5> field5) {
        return context.newRecord(field1, field2, field3, field4, field5);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> Record6<T1, T2, T3, T4, T5, T6> newRecord(Field<T1> field1, Field<T2> field2,
            Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6) {
        return context.newRecord(field1, field2, field3, field4, field5, field6);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> Record7<T1, T2, T3, T4, T5, T6, T7> newRecord(Field<T1> field1,
            Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> Record8<T1, T2, T3, T4, T5, T6, T7, T8> newRecord(Field<T1> field1,
            Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6, Field<T7> field7,
            Field<T8> field8) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9> newRecord(Field<T1> field1,
            Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6, Field<T7> field7,
            Field<T8> field8, Field<T9> field9) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> Record21<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20, Field<T21> field21) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> Record22<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> newRecord(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20, Field<T21> field21,
            Field<T22> field22) {
        return context.newRecord(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21,
                field22);
    }

    @Override
    public <R extends Record> Result<R> newResult(Table<R> table) {
        return context.newResult(table);
    }

    @Override
    public Result<Record> newResult(Field<?>... fields) {
        return context.newResult(fields);
    }

    @Override
    public Result<Record> newResult(Collection<? extends Field<?>> fields) {
        return context.newResult(fields);
    }

    @Override
    public <T1> Result<Record1<T1>> newResult(Field<T1> field1) {
        return context.newResult(field1);
    }

    @Override
    public <T1, T2> Result<Record2<T1, T2>> newResult(Field<T1> field1, Field<T2> field2) {
        return context.newResult(field1, field2);
    }

    @Override
    public <T1, T2, T3> Result<Record3<T1, T2, T3>> newResult(Field<T1> field1, Field<T2> field2, Field<T3> field3) {
        return context.newResult(field1, field2, field3);
    }

    @Override
    public <T1, T2, T3, T4> Result<Record4<T1, T2, T3, T4>> newResult(Field<T1> field1, Field<T2> field2,
            Field<T3> field3, Field<T4> field4) {
        return context.newResult(field1, field2, field3, field4);
    }

    @Override
    public <T1, T2, T3, T4, T5> Result<Record5<T1, T2, T3, T4, T5>> newResult(Field<T1> field1, Field<T2> field2,
            Field<T3> field3, Field<T4> field4, Field<T5> field5) {
        return context.newResult(field1, field2, field3, field4, field5);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> Result<Record6<T1, T2, T3, T4, T5, T6>> newResult(Field<T1> field1,
            Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6) {
        return context.newResult(field1, field2, field3, field4, field5, field6);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> Result<Record7<T1, T2, T3, T4, T5, T6, T7>> newResult(Field<T1> field1,
            Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> Result<Record8<T1, T2, T3, T4, T5, T6, T7, T8>> newResult(Field<T1> field1,
            Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6, Field<T7> field7,
            Field<T8> field8) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> Result<Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Result<Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> Result<Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> Result<Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> Result<Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> Result<Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> Result<Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> Result<Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> Result<Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> Result<Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> Result<Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> Result<Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> Result<Record21<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20, Field<T21> field21) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> Result<Record22<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22>> newResult(
            Field<T1> field1, Field<T2> field2, Field<T3> field3, Field<T4> field4, Field<T5> field5, Field<T6> field6,
            Field<T7> field7, Field<T8> field8, Field<T9> field9, Field<T10> field10, Field<T11> field11,
            Field<T12> field12, Field<T13> field13, Field<T14> field14, Field<T15> field15, Field<T16> field16,
            Field<T17> field17, Field<T18> field18, Field<T19> field19, Field<T20> field20, Field<T21> field21,
            Field<T22> field22) {
        return context.newResult(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21,
                field22);
    }

    @Override
    public <R extends Record> Result<R> fetch(ResultQuery<R> query) throws DataAccessException {
        return context.fetch(query);
    }

    @Override
    public <R extends Record> Cursor<R> fetchLazy(ResultQuery<R> query) throws DataAccessException {
        return context.fetchLazy(query);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(ResultQuery<R> query) {
        return context.fetchAsync(query);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Executor executor, ResultQuery<R> query) {
        return context.fetchAsync(executor, query);
    }

    @Override
    public <R extends Record> Stream<R> fetchStream(ResultQuery<R> query) throws DataAccessException {
        return context.fetchStream(query);
    }

    @Override
    public <R extends Record> Results fetchMany(ResultQuery<R> query) throws DataAccessException {
        return context.fetchMany(query);
    }

    @Override
    public <R extends Record> R fetchOne(ResultQuery<R> query) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(query);
    }

    @Override
    public <R extends Record> R fetchSingle(ResultQuery<R> query)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(query);
    }

    @Override
    public <R extends Record> Optional<R> fetchOptional(ResultQuery<R> query)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(query);
    }

    @Override
    public <T> T fetchValue(Table<? extends Record1<T>> table) throws DataAccessException, TooManyRowsException {
        return context.fetchValue(table);
    }

    @Override
    public <T, R extends Record1<T>> T fetchValue(ResultQuery<R> query)
            throws DataAccessException, TooManyRowsException {
        return context.fetchValue(query);
    }

    @Override
    public <T> T fetchValue(TableField<?, T> field) throws DataAccessException, TooManyRowsException {
        return context.fetchValue(field);
    }

    @Override
    public <T> T fetchValue(Field<T> field) throws DataAccessException {
        return context.fetchValue(field);
    }

    @Override
    public <T, R extends Record1<T>> Optional<T> fetchOptionalValue(ResultQuery<R> query)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(query);
    }

    @Override
    public <T> Optional<T> fetchOptionalValue(TableField<?, T> field)
            throws DataAccessException, TooManyRowsException, InvalidResultException {
        return context.fetchOptionalValue(field);
    }

    @Override
    public <T> List<T> fetchValues(Table<? extends Record1<T>> table) throws DataAccessException {
        return context.fetchValues(table);
    }

    @Override
    public <T, R extends Record1<T>> List<T> fetchValues(ResultQuery<R> query) throws DataAccessException {
        return context.fetchValues(query);
    }

    @Override
    public <T> List<T> fetchValues(TableField<?, T> field) throws DataAccessException {
        return context.fetchValues(field);
    }

    @Override
    public <R extends TableRecord<R>> Result<R> fetchByExample(R example) throws DataAccessException {
        return context.fetchByExample(example);
    }

    @Override
    public int fetchCount(Select<?> query) throws DataAccessException {
        return context.fetchCount(query);
    }

    @Override
    public int fetchCount(Table<?> table) throws DataAccessException {
        return context.fetchCount(table);
    }

    @Override
    public int fetchCount(Table<?> table, Condition condition) throws DataAccessException {
        return context.fetchCount(table, condition);
    }

    @Override
    public int fetchCount(Table<?> table, Condition... conditions) throws DataAccessException {
        return context.fetchCount(table, conditions);
    }

    @Override
    public int fetchCount(Table<?> table, Collection<? extends Condition> conditions) throws DataAccessException {
        return context.fetchCount(table, conditions);
    }

    @Override
    public boolean fetchExists(Select<?> query) throws DataAccessException {
        return context.fetchExists(query);
    }

    @Override
    public boolean fetchExists(Table<?> table) throws DataAccessException {
        return context.fetchExists(table);
    }

    @Override
    public boolean fetchExists(Table<?> table, Condition condition) throws DataAccessException {
        return context.fetchExists(table, condition);
    }

    @Override
    public boolean fetchExists(Table<?> table, Condition... conditions) throws DataAccessException {
        return context.fetchExists(table, conditions);
    }

    @Override
    public boolean fetchExists(Table<?> table, Collection<? extends Condition> conditions) throws DataAccessException {
        return context.fetchExists(table, conditions);
    }

    @Override
    public int execute(Query query) throws DataAccessException {
        return context.execute(query);
    }

    @Override
    public <R extends Record> Result<R> fetch(Table<R> table) throws DataAccessException {
        return context.fetch(table);
    }

    @Override
    public <R extends Record> Result<R> fetch(Table<R> table, Condition condition) throws DataAccessException {
        return context.fetch(table, condition);
    }

    @Override
    public <R extends Record> Result<R> fetch(Table<R> table, Condition... conditions) throws DataAccessException {
        return context.fetch(table, conditions);
    }

    @Override
    public <R extends Record> Result<R> fetch(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException {
        return context.fetch(table, conditions);
    }

    @Override
    public <R extends Record> R fetchOne(Table<R> table) throws DataAccessException, TooManyRowsException {
        return context.fetchOne(table);
    }

    @Override
    public <R extends Record> R fetchOne(Table<R> table, Condition condition)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOne(table, condition);
    }

    @Override
    public <R extends Record> R fetchOne(Table<R> table, Condition... conditions)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOne(table, conditions);
    }

    @Override
    public <R extends Record> R fetchOne(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOne(table, conditions);
    }

    @Override
    public <R extends Record> R fetchSingle(Table<R> table)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(table);
    }

    @Override
    public <R extends Record> R fetchSingle(Table<R> table, Condition condition)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(table, condition);
    }

    @Override
    public <R extends Record> R fetchSingle(Table<R> table, Condition... conditions)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(table, conditions);
    }

    @Override
    public <R extends Record> R fetchSingle(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException, NoDataFoundException, TooManyRowsException {
        return context.fetchSingle(table, conditions);
    }

    @Override
    public Record fetchSingle(SelectField<?>... fields) throws DataAccessException {
        return context.fetchSingle(fields);
    }

    @Override
    public Record fetchSingle(Collection<? extends SelectField<?>> fields) throws DataAccessException {
        return context.fetchSingle(fields);
    }

    @Override
    public <T1> Record1<T1> fetchSingle(SelectField<T1> field1) throws DataAccessException {
        return context.fetchSingle(field1);
    }

    @Override
    public <T1, T2> Record2<T1, T2> fetchSingle(SelectField<T1> field1, SelectField<T2> field2)
            throws DataAccessException {
        return context.fetchSingle(field1, field2);
    }

    @Override
    public <T1, T2, T3> Record3<T1, T2, T3> fetchSingle(SelectField<T1> field1, SelectField<T2> field2,
            SelectField<T3> field3) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3);
    }

    @Override
    public <T1, T2, T3, T4> Record4<T1, T2, T3, T4> fetchSingle(SelectField<T1> field1, SelectField<T2> field2,
            SelectField<T3> field3, SelectField<T4> field4) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4);
    }

    @Override
    public <T1, T2, T3, T4, T5> Record5<T1, T2, T3, T4, T5> fetchSingle(SelectField<T1> field1, SelectField<T2> field2,
            SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6> Record6<T1, T2, T3, T4, T5, T6> fetchSingle(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5,
            SelectField<T6> field6) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7> Record7<T1, T2, T3, T4, T5, T6, T7> fetchSingle(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5,
            SelectField<T6> field6, SelectField<T7> field7) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8> Record8<T1, T2, T3, T4, T5, T6, T7, T8> fetchSingle(SelectField<T1> field1,
            SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4, SelectField<T5> field5,
            SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9> Record9<T1, T2, T3, T4, T5, T6, T7, T8, T9> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Record10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> Record11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> Record12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12)
            throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> Record13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> Record14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> Record15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> Record16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16)
            throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> Record17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> Record18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> Record19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> Record20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20)
            throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> Record21<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20,
            SelectField<T21> field21) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21);
    }

    @Override
    public <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> Record22<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> fetchSingle(
            SelectField<T1> field1, SelectField<T2> field2, SelectField<T3> field3, SelectField<T4> field4,
            SelectField<T5> field5, SelectField<T6> field6, SelectField<T7> field7, SelectField<T8> field8,
            SelectField<T9> field9, SelectField<T10> field10, SelectField<T11> field11, SelectField<T12> field12,
            SelectField<T13> field13, SelectField<T14> field14, SelectField<T15> field15, SelectField<T16> field16,
            SelectField<T17> field17, SelectField<T18> field18, SelectField<T19> field19, SelectField<T20> field20,
            SelectField<T21> field21, SelectField<T22> field22) throws DataAccessException {
        return context.fetchSingle(field1, field2, field3, field4, field5, field6, field7, field8, field9, field10,
                field11, field12, field13, field14, field15, field16, field17, field18, field19, field20, field21,
                field22);
    }

    @Override
    public <R extends Record> Optional<R> fetchOptional(Table<R> table)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(table);
    }

    @Override
    public <R extends Record> Optional<R> fetchOptional(Table<R> table, Condition condition)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(table, condition);
    }

    @Override
    public <R extends Record> Optional<R> fetchOptional(Table<R> table, Condition... conditions)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(table, conditions);
    }

    @Override
    public <R extends Record> Optional<R> fetchOptional(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException, TooManyRowsException {
        return context.fetchOptional(table, conditions);
    }

    @Override
    public <R extends Record> R fetchAny(Table<R> table) throws DataAccessException {
        return context.fetchAny(table);
    }

    @Override
    public <R extends Record> R fetchAny(Table<R> table, Condition condition) throws DataAccessException {
        return context.fetchAny(table, condition);
    }

    @Override
    public <R extends Record> R fetchAny(Table<R> table, Condition... conditions) throws DataAccessException {
        return context.fetchAny(table, conditions);
    }

    @Override
    public <R extends Record> R fetchAny(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException {
        return context.fetchAny(table, conditions);
    }

    @Override
    public <R extends Record> Cursor<R> fetchLazy(Table<R> table) throws DataAccessException {
        return context.fetchLazy(table);
    }

    @Override
    public <R extends Record> Cursor<R> fetchLazy(Table<R> table, Condition condition) throws DataAccessException {
        return context.fetchLazy(table, condition);
    }

    @Override
    public <R extends Record> Cursor<R> fetchLazy(Table<R> table, Condition... conditions) throws DataAccessException {
        return context.fetchLazy(table, conditions);
    }

    @Override
    public <R extends Record> Cursor<R> fetchLazy(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException {
        return context.fetchLazy(table, conditions);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Table<R> table) {
        return context.fetchAsync(table);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Table<R> table, Condition condition) {
        return context.fetchAsync(table, condition);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Table<R> table, Condition... condition) {
        return context.fetchAsync(table, condition);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Table<R> table,
            Collection<? extends Condition> condition) {
        return context.fetchAsync(table, condition);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Executor executor, Table<R> table) {
        return context.fetchAsync(executor, table);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Executor executor, Table<R> table,
            Condition condition) {
        return context.fetchAsync(executor, table, condition);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Executor executor, Table<R> table,
            Condition... conditions) {
        return context.fetchAsync(executor, table, conditions);
    }

    @Override
    public <R extends Record> CompletionStage<Result<R>> fetchAsync(Executor executor, Table<R> table,
            Collection<? extends Condition> conditions) {
        return context.fetchAsync(executor, table, conditions);
    }

    @Override
    public <R extends Record> Stream<R> fetchStream(Table<R> table) throws DataAccessException {
        return context.fetchStream(table);
    }

    @Override
    public <R extends Record> Stream<R> fetchStream(Table<R> table, Condition condition) throws DataAccessException {
        return context.fetchStream(table, condition);
    }

    @Override
    public <R extends Record> Stream<R> fetchStream(Table<R> table, Condition... conditions)
            throws DataAccessException {
        return context.fetchStream(table, conditions);
    }

    @Override
    public <R extends Record> Stream<R> fetchStream(Table<R> table, Collection<? extends Condition> conditions)
            throws DataAccessException {
        return context.fetchStream(table, conditions);
    }

    @Override
    public int executeInsert(TableRecord<?> record) throws DataAccessException {
        return context.executeInsert(record);
    }

    @Override
    public int executeUpdate(UpdatableRecord<?> record) throws DataAccessException {
        return context.executeUpdate(record);
    }

    @Override
    public int executeUpdate(TableRecord<?> record, Condition condition) throws DataAccessException {
        return context.executeUpdate(record, condition);
    }

    @Override
    public int executeDelete(UpdatableRecord<?> record) throws DataAccessException {
        return context.executeDelete(record);
    }

    @Override
    public int executeDelete(TableRecord<?> record, Condition condition) throws DataAccessException {
        return context.executeDelete(record, condition);
    }

    @Override
    public Configuration configuration() {
        return context.configuration();
    }

    @Override
    public DSLContext dsl() {
        return context.dsl();
    }

    @Override
    public Settings settings() {
        return context.settings();
    }

    @Override
    public SQLDialect dialect() {
        return context.dialect();
    }

    @Override
    public SQLDialect family() {
        return context.family();
    }

    @Override
    public Map<Object, Object> data() {
        return context.data();
    }

    @Override
    public Object data(Object key) {
        return context.data(key);
    }

    @Override
    public Object data(Object key, Object value) {
        return context.data(key, value);
    }

}
