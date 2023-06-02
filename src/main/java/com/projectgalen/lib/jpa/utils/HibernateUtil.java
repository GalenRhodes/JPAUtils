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
import com.projectgalen.lib.jpa.utils.enums.JpaState;
import com.projectgalen.lib.jpa.utils.errors.DaoException;
import com.projectgalen.lib.jpa.utils.errors.SvcConstraintViolation;
import com.projectgalen.lib.jpa.utils.interfaces.EntityDoDelegate;
import com.projectgalen.lib.jpa.utils.interfaces.QueryDelegate;
import com.projectgalen.lib.jpa.utils.interfaces.SessionDoDelegate;
import com.projectgalen.lib.jpa.utils.interfaces.SessionGetDelegate;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PersistenceException;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.projectgalen.lib.utils.PGArrays.wrap;
import static com.projectgalen.lib.utils.errors.Errors.findNestedCause;

@SuppressWarnings({ "unused", "UnusedReturnValue" })
public class HibernateUtil {
    private static final PGProperties     props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    public static final  PGResourceBundle msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");
    private static final ReentrantLock    lock  = new ReentrantLock(true);

    public static final String[] STRNA = new String[0];
    public static final Object[] OBJNA = new Object[0];

    private HibernateUtil() { }

    public static <T extends JpaBase> void delete(@NotNull T entity) {
        if(!(entity.isNew() || entity.isDeleted())) {
            HibernateUtil.withSessionDo((session, tx) -> session.remove(entity));
            entity.setJpaState(JpaState.DELETED);
        }
    }

    public static @NotNull <T extends JpaBase> List<T> find(Class<T> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues) {
        return find(cls, searchFields, searchValues, STRNA);
    }

    public static @NotNull <T extends JpaBase> List<T> find(Class<T> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, String @NotNull [] sortFields) {
        return find(cls, searchFields, searchValues, sortFields, 0, Integer.MAX_VALUE);
    }

    public static @NotNull <T extends JpaBase> List<T> find(Class<T> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, int startingRecord, int maxRecordsToReturn) {
        return find(cls, searchFields, searchValues, STRNA, startingRecord, maxRecordsToReturn);
    }

    public static @NotNull <T extends JpaBase> List<T> find(Class<T> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, String @NotNull [] sortFields, int startingRecord, int maxRecordsToReturn) {
        return Objects.requireNonNullElse(HibernateUtil.find(cls, searchFields, searchValues, sortFields, Query::list), new ArrayList<>());
    }

