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
import com.projectgalen.lib.jpa.utils.interfaces.QueryDelegate;
import com.projectgalen.lib.jpa.utils.interfaces.SessionConsumer;
import com.projectgalen.lib.jpa.utils.interfaces.VoidSessionConsumer;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import com.projectgalen.lib.utils.Streams;
import com.projectgalen.lib.utils.collections.CollectionItem;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class _HibernateUtil {
    public static final PGProperties       props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    public static final PGResourceBundle   msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");
    public static final String @NotNull [] STRNA = new String[0];
    public static final Object @NotNull [] OBJNA = new Object[0];

    public _HibernateUtil() { }

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

    protected static <E extends JpaBase> void initialize(E entity) {
        if(!Hibernate.isInitialized(entity)) Hibernate.initialize(entity);
    }

    protected static <E extends JpaBase, R> R query(@NotNull Session session, @NotNull Class<E> cls, @NotNull String query, @NotNull Map<String, Object> params, int firstRecord, int maxRecords, @NotNull QueryDelegate<E, R> delegate) {
        Query<E> q = session.createQuery(query, cls);
        params.forEach(q::setParameter);
        if(firstRecord > 0) q.setFirstResult(firstRecord);
        q.setMaxResults(maxRecords);
        return delegate.getWithQuery(q);
    }

    protected static <E extends JpaBase, R> R queryAnd(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords, @NotNull QueryDelegate<E, R> delegate) {
        return query(session, cls, "and", fields, values, sortFields, firstRecord, maxRecords, delegate);
    }

    protected static <E extends JpaBase, R> R queryOr(@NotNull Session session, @NotNull Class<E> cls, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords, @NotNull QueryDelegate<E, R> delegate) {
        return query(session, cls, "or", fields, values, sortFields, firstRecord, maxRecords, delegate);
    }

    private static @NotNull String foo01(@NotNull CollectionItem<String> i) {
        return String.format(" e.%s = :val%d ", i.item, i.index);
    }

    private static @NotNull String foo02(@NotNull String f) {
        return String.format(" e.%s", f);
    }

    private static <E extends JpaBase> void foo03(@NotNull Query<E> qry, @NotNull CollectionItem<Object> i) {
        qry.setParameter(String.format("val%d", i.index), i.item);
    }

    private static <E extends JpaBase, R> R query(@NotNull Session session, @NotNull Class<E> cls, @NotNull String andor, String @NotNull [] fields, Object @NotNull [] values, String @NotNull [] sortFields, int firstRecord, int maxRecords, @NotNull QueryDelegate<E, R> delegate) {
        if(fields.length != values.length) throw new IllegalArgumentException(msgs.format("msg.err.fields_values_count_mismatch", fields.length, values.length));

        StringBuilder sb = new StringBuilder().append(String.format("from %s e", cls.getSimpleName()));
        if(fields.length > 0) sb.append(Streams.arrayStream(fields).map(_HibernateUtil::foo01).collect(Collectors.joining(andor, " where", "")));
        if(sortFields.length > 0) sb.append(Arrays.stream(sortFields).map(_HibernateUtil::foo02).collect(Collectors.joining(",", " order by", "")));

        Query<E> qry = session.createQuery(sb.toString(), cls);
        if(values.length > 0) Streams.arrayStream(values).forEach(i -> foo03(qry, i));
        if(firstRecord > 0) qry.setFirstResult(firstRecord);
        qry.setMaxResults(maxRecords);
        return delegate.getWithQuery(qry);
    }

    private static final class HibernateSessionFactory {
        private static final SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
    }
}
