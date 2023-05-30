package com.projectgalen.lib.jpa.utils.base;

// ===========================================================================
//     PROJECT: PGBudgetDB
//    FILENAME: Base.java
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
import com.projectgalen.lib.utils.Null;
import com.projectgalen.lib.utils.concurrency.Locks;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.HibernateUtil.withSessionDo;
import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;

@SuppressWarnings({ "unused" })
public class JpaBase {

    protected final Map<String, Object> changeMap = new TreeMap<>();
    protected final ReentrantLock       lock      = new ReentrantLock(true);

    @Transient protected JpaState jpaState;

    public JpaBase() {
        jpaState = CURRENT;
    }

    public JpaBase(boolean dummy) {
        jpaState = (dummy ? CURRENT : NEW);
    }

    public void delete() {
        Locks.doWithLock(lock, () -> {
            if((jpaState != NEW) && (jpaState != DELETED)) {
                withSessionDo((session, tx) -> session.remove(this));
                jpaState = DELETED;
            }
        });
    }

    @Transient
    public @NotNull JpaState getJpaState() {
        return Locks.getWithLock(lock, () -> jpaState);
    }

    public void initialize() {
        Locks.doWithLock(lock, () -> Hibernate.initialize(this));
    }

    @Transient
    public boolean isDeleted() {
        return Locks.getWithLock(lock, () -> (jpaState == DELETED));
    }

    @Transient
    public boolean isDirty() {
        return Locks.getWithLock(lock, () -> ((jpaState == DIRTY) || (jpaState == NEW)));
    }

    @Transient
    public boolean isNew() {
        return Locks.getWithLock(lock, () -> (jpaState == NEW));
    }

    @Transient
    public boolean isCurrent() {
        return (jpaState == CURRENT);
    }

    public void refresh() {
        HibernateUtil.refresh(this);
        setJpaState(CURRENT);
    }

    @Transient
    public void saveChanges(boolean deep) {
        HibernateUtil.withSessionDo((session, tx) -> saveChanges(session, tx, deep));
    }

    public void saveChanges() {
        saveChanges(true);
    }

    public void setAsDirty() {
        Locks.doWithLock(lock, () -> { if(jpaState == CURRENT) jpaState = DIRTY; });
    }

    @Transient
    public void setJpaState(@NotNull JpaState jpaState) {
        Locks.doWithLock(lock, () -> this.jpaState = jpaState);
    }

    protected void addValueToChangeMap(@NotNull String fieldName, @Nullable Object originalValue) {
        Locks.doWithLock(lock, () -> { if(!changeMap.containsKey(fieldName)) changeMap.put(fieldName, Objects.requireNonNullElse(originalValue, Null.NULL())); });
    }

    protected boolean didValueChange(@NotNull String fieldName) {
        return Locks.getWithLock(lock, () -> changeMap.containsKey(fieldName));
    }

    @Transient
    protected @NotNull Stream<Field> getManyToOneFieldsStream() {
        return Reflection.getFieldsWithAnyAnnotation(getClass(), ManyToOne.class).stream().filter(f -> JpaBase.class.isAssignableFrom(f.getType()));
    }

    protected @Nullable Object getOriginalValue(@NotNull String fieldName) {
        return Locks.getWithLock(lock, () -> Null.get(changeMap.get(fieldName)));
    }

    protected void saveChanges(@NotNull Session session, @NotNull Transaction tx, boolean deep) {
        Locks.doWithLock(lock, () -> {
            if(deep) getManyToOneFieldsStream().forEach(f -> saveChild(session, tx, f));
            if(jpaState != CURRENT) {
                if(jpaState == DIRTY) session.merge(this); else session.persist(this);/*@f0*/
                if(jpaState != DELETED) session.refresh(this);/*@f1*/
                jpaState = CURRENT;
                changeMap.clear();
            }
        });
    }

    private void saveChild(@NotNull Session session, @NotNull Transaction tx, Field field) {
        try {
            field.setAccessible(true);
            Null.doIfNotNull((JpaBase)field.get(this), c -> c.saveChanges(session, tx, true));
        }
        catch(Exception e) {
            throw ((e instanceof DaoException) ? ((DaoException)e) : new DaoException(e));
        }
    }
}
