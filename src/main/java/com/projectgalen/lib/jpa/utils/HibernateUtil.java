package com.projectgalen.lib.jpa.utils;

// ===========================================================================
//     PROJECT: PGBudgetDB
//    FILENAME: HibernateUtils.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: February 10, 2023
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

import com.projectgalen.lib.jpa.utils.base.JpaBase;
import com.projectgalen.lib.jpa.utils.base._HibernateUtil;
import com.projectgalen.lib.jpa.utils.interfaces.StreamConsumer;
import com.projectgalen.lib.jpa.utils.interfaces.VoidStreamConsumer;
import com.projectgalen.lib.utils.concurrency.Locks;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.enums.JpaState.CURRENT;

@SuppressWarnings({ "unused", "UnusedReturnValue" })
public final class HibernateUtil extends _HibernateUtil {

    private static final Lock          lock       = new ReentrantLock(true);
    private static final List<JpaBase> DIRTY_LIST = new ArrayList<>();

    private HibernateUtil() { }

    public static void addToDirtyList(JpaBase e) {
        Locks.doWithLock(lock, () -> { if(DIRTY_LIST.stream().noneMatch(_e -> (_e == e))) DIRTY_LIST.add(e); });
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, fields, values, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, int firstRecord, int maxRecords, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, fields, values, STRNA, firstRecord, maxRecords, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, fields, values, sortFields, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, fields, values, sortFields, firstRecord, maxRecords, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, qry, prms, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int firstRecord, int maxRecords, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, qry, prms, firstRecord, maxRecords, consumer);
    }

    public static <E extends JpaBase> E fetch(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms) {
        return fetch(cls, qry, prms, 0);
    }

    public static <E extends JpaBase> E fetch(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int startingAtRecord) {
        return withSessionGet(session -> fetch(session, cls, qry, prms, startingAtRecord));
    }

    public static @Nullable <E extends JpaBase> E fetch(Session session, @NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms) {
        return fetch(session, cls, qry, prms, 0);
    }

    public static @Nullable <E extends JpaBase> E fetch(Session session, @NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int startingAtRecord) {
        return stream(session, cls, qry, prms, startingAtRecord, 1).findFirst().orElse(null);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values) {
        return find(cls, fields, values, STRNA, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields) {
        return find(cls, fields, values, sortFields, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, int firstRecord, int maxRecords) {
        return find(cls, fields, values, STRNA, firstRecord, maxRecords);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords) {
        return withSessionGet(session -> find(session, cls, fields, values, sortFields, firstRecord, maxRecords));
    }

    public static <E extends JpaBase> List<E> find(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values) {
        return find(session, cls, fields, values, STRNA, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields) {
        return find(session, cls, fields, values, sortFields, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, int firstRecord, int maxRecords) {
        return find(session, cls, fields, values, STRNA, firstRecord, maxRecords);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords) {
        return stream(session, cls, fields, values, sortFields, firstRecord, maxRecords).collect(Collectors.toList());
    }

    public static <E extends JpaBase> List<E> find(@NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params) {
        return find(cls, query, params, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params, int firstRecord, int maxRecords) {
        return withSessionGet(session -> find(session, cls, query, params, firstRecord, maxRecords));
    }

    public static <E extends JpaBase> List<E> find(@NotNull Session session, @NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params) {
        return find(session, cls, query, params, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> List<E> find(@NotNull Session session, @NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params, int firstRecord, int maxRecords) {
        return stream(session, cls, query, params, firstRecord, maxRecords).collect(Collectors.toList());
    }

    public static void removeFromDirtyList(JpaBase e) {
        Locks.doWithLock(lock, () -> DIRTY_LIST.removeIf(_e -> (_e == e)));
    }

    public static void saveAll() {
        withSessionDo(HibernateUtil::saveAll);
    }

    public static void saveAll(@NotNull Session session) {
        Locks.doWithLock(lock, () -> {
            DIRTY_LIST.forEach(e -> saveEntity(session, e));
            session.flush();
            DIRTY_LIST.stream().filter(JpaBase::isCurrent).forEach(session::refresh);
            DIRTY_LIST.clear();
        });
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, @NotNull StreamConsumer<E, R> consumer) {
        return stream(cls, fields, values, STRNA, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, int firstRecord, int maxRecords, @NotNull StreamConsumer<E, R> consumer) {
        return stream(cls, fields, values, STRNA, firstRecord, maxRecords, consumer);
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, @NotNull StreamConsumer<E, R> consumer) {
        return stream(cls, fields, values, sortFields, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords, @NotNull StreamConsumer<E, R> consumer) {
        return withSessionGet(session -> consumer.getWithStream(session, stream(session, cls, fields, values, sortFields, firstRecord, maxRecords)));
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int firstRecord, int maxRecords, @NotNull StreamConsumer<E, R> consumer) {
        return withSessionGet(session -> consumer.getWithStream(session, stream(session, cls, qry, prms, firstRecord, maxRecords)));
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, @NotNull StreamConsumer<E, R> consumer) {
        return stream(cls, qry, prms, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values) {
        return stream(session, cls, fields, values, STRNA, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, int firstRecord, int maxRecords) {
        return stream(session, cls, fields, values, STRNA, firstRecord, maxRecords);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields) {
        return stream(session, cls, fields, values, sortFields, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms) {
        return stream(session, cls, qry, prms, 0, Integer.MAX_VALUE);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords) {
        return queryAnd(session, cls, fields, values, sortFields, firstRecord, maxRecords, Query::stream).peek(_HibernateUtil::initialize);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int firstRecord, int maxRecords) {
        return query(session, cls, qry, prms, firstRecord, maxRecords, Query::stream).peek(_HibernateUtil::initialize);
    }

    private static void saveEntity(@NotNull Session session, @NotNull JpaBase entity) {
        switch(entity.getJpaState()) {/*@f0*/
            case NEW:     session.persist(entity); entity.setJpaState(CURRENT); break;
            case DIRTY:   session.merge(entity);   entity.setJpaState(CURRENT); break;
            case DELETED: session.remove(entity);                               break;
        }/*@f1*/
    }
}
