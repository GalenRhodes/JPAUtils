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
import jakarta.persistence.PersistenceException;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({ "UnusedReturnValue", "unused" })
public abstract class AbstractDao<T extends JpaBase> {

    protected static final PGProperties     props = PGProperties.getXMLProperties("settings.properties", HibernateUtil.class);
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
     */
    public void create(@NotNull T entity) {
        update(entity);
    }

    public void delete(@NotNull T entity) {
        if(!entity.isNew()) {
            withSessionDo(session -> q(() -> session.remove(entity)));
            entity.setJpaState(JpaState.DELETED);
        }
    }

    public @NotNull List<T> findAll() {
        List<T> results = withSessionDo(session -> session.createQuery(props.format("pgbudget.dao.get_from", getEntityName()), getEntityClass()).list());
        if(results == null) return Collections.emptyList();
        for(T o : results) o.setJpaState(JpaState.NORMAL);
        return results;
    }

    public T get(@NotNull Long id) {
        return get("id", id);
    }

    public T get(long id) {
        return get("id", id);
    }

    public T get(@NotNull String searchField, @NotNull Object searchValue) {
        return withSessionDo(session -> {
            String   valueKey = props.getProperty("pgbudget.dao.value_key");
            Query<T> query    = session.createQuery(props.format("pgbudget.dao.get_unique", getEntityName(), searchField, valueKey), getEntityClass());

            query.setParameter(valueKey, searchValue);

            T result = query.uniqueResult();

            Hibernate.initialize(result);
            result.setJpaState(JpaState.NORMAL);
            return result;
        });
    }

    public @NotNull T newInstance() {
        try {
            try {
                return entityClass.getConstructor(boolean.class).newInstance(false);
            }
            catch(Exception e) {
                return entityClass.getConstructor().newInstance();
            }
        }
        catch(Exception e) {
            throw new DaoException(msgs.getString("msg.err.dao.instantiation"), e);
        }
    }

    public void update(@NotNull T entity) {
        switch(entity.getJpaState()) {
            case NEW:
            case DELETED:
                withSessionDo(session -> q(() -> session.persist(entity)));
                entity.setJpaState(JpaState.NORMAL);
                break;
            case DIRTY:
                withSessionDo(session -> session.merge(entity));
                entity.setJpaState(JpaState.NORMAL);
                break;
            default:
                break;
        }
    }

    protected @NotNull T init(@NotNull T entity) {
        Hibernate.initialize(entity);
        return entity;
    }

    protected int q(@NotNull QDelegate qDelegate) {
        qDelegate.action();
        return 0;
    }

    protected <R> R withSessionDo(@NotNull SessionAction<R> delegate) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            try {
                session.beginTransaction();
                R r = delegate.action(session);
                session.getTransaction().commit();
                return r;
            }
            catch(Exception e) {
                session.getTransaction().rollback();
                if(isConstraintViolation(e)) {
                    Matcher m = Pattern.compile(props.getProperty("pgbudget.dao.dup_key_regexp")).matcher(e.getCause().getCause().getMessage());
                    if(m.matches()) throw new SvcConstraintViolation(m.group(1), m.group(2), m.group(3), ((ConstraintViolationException)e.getCause()).getConstraintName());
                }
                throw e;
            }
        }
    }

    private static boolean isConstraintViolation(@NotNull Exception e) {
        return ((e instanceof PersistenceException) && (e.getCause() instanceof ConstraintViolationException) && (e.getCause().getCause() instanceof SQLIntegrityConstraintViolationException));
    }

    protected interface QDelegate {
        void action();
    }

    protected interface SessionAction<R> {
        R action(@NotNull Session session);
    }
}
