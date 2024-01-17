package com.projectgalen.lib.jpa.utils.base;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: _HibernateUtils.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: June 30, 2023
//
// Copyright © 2023 Project Galen. All rights reserved.
//
// Permission to use, copy, modify, and distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
// IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
// ===========================================================================

import com.projectgalen.lib.jpa.utils.enums.JpaState;
import com.projectgalen.lib.jpa.utils.errors.DaoException;
import com.projectgalen.lib.jpa.utils.interfaces.QueryConsumer;
import com.projectgalen.lib.jpa.utils.interfaces.QueryFunction;
import com.projectgalen.lib.jpa.utils.interfaces.SessionConsumer;
import com.projectgalen.lib.jpa.utils.interfaces.SessionFunction;
import com.projectgalen.lib.utils.PGResourceBundle;
import com.projectgalen.lib.utils.concurrency.Locks;
import com.projectgalen.lib.utils.helpers.Null;
import com.projectgalen.lib.utils.helpers.U;
import com.projectgalen.lib.utils.streams.Streams;
import com.projectgalen.lib.utils.text.Text;
import jakarta.persistence.Id;
import jakarta.persistence.TypedQuery;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;
import static com.projectgalen.lib.utils.reflection.Reflection2.getAnnotatedFields;

@SuppressWarnings({ "unchecked", "rawtypes", "unused", "UnusedReturnValue" })
public class Utils {
    public static final PGResourceBundle msgs        = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");
    public static final String           NULL_PK_TAG = "☠︎";

    private static final ScheduledExecutorService                              EXECUTOR     = Executors.newSingleThreadScheduledExecutor();
    private static final List<JpaBase<?>>                                      DIRTY_LIST   = new ArrayList<>();
    private static final Map<Class<?>, Map<String, WeakReference<JpaBase<?>>>> ENTITY_CACHE = new HashMap<>();
    private static final ReferenceQueue<JpaBase<?>>                            REF_QUEUE    = new ReferenceQueue<>();
    private static final Lock                                                  LOCK         = new ReentrantLock(true);

    public Utils() { }

    public static @NotNull String getFieldGetterName(@NotNull Field f) {
        return String.format("get%s", Text.capitalize(f.getName()));
    }

    public static boolean isJpaBaseField(@NotNull Field f) {
        return JpaBase.class.isAssignableFrom(f.getType());
    }

    public static <T> Optional<T> opt(@Nullable T val) {
        return Optional.ofNullable(val);
    }

    /**
     * Write all NEW and DIRTY entities to, and remove all DELETED entities from, the persistent store.
     *
     * @param session The JPA session to use.
     */
    public static void saveAll(@NotNull Session session) {
        doLocked(() -> {
            Map<JpaState, List<JpaBase<?>>> m = DIRTY_LIST.stream().collect(Collectors.groupingBy(e -> e.jpaState));

            Null.doIfNotNull(m.get(NEW), l -> l.forEach(e -> saveNew(session, e)));
            Null.doIfNotNull(m.get(DIRTY), l -> l.stream().peek(session::merge).forEach(e -> e.jpaState = CURRENT));
            Null.doIfNotNull(m.get(DELETED), l -> l.stream().peek(session::remove).forEach(Utils::removeFromCache));

            session.flush();

            Null.doIfNotNull(m.get(NEW), l -> l.stream().peek(session::refresh).peek(Utils::addToCache).forEach(JpaBase::fireUpdatedEvent));
            Null.doIfNotNull(m.get(DIRTY), l -> l.stream().peek(session::refresh).forEach(JpaBase::fireUpdatedEvent));
            Null.doIfNotNull(m.get(DELETED), l -> l.forEach(JpaBase::fireUpdatedEvent));

            DIRTY_LIST.clear();
        });
    }

