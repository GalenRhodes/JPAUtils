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
import com.projectgalen.lib.jpa.utils.interfaces.EntityAction;
import com.projectgalen.lib.jpa.utils.interfaces.TransactionAction;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PersistenceException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class HibernateUtil {
    private static final PGProperties props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);

    private final Session session;

    private HibernateUtil() {
        session = getSessionFactory().openSession();
    }

    public Session getSession() {
        return session;
    }

    public static SessionFactory getSessionFactory() {
        return HOLDER.INSTANCE;
    }

    public static void refresh(@Nullable JpaBase entity) {
        if(entity != null) refresh(shared().getSession(), entity, true);
    }

    public static void refresh(@Nullable JpaBase entity, boolean deep) {
        if(entity != null) refresh(shared().getSession(), entity, deep);
    }

    public static void refresh(@NotNull Session session, @NotNull JpaBase entity) {
        refresh(session, entity, true);
    }

    public static void refresh(@NotNull Session session, @NotNull JpaBase entity, boolean deep) {
        if(deep) withEachEntity(entity, true, session::refresh);
        else session.refresh(entity);
    }

    public static HibernateUtil shared() { return HibernateUtilHolder.INSTANCE; }

    public static void update(@NotNull List<JpaBase> entities) {
        update(entities, true);
    }

    public static void update(@NotNull List<JpaBase> entities, boolean deep) {
        for(JpaBase entity : entities) update(shared().getSession(), entity, deep);
    }

    public static void update(@Nullable JpaBase entity) {
        update(entity, true);
    }

    public static void update(@Nullable JpaBase entity, boolean deep) {
        if(entity != null) update(shared().getSession(), entity, deep);
    }

    public static void update(@NotNull Session session, @Nullable JpaBase entity, boolean deep) {
        if(deep) {
            List<JpaBase> list = new ArrayList<>();
            withTransaction(session, transaction -> withEachEntity(entity, true, child -> _update(session, child, list)));
            for(JpaBase child : list) {
                if(child.getJpaState() != JpaState.NORMAL) child.setJpaState(JpaState.NORMAL);
                session.refresh(child);
            }
        }
        else update(session, entity);
    }

    public static void update(@NotNull Session session, @Nullable JpaBase entity) {
        if((entity != null)) {
            withTransaction(session, transaction -> _update(session, entity, null));
            if(entity.getJpaState() != JpaState.NORMAL) entity.setJpaState(JpaState.NORMAL);
            session.refresh(entity);
        }
    }

    public static void withEachEntity(@Nullable JpaBase entity, @NotNull EntityAction<JpaBase> action) {
        withEachEntity(entity, false, action);
    }

    public static void withEachEntity(@Nullable JpaBase entity, boolean parentLast, @NotNull EntityAction<JpaBase> action) {
        if(entity != null) {
            if(!parentLast) action.action(entity);
            Reflection.forEachField(entity.getClass(), field -> traverseChildren(entity, parentLast, action, field));
            if(parentLast) action.action(entity);
        }
    }

    public static void withTransaction(@NotNull Session session, @NotNull TransactionAction delegate) {
        Transaction transaction = session.beginTransaction();

        try {
            delegate.action(transaction);
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

    private static void _update(@NotNull Session session, @NotNull JpaBase entity, List<JpaBase> list) {
        switch(entity.getJpaState()) {/*@f0*/
            case NEW:
            case DELETED: session.persist(entity);
            case DIRTY:   session.merge(entity);
        }/*@f1*/
        if(list != null) list.add(entity);
    }

    private static boolean traverseChildren(@NotNull JpaBase entity, boolean parentLast, @NotNull EntityAction<JpaBase> action, @NotNull Field field) {
        if(field.isAnnotationPresent(ManyToOne.class) && JpaBase.class.isAssignableFrom(field.getType())) {
            try {
                field.setAccessible(true);
                withEachEntity((JpaBase)field.get(entity), parentLast, action);
            }
            catch(Exception e) {
                if(e instanceof RuntimeException) throw (RuntimeException)e;
                throw new DaoException(e);
            }
        }
        return false;
    }

    private static final class HOLDER {
        private static final SessionFactory INSTANCE = new Configuration().configure().buildSessionFactory();
    }

    private static final class HibernateUtilHolder {
        private static final HibernateUtil INSTANCE = new HibernateUtil();
    }
}
