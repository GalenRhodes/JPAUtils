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
import com.projectgalen.lib.jpa.utils.errors.DaoException;
import com.projectgalen.lib.jpa.utils.interfaces.*;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings({ "unused", "UnusedReturnValue" })
public final class HibernateUtil {
    public static final PGProperties       props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    public static final PGResourceBundle   msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");
    public static final String @NotNull [] STRNA = new String[0];
    public static final Object @NotNull [] OBJNA = new Object[0];

    private HibernateUtil() { }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, @NotNull VoidStreamConsumer<E> consumer) {
        doStream(cls, fields, values, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, int firstRecord, int maxRecords, @NotNull VoidStreamConsumer<E> consumer) {
        doStream(cls, fields, values, STRNA, firstRecord, maxRecords, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, @NotNull VoidStreamConsumer<E> consumer) {
        doStream(cls, fields, values, sortFields, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords, @NotNull VoidStreamConsumer<E> consumer) {
        buildQuery(cls, fields, values, sortFields, (q, p) -> stream(cls, q, p, firstRecord, maxRecords, consumer));
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, qry, prms, 0, Integer.MAX_VALUE, consumer);
    }

    public static <E extends JpaBase> void doStream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int firstRecord, int maxRecords, @NotNull VoidStreamConsumer<E> consumer) {
        stream(cls, qry, prms, firstRecord, maxRecords, consumer);
    }

    public static <E extends JpaBase, R> R query(@NotNull Session session, @NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params, int firstRecord, int maxRecords, @NotNull QueryDelegate<E, R> delegate) {
        Query<E> q = session.createQuery(query, cls);
        params.forEach(q::setParameter);
        if(firstRecord > 0) q.setFirstResult(firstRecord);
        q.setMaxResults(maxRecords);
        return delegate.getWithQuery(q);
    }

    public static <E extends JpaBase, R> R query(@NotNull Session session, @NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params, @NotNull QueryDelegate<E, R> delegate) {
        return query(session, cls, query, params, 0, Integer.MAX_VALUE, delegate);
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
        return buildQuery(cls, fields, values, sortFields, (q, p) -> stream(cls, q, p, firstRecord, maxRecords, consumer));
    }

    public static <E extends JpaBase, R> R stream(@NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int firstRecord, int maxRecords, @NotNull StreamConsumer<E, R> consumer) {
        return withSessionGet(session -> consumer.getWithStream(session, query(session, cls, qry, prms, firstRecord, maxRecords, Query::stream)));
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

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords) {
        return buildQuery(cls, fields, values, sortFields, (q, p) -> stream(session, cls, q, p, firstRecord, maxRecords));
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms, int firstRecord, int maxRecords) {
        return query(session, cls, qry, prms, firstRecord, maxRecords, Query::stream);
    }

    public static <E extends JpaBase> Stream<E> stream(@NotNull Session session, @NotNull Class<E> cls, @NotNull String qry, @NotNull Map<String, Object> prms) {
        return stream(session, cls, qry, prms, 0, Integer.MAX_VALUE);
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

    private static <E extends JpaBase, R> R buildQuery(@NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, @NotNull QueryBuilderHandler<R> handler) {
        if(fields.length != values.length) throw new IllegalArgumentException(msgs.format("msg.err.fields_values_count_mismatch", fields.length, values.length));

        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder       sb     = new StringBuilder().append(String.format("from %s e", cls.getSimpleName()));

        if(fields.length > 0) {
            sb.append(String.format(" where e.%s = :v0", fields[0]));
            params.put("v0", values[0]);

            for(int i = 1; i < fields.length; i++) {
                sb.append(String.format(" and e.%s = :v%d", fields[i], i));
                params.put(String.format("v%d", i), values[i]);
            }
        }

        if(sortFields.length > 0) {
            sb.append(" order by e.").append(sortFields[0]);
            for(int i = 1; i < sortFields.length; i++) sb.append(", e.").append(sortFields[i]);
        }

        return handler.withBuiltQuery(sb.toString(), params);
    }

    private static final class HibernateSessionFactory {
        private static final SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
    }
}