    public static <E extends JpaBase<E>> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, @NotNull String ql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        if(Text.startsWithIgnoreCase(ql, "where")) ql = getFromClause(cls) + " " + ql;
        if(Text.startsWithIgnoreCase(ql, "from")) return fetchFromCache(session, cls, ql, params, startingRow, maxRows);
        return withQueryGet(session, cls, ql, params, startingRow, maxRows, (s, q) -> q.getResultStream()).peek(Utils::initialize).map(e -> locked(() -> replaceWithCached(e)));
    }

    /**
     * Executes the query and return a stream of the results. <b>NOTE:</b> Do not close the session until you are done with the stream.
     *
     * @param session  The JPA session to use.
     * @param cls      The type of the return value.
     * @param ql       The query.
     * @param prms     The parameters.
     * @param startRow The starting row.
     * @param maxRows  The maximum number of rows. (0 (zero) means return all rows.)
     *
     * @return A stream of type E.
     */
    public static <E> @NotNull Stream<E> streamObj(@NotNull Session session, @NotNull Class<E> cls, @NotNull String ql, @NotNull Map<String, Object> prms, int startRow, int maxRows) {
        return (isJpaClass(cls)
                ? (Stream<E>)stream(session, (Class<JpaBase>)cls, ql.trim(), prms, startRow, maxRows)
                : withQueryGet(session, cls, ql, prms, startRow, maxRows, (s, q) -> q.getResultStream()));
    }

    public static <E> void withQueryDo(@NotNull Session session, @NotNull Class<E> cls, @NotNull String ql, @NotNull Map<String, Object> params, int startingRow, int maxRows, @NotNull QueryConsumer<E> consumer) {
        consumer.accept(session, createQuery(session, cls, ql, params, startingRow, maxRows));
    }

    public static <E, R> R withQueryGet(@NotNull Session session, @NotNull Class<E> cls, @NotNull String ql, @NotNull Map<String, Object> params, int startingRow, int maxRows, @NotNull QueryFunction<E, R> function) {
        return function.apply(session, createQuery(session, cls, ql, params, startingRow, maxRows));
    }

    public static void withSessionDo(@NotNull SessionConsumer executable) {
        withSessionGet(executable);
    }

    public static <R> R withSessionGet(@NotNull SessionFunction<R> consumer) {
        try(Session session = HibernateSessionFactory.sessionFactory.openSession()) {
            Transaction tx = null;
            try {
                tx = session.beginTransaction();
                R val = consumer.apply(session);
                session.flush();
                tx.commit();
                return val;
            }
            catch(Exception e) {
                if(tx != null) tx.rollback();
                throw new DaoException(e);
            }
        }
    }

    private static <E> TypedQuery<E> createQuery(@NotNull Session session, @NotNull Class<E> cls, @NotNull String ql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        TypedQuery<E> query = session.createQuery(ql, cls);
        params.forEach(query::setParameter);
        if(startingRow > 0) query.setFirstResult(startingRow);
        if(maxRows > 0) query.setMaxResults(maxRows);
        return query;
    }

    private static <E extends JpaBase<?>> @NotNull Stream<E> fetchFromCache(@NotNull Session session, @NotNull Class<E> cls, @NotNull String ql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        List<String> fields = getPkFieldNames(cls);
        Stream<List> list   = withQueryGet(session, List.class, String.format("select new list(%s) %s", String.join(", ", fields), ql), params, startingRow, maxRows, (s, q) -> q.getResultStream());
        return list.peek(Utils::initialize).map(idList -> fetchFromCache(session, cls, fields, idList));
    }

    private static <E extends JpaBase<?>> @Nullable E fetchFromCache(@NotNull Session session, @NotNull Class<E> cls, @NotNull List<String> fields, @NotNull List idList) {
        if(fields.size() != idList.size()) throw new DaoException(msgs.format("msg.err.fields_values_count_mismatch", fields.size(), idList.size()));
        String pkey = ((List<Object>)idList).stream().map(o -> Objects.toString(o, NULL_PK_TAG)).collect(Collectors.joining("][", String.format("%s[", cls.getSimpleName()), "]"));
        return opt(locked(() -> getCached(cls, pkey))).orElseGet(() -> fetchFromDatabase(session, cls, fields, idList));
    }

    private static <E extends JpaBase<?>> @Nullable E fetchFromDatabase(@NotNull Session session, @NotNull Class<E> cls, @NotNull List<String> fields, @NotNull List ids) {
        Map<String, Object> prms = Streams.listStream((List<Object>)ids).collect(Collectors.toMap(e -> String.format("v%d", e.index), e -> e.item));
        String              ql   = getFromClause(cls) + getPkWhereClause(fields);
        return locked(() -> withQueryGet(session, cls, ql, prms, 0, 1, (s, q) -> q.getResultStream().peek(Utils::initialize).peek(Utils::addToCache).findFirst().orElse(null)));
    }

    private static <E extends JpaBase<?>> E getCached(@NotNull Class<E> cls, @NotNull String pkey) {
        return Null.getIfNotNull(ENTITY_CACHE.get(cls), m -> (E)Null.getIfNotNull(m.get(pkey), Reference::get));
    }

    private static String getFromClause(@NotNull Class<?> cls) {
        return String.format("from %s e", cls.getSimpleName());
    }

    private static <E extends JpaBase<?>> @NotNull List<String> getPkFieldNames(@NotNull Class<E> cls) {
        return getAnnotatedFields(cls, Id.class).map(Field::getName).collect(Collectors.toList());
    }

    private static String getPkWhereClause(@NotNull List<String> fields) {
        return Streams.listStream(fields).map(e -> String.format("(e.%s = :v%d)", e.item, e.index)).collect(Collectors.joining(" and ", " where ", ""));
    }

    private static void initialize(Object entity) {
        if(!Hibernate.isInitialized(entity)) Hibernate.initialize(entity);
    }

    private static boolean isJpaClass(@NotNull Class<?> cls) {
        return JpaBase.class.isAssignableFrom(cls);
    }

    private static void saveNew(@NotNull Session session, @NotNull JpaBase<?> entity) {
        entity.getToOneStream().filter(e -> (e.jpaState == NEW)).forEach(e -> saveNew(session, e));
        session.persist(entity);
        entity.jpaState = CURRENT;
    }

    static <E extends JpaBase<?>> @NotNull E addToCache(@NotNull JpaBase<?> e) {
        if(U.isObjIn(e.getJpaState(), CURRENT, DIRTY)) ENTITY_CACHE.computeIfAbsent(e.getClass(), k -> new TreeMap<>()).put(e.getPKey(), new WeakReference<>(e));
        return (E)e;
    }

    static void addToDirtyList(@NotNull JpaBase<?> e) {
        if(DIRTY_LIST.stream().noneMatch(o -> (e == o))) DIRTY_LIST.add(e);
    }

    static void doLocked(@NotNull Runnable runnable) {
        Locks.doWithLock(LOCK, runnable);
    }

    static <E> E locked(@NotNull Supplier<E> supplier) {
        return Locks.getWithLock(LOCK, supplier);
    }

    static void removeFromCache(@NotNull JpaBase<?> e) {
        ENTITY_CACHE.values().forEach(m -> m.values().removeIf(o -> (o.get() == e)));
    }

    static void removeFromDirtyList(JpaBase<?> e) {
        DIRTY_LIST.removeIf(o -> (e == o));
    }

    static <E extends JpaBase<E>> E replaceWithCached(@NotNull JpaBase<?> entity) {
        return opt((E)getCached(entity.getClass(), entity.getPKey())).orElseGet(() -> addToCache(entity));
    }

    private static final class HibernateSessionFactory {
        private static final SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
    }

    static {
        EXECUTOR.scheduleAtFixedRate(() -> doLocked(() -> ENTITY_CACHE.values().forEach(m -> m.values().removeIf(w -> Objects.isNull(w.get())))), 1, 1, TimeUnit.MINUTES);
    }
}
