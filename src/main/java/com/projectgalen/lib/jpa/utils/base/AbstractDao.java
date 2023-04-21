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
import com.projectgalen.lib.jpa.utils.interfaces.QueryAction;
import com.projectgalen.lib.jpa.utils.interfaces.SessionDoAction;
import com.projectgalen.lib.jpa.utils.interfaces.SessionGetAction;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import com.projectgalen.lib.utils.U;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({ "UnusedReturnValue", "unused", "SameParameterValue" })
public class AbstractDao<T extends JpaBase> {

    private static final PGProperties     props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    private static final PGResourceBundle msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");

    private final Class<T> entityClass;

    public AbstractDao(@NotNull Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Synonym for update(entity).
     *
     * @param entity The entity to insert into the database
     * @param deep   If true it will also attempt to create/update any child objects as well.
     */
    public void create(@NotNull T entity, boolean deep) {
        update(entity, deep);
    }

    public void create(@NotNull T entity) {
        update(entity, true);
    }

    public void delete(@NotNull T entity) {
        if(!entity.isNew()) {
            try(Session session = HibernateUtil.getSessionFactory().openSession()) {
                HibernateUtil.withTransaction(session, s -> s.remove(entity));
                entity.setJpaState(JpaState.DELETED);
            }
        }
    }

    public @NotNull List<T> find(@NotNull String queryString, @Nullable Map<String, Object> parameters) {
        return find(queryString, parameters, true);
    }

    public @NotNull List<T> find(@NotNull String queryString, @Nullable Map<String, Object> parameters, boolean initializeResults) {
        @NotNull List<T> list = Objects.requireNonNull(findWithAction(queryString, parameters, q -> notNullResults(q.list())));
        if(initializeResults) for(T entity : list) Hibernate.initialize(entity);
        return list;
    }

    public @NotNull List<T> findAll() {
        return findAll(true);
    }

    public @NotNull List<T> findAll(boolean initializeResults) {
        return find(String.format("from %s e", getEntityName()), null, initializeResults);
    }

    public <R> @Nullable R findWithAction(@NotNull String queryString, @Nullable Map<String, Object> parameters, @NotNull QueryAction<T, R> queryAction) {
        return withSessionGet(session -> {
            Query<T> query = session.createQuery(queryString, getEntityClass());
            if(parameters != null) for(Map.Entry<String, Object> entry : parameters.entrySet()) query.setParameter(entry.getKey(), entry.getValue());
            return queryAction.action(query);
        });
    }

    public @Nullable T get(String @NotNull [] searchFields, Object @NotNull [] searchValues) {
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

        return findWithAction(sb.toString(), params, q -> init(q.uniqueResult()));
    }

    public @Nullable T get(@NotNull String searchField, @NotNull Object searchValue) {
        return get(U.asArray(searchField), U.asArray(searchValue));
    }

    public @NotNull Class<T> getEntityClass() {
        return entityClass;
    }

    public @NotNull String getEntityName() {
        return getEntityClass().getSimpleName();
    }

    public @NotNull T getNewEntity() {
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

    public @Nullable T init(@Nullable T entity) {
        if(entity != null) Hibernate.initialize(entity);
        return entity;
    }

    public void update(@NotNull T entity) {
        HibernateUtil.update(entity, true);
    }

    public void update(@NotNull T entity, boolean deep) {
        HibernateUtil.update(entity, deep);
    }

    public <R> R withSessionGet(@NotNull SessionGetAction<R> delegate) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            return delegate.action(session);
        }
    }

    public void withSessionUpdate(@NotNull SessionDoAction delegate) {
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            HibernateUtil.withTransaction(session, delegate);
        }
    }

    private @NotNull List<T> notNullResults(@Nullable List<T> results) {
        return (results == null) ? Collections.emptyList() : results;
    }
}
