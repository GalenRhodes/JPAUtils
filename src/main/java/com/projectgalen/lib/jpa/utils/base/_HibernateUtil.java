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

import com.projectgalen.lib.jpa.utils.HibernateUtil;
import com.projectgalen.lib.jpa.utils.errors.DaoException;
import com.projectgalen.lib.jpa.utils.interfaces.SessionConsumer;
import com.projectgalen.lib.jpa.utils.interfaces.VoidSessionConsumer;
import com.projectgalen.lib.utils.Null;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import com.projectgalen.lib.utils.concurrency.Locks;
import jakarta.persistence.Id;
import jakarta.persistence.TypedQuery;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.projectgalen.lib.utils.reflection.Reflection2.getAnnotatedFields;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class _HibernateUtil {
    public static final PGProperties       props       = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    public static final PGResourceBundle   msgs        = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");
    public static final String @NotNull [] STRNA       = new String[0];
    public static final Object @NotNull [] OBJNA       = new Object[0];
    public static final String             NULL_PK_TAG = "☠︎";

    private static final Map<String, WeakReference<JpaBase<?>>> ENTITY_CACHE   = new TreeMap<>();
    private static final ScheduledExecutorService               EXECUTOR       = Executors.newSingleThreadScheduledExecutor();
    private static final ReferenceQueue<JpaBase<?>>             referenceQueue = new ReferenceQueue<>();
    private static final Lock                                   lock           = new ReentrantLock(true);
    private static final List<JpaBase<?>>                       DIRTY_LIST     = new ArrayList<>();

    public _HibernateUtil() { }

    public static void addToCache(JpaBase<?> e) {
        Locks.doWithLock(lock, () -> ENTITY_CACHE.put(e.getPKey(), new WeakReference<>(e, referenceQueue)));
    }

    public static void addToDirtyList(JpaBase<?> e) {
        Locks.doWithLock(lock, () -> { if(DIRTY_LIST.stream().noneMatch(_e -> (_e == e))) DIRTY_LIST.add(e); });
    }

    public static void removeFromCache(@NotNull JpaBase<?> e) {
        String pKey = e.getPKey();
        Locks.doWithLock(lock, () -> Null.doIfNotNull(ENTITY_CACHE.get(pKey), c -> { if(c.get() == e) ENTITY_CACHE.remove(pKey); }));
    }

    public static void removeFromDirtyList(JpaBase<?> e) {
        Locks.doWithLock(lock, () -> DIRTY_LIST.removeIf(e0 -> (e0 == e)));
    }

    public static void saveAll(@NotNull Session session) {
        List<JpaBase<?>> allList = getAndClearDirtyList();
        List<JpaBase<?>> newList = allList.stream().filter(JpaBase::isNew).collect(Collectors.toList());

        allList.forEach(e -> e._saveChanges(session));
        session.flush();
        allList.stream().filter(JpaBase::isCurrent).forEach(session::refresh);
        newList.forEach(_HibernateUtil::addToCache);
        allList.forEach(JpaBase::fireUpdatedEvent);
    }

    public static <E> @NotNull Stream<E> stream(@NotNull Session session, @NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        hql = hql.trim();

        if(hql.length() > 5) {
            if(JpaBase.class.isAssignableFrom(clazz) && hql.substring(0, 5).equalsIgnoreCase("where")) hql = String.format("from %s e %s", clazz.getSimpleName(), hql);

            if(JpaBase.class.isAssignableFrom(clazz) && hql.substring(0, 4).equalsIgnoreCase("from")) {
                List<String>     flds  = getAnnotatedFields(clazz, Id.class).map(Field::getName).collect(Collectors.toList());
                TypedQuery<List> query = session.createQuery(String.format("select new list(%s) %s", String.join(", ", flds), hql), List.class);

                if(startingRow > 0) query.setFirstResult(startingRow);
                if(maxRows > 0) query.setMaxResults(maxRows);

                params.forEach(query::setParameter);
                return query.getResultStream().map(ids -> (E)fetchFromCache(session, (Class<? extends JpaBase<?>>)clazz, flds, ids));
            }
        }

        return queryStream(session, clazz, hql, params, startingRow, maxRows);
    }

    public static void withSessionDo(@NotNull VoidSessionConsumer executable) {
        withSessionGet(executable);
    }

    public static <R> R withSessionGet(@NotNull SessionConsumer<R> consumer) {
        try(Session session = HibernateSessionFactory.sessionFactory.openSession()) {
            Transaction tx = null;
            try {
                tx = session.beginTransaction();
                R val = consumer.getWithSession(session);
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

    private static <E extends JpaBase<?>> @Nullable E fetchFromCache(@NotNull Session session, @NotNull Class<E> clazz, @NotNull List<String> fields, @NotNull List ids) {
        if(fields.size() != ids.size()) throw new DaoException(msgs.format("msg.err.fields_values_count_mismatch", fields.size(), ids.size()));

        String pkey   = ((List<Object>)ids).stream().map(o -> Objects.toString(o, NULL_PK_TAG)).collect(Collectors.joining("][", String.format("%s[", clazz.getSimpleName()), "]"));
        E      entity = (E)getCached(pkey);

        return ((entity == null) ? fetchFromDatabase(session, clazz, fields, ids) : entity);
    }

    private static <E extends JpaBase<?>> @Nullable E fetchFromDatabase(@NotNull Session session, @NotNull Class<E> clazz, @NotNull List<String> fields, @NotNull List ids) {
        StringBuilder       sb   = new StringBuilder().append(String.format("from %s e where ", clazz.getSimpleName()));
        Map<String, Object> prms = new TreeMap<>();

        IntStream.range(0, fields.size()).forEach(i -> {
            if(i > 0) sb.append(" and ");
            sb.append(String.format("e.%s = :v%d", fields.get(i), i));
            prms.put(String.format("v%d", i), ids.get(i));
        });

        return queryStream(session, clazz, sb.toString(), prms, 0, 1).peek(_HibernateUtil::addToCache).findFirst().orElse(null);
    }

    private static @NotNull List<JpaBase<?>> getAndClearDirtyList() {
        return Locks.getWithLock(lock, () -> {
            List<JpaBase<?>> list = new ArrayList<>(DIRTY_LIST);
            DIRTY_LIST.clear();
            return list;
        });
    }

    private static JpaBase<?> getCached(String pkey) {
        return Locks.getWithLock(lock, () -> Null.getIfNotNull(ENTITY_CACHE.get(pkey), WeakReference::get));
    }

    private static void initialize(Object entity) {
        if(!Hibernate.isInitialized(entity)) Hibernate.initialize(entity);
    }

    private static <E> @NotNull Stream<E> queryStream(@NotNull Session session, @NotNull Class<E> clazz, String hql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        TypedQuery<E> query = session.createQuery(hql, clazz);
        params.forEach(query::setParameter);
        if(startingRow > 0) query.setFirstResult(startingRow);
        if(maxRows > 0) query.setMaxResults(maxRows);
        return query.getResultStream().peek(_HibernateUtil::initialize);
    }

    private static final class HibernateSessionFactory {
        private static final SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
    }

    static {
        EXECUTOR.scheduleAtFixedRate(() -> Locks.doWithLock(lock, () -> ENTITY_CACHE.entrySet().removeIf(e -> Objects.isNull(e.getValue().get()))), 1, 1, TimeUnit.MINUTES);
    }
}