    public static <E extends JpaBase, R> R find(Class<E> cls, String queryString, Map<String, Object> parameters, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, queryString, parameters, 0, Integer.MAX_VALUE, queryAction);
    }

    public static <E extends JpaBase, R> R find(Class<E> cls, String queryString, Map<String, Object> parameters, int maxRecordsToReturn, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, queryString, parameters, 0, maxRecordsToReturn, queryAction);
    }

    public static <E extends JpaBase, R> R find(Class<E> cls, String queryString, Map<String, Object> parameters, int startingRecord, int maxRecordsToReturn, @NotNull QueryDelegate<E, R> queryAction) {
        return HibernateUtil.withSessionGet((session, tx) -> {
            Query<E> query = session.createQuery(queryString, cls);
            for(Map.Entry<String, Object> entry : parameters.entrySet()) query.setParameter(entry.getKey(), entry.getValue());
            if(startingRecord > 0) query.setFirstResult(startingRecord);
            query.setMaxResults(maxRecordsToReturn);
            return initialize(queryAction.action(query));
        });
    }

    public static <E extends JpaBase, R> @Nullable R find(Class<E> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, searchFields, searchValues, STRNA, queryAction);
    }

    public static <E extends JpaBase, R> @Nullable R find(Class<E> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, String @NotNull [] sortFields, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, searchFields, searchValues, sortFields, 0, Integer.MAX_VALUE, queryAction);
    }

    public static <E extends JpaBase, R> @Nullable R find(Class<E> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, int maxRecordsToReturn, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, searchFields, searchValues, STRNA, maxRecordsToReturn, queryAction);
    }

    public static <E extends JpaBase, R> @Nullable R find(Class<E> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, String @NotNull [] sortFields, int maxRecordsToReturn, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, searchFields, searchValues, sortFields, 0, maxRecordsToReturn, queryAction);
    }

    public static <E extends JpaBase, R> @Nullable R find(Class<E> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, int startingRecord, int maxRecordsToReturn, @NotNull QueryDelegate<E, R> queryAction) {
        return find(cls, searchFields, searchValues, STRNA, startingRecord, maxRecordsToReturn, queryAction);
    }

    public static <E extends JpaBase, R> @Nullable R find(Class<E> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, String @NotNull [] sortFields, int startingRecord, int maxRecordsToReturn, @NotNull QueryDelegate<E, R> queryAction) {
        if(searchFields.length != searchValues.length) throw new IllegalArgumentException(msgs.format("msg.err.fields_values_count_mismatch", searchFields.length, searchValues.length));

        Map<String, Object> parameters = new LinkedHashMap<>();
        StringBuilder       sb         = new StringBuilder().append("from ").append(cls.getSimpleName()).append(" e");

        if(searchFields.length > 0) {
            parameters.put("VAL0", searchValues[0]);
            sb.append(" where e.").append(searchFields[0]).append(" = :VAL0");

            for(int i = 1, j = searchFields.length; i < j; i++) {
                String key = String.format("VAL%d", i);
                parameters.put(key, searchValues[i]);
                sb.append(" and e.").append(searchFields[i]).append(" = :").append(key);
            }
        }

        if(sortFields.length > 0) {
            sb.append(" order by e.").append(sortFields[0]);
            for(int i = 1; i < sortFields.length; i++) sb.append(", e.").append(sortFields[i]);
        }

        String queryString = sb.toString();
        return find(cls, queryString, parameters, startingRecord, maxRecordsToReturn, queryAction);
    }

    public static @NotNull <T extends JpaBase> List<T> findAll(Class<T> cls) {
        return findAll(cls, Integer.MAX_VALUE);
    }

    public static @NotNull <T extends JpaBase> List<T> findAll(Class<T> cls, int maxRecordsToReturn) {
        return findAll(cls, 0, maxRecordsToReturn);
    }

    public static @NotNull <T extends JpaBase> List<T> findAll(Class<T> cls, int startingRecord, int maxRecordsToReturn) {
        return find(cls, STRNA, OBJNA, STRNA, startingRecord, maxRecordsToReturn);
    }

    public static @Nullable <T extends JpaBase> T get(Class<T> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues) {
        return get(cls, searchFields, searchValues, 0);
    }

    public static @Nullable <T extends JpaBase> T get(Class<T> cls, String @NotNull [] searchFields, Object @NotNull [] searchValues, int startingRecord) {
        return HibernateUtil.find(cls, searchFields, searchValues, STRNA, startingRecord, 1, Query::uniqueResult);
    }

    public static @Nullable <T extends JpaBase> T get(Class<T> cls, @NotNull String searchField, @NotNull Object searchValue) {
        return get(cls, searchField, searchValue, 0);
    }

    public static @Nullable <T extends JpaBase> T get(Class<T> cls, @NotNull String searchField, @NotNull Object searchValue, int startingRecord) {
        return get(cls, wrap(searchField), wrap(searchValue), startingRecord);
    }

    public static <R> R initialize(R entity) {
        if((entity != null) && !Hibernate.isInitialized(entity)) Hibernate.initialize(entity);
        if(entity instanceof Collection) for(Object o : (Collection<?>)entity) initialize(o);
        return entity;
    }

    public static void refresh(@NotNull JpaBase entity) {
        withSessionDo(((session, tx) -> withEntityDo(entity, session::refresh)));
    }

    public static void withEntityDo(@NotNull JpaBase parent, @NotNull EntityDoDelegate<JpaBase> delegate) { withEntityDo(true, parent, delegate); }

    public static void withEntityDo(boolean parentLast, @NotNull JpaBase parent, @NotNull EntityDoDelegate<JpaBase> delegate) {
        if(!parentLast) delegate.action(parent);
        Reflection.forEachField(parent.getClass(), field -> {
            if(field.isAnnotationPresent(ManyToOne.class) && JpaBase.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    JpaBase child = (JpaBase)field.get(parent);
                    if(child != null) withEntityDo(parentLast, child, delegate);
                }
                catch(Exception e) {
                    throw ((e instanceof DaoException) ? ((DaoException)e) : new DaoException(e));
                }
            }
            return false;
        });
        if(parentLast) delegate.action(parent);
    }

    public static void withSessionDo(@NotNull SessionDoDelegate delegate) {
        withSessionGet((session, tx) -> {
            delegate.action(session, tx);
            return null;
        });
    }

    public static <R> R withSessionGet(@NotNull SessionGetDelegate<R> delegate) {
        try(Session session = HibernateSessionFactory.sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();

            try {
                R result = delegate.get(session, tx);
                session.flush();
                tx.commit();
                return result;
            }
            catch(Throwable t) {
                try { tx.rollback(); } catch(Throwable t2) { t2.printStackTrace(System.err); }
                throw handleErrors(t);
            }
        }
    }

    private static RuntimeException handleErrors(Throwable t) {
        if(t instanceof PersistenceException) {
            ConstraintViolationException cve = findNestedCause(t, ConstraintViolationException.class);
            if(cve != null) {
                SQLIntegrityConstraintViolationException sicve = findNestedCause(cve.getCause(), SQLIntegrityConstraintViolationException.class);
                if(sicve != null) {
                    Matcher m = Pattern.compile(props.getProperty("pgbudget.dao.dup_key_regexp")).matcher(sicve.getMessage());
                    if(m.matches()) throw new SvcConstraintViolation(m.group(1), m.group(2), m.group(3), cve.getConstraintName());
                }
            }
            throw (PersistenceException)t;
        }
        if(t instanceof Error) throw (Error)t;
        if(t instanceof DaoException) throw (DaoException)t;
        if(t instanceof RuntimeException) throw (RuntimeException)t;
        throw new DaoException(t);
    }

    private static final class HibernateSessionFactory {
        private static final SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
    }
}
