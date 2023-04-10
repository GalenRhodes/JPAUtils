package com.projectgalen.lib.jpa.utils.base;

// ===========================================================================
//     PROJECT: PGBudgetDB
//    FILENAME: AbstractDao.java
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

import com.projectgalen.lib.jpa.utils.HibernateUtil;
import com.projectgalen.lib.jpa.utils.enums.JpaState;
import com.projectgalen.lib.jpa.utils.errors.DaoException;
import com.projectgalen.lib.jpa.utils.errors.SvcConstraintViolation;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import com.projectgalen.lib.utils.U;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PersistenceException;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({ "UnusedReturnValue", "unused", "SameParameterValue" })
public abstract class AbstractDao<T extends JpaBase> {

    protected static final PGProperties     props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    protected static final PGResourceBundle msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");

    protected final Class<T> entityClass;

    public AbstractDao(@NotNull Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public @NotNull Class<T> getEntityClass() {
        return entityClass;
    }

    public @NotNull String getEntityName() {
        return getEntityClass().getSimpleName();
    }

    /**
     * Synonym for update(entity).
     *
     * @param entity The entity to insert into the database
     * @param deep   If true it will also attempt to create/update any child objects as well.
     */
    protected void _create(@NotNull T entity, boolean deep) {
        _update(entity, deep);
    }

    protected void _delete(@NotNull T entity) {
        if(!entity.isNew()) {
            try(Session session = HibernateUtil.getSessionFactory().openSession()) {
                _withTransaction(session, s -> s.remove(entity));
                entity.setJpaState(JpaState.DELETED);
            }
        }
    }

    protected @NotNull List<T> _find(@NotNull String queryString, @Nullable Map<String, Object> parameters, boolean initializeResults) {
        @NotNull List<T> list = Objects.requireNonNull(_findWithAction(queryString, parameters, q -> notNullResults(q.list())));
        if(initializeResults) for(T entity : list) Hibernate.initialize(entity);
        return list;
    }

    protected @NotNull List<T> _findAll(boolean initializeResults) {
        return _find(String.format("from %s e", getEntityName()), null, initializeResults);
    }

    protected <R> @Nullable R _findWithAction(@NotNull String queryString, @Nullable Map<String, Object> parameters, @NotNull QueryAction<T, R> queryAction) {
        return _withSessionGet(session -> {
            Query<T> query = session.createQuery(queryString, getEntityClass());
            if(parameters != null) for(Map.Entry<String, Object> entry : parameters.entrySet()) query.setParameter(entry.getKey(), entry.getValue());
            return queryAction.action(query);
        });
    }

    protected @Nullable T _get(String @NotNull [] searchFields, Object @NotNull [] searchValues) {
        if(searchFields.length != searchValues.length) throw new IllegalArgumentException(msgs.format("msg.err.fields_values_count_mismatch", searchFields.length, searchValues.length));

        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder       sb     = new StringBuilder().append("from ").append(getEntityName()).append(" e where ");
        boolean             first  = true;

        for(int i = 0, j = searchFields.length; i < j; i++) {
            String key = String.format("VAL%d", i);
            if(first) first = false;
            else sb.append(" and ");
            sb.append("e.").append(searchFields[i]).append(" = :").append(key);
            params.put(key, searchValues[i]);
        }

        return _findWithAction(sb.toString(), params, q -> init(q.uniqueResult()));
    }

    protected @Nullable T _get(@NotNull String searchField, @NotNull Object searchValue) {
        return _get(U.asArray(searchField), U.asArray(searchValue));
    }

    protected @NotNull T _getNewEntity() {
        try {
            try {
                return entityClass.getConstructor(boolean.class).newInstance(false);
            }
            catch(Exception e) {
                return entityClass.getConstructor().newInstance();
            }
        }
        catch(Exception e) {
            throw new DaoException(msgs.format("msg.err.dao.new_instance_failure", getEntityName()), e);
        }
    }

    protected void _update(@NotNull T entity, boolean deep) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            _withTransaction(session, s -> _update(s, entity, deep));
            session.refresh(entity);
        }
    }

    protected <R> R _withSessionGet(@NotNull SessionAction<R> delegate) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            return delegate.action(session);
        }
    }

    protected void _withSessionUpdate(@NotNull SessionUpdateAction delegate) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            _withTransaction(session, delegate);
        }
    }

    protected @Nullable T init(@Nullable T entity) {
        if(entity != null) Hibernate.initialize(entity);
        return entity;
    }

    private void _update(@NotNull Session session, @Nullable JpaBase entity, boolean deep) {
        if(entity != null) {
            if(deep) Reflection.forEachField(entity.getClass(), field -> {
                if(field.isAnnotationPresent(ManyToOne.class) && JpaBase.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        _update(session, (JpaBase)field.get(entity), true);
                    }
                    catch(Exception e) {
                        if(e instanceof RuntimeException) throw (RuntimeException)e;
                        throw new DaoException(e);
                    }
                }
                return false;
            });
            switch(entity.getJpaState()) {/*@f0*/
                case NEW:
                case DELETED: session.persist(entity); entity.setJpaState(JpaState.NORMAL); break;
                case DIRTY:   session.merge(entity);   entity.setJpaState(JpaState.NORMAL); break;
                default:                                                                    break;
            }/*@f1*/
        }
    }

    private @NotNull List<T> notNullResults(@Nullable List<T> results) {
        return (results == null) ? Collections.emptyList() : results;
    }

    protected static void _withTransaction(@NotNull Session session, @NotNull SessionUpdateAction delegate) {
        Transaction transaction = session.beginTransaction();
        try {
            delegate.action(session);
            session.flush();
            transaction.commit();
        }
        catch(Exception e) {
            transaction.rollback();
            if(((e instanceof PersistenceException) && (e.getCause() instanceof ConstraintViolationException) && (e.getCause().getCause() instanceof SQLIntegrityConstraintViolationException))) {
                Matcher m = Pattern.compile(props.getProperty("pgbudget.dao.dup_key_regexp")).matcher(e.getCause().getCause().getMessage());
                if(m.matches()) throw new SvcConstraintViolation(m.group(1), m.group(2), m.group(3), ((ConstraintViolationException)e.getCause()).getConstraintName());
            }
            throw e;
        }
    }

    public interface QueryAction<O, R> {
        R action(@NotNull Query<O> query);
    }

    public interface SessionAction<R> {
        R action(@NotNull Session session);
    }

    public interface SessionUpdateAction {
        void action(@NotNull Session session);
    }
}
