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
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
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

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HibernateUtil {
    private static final PGProperties     props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    private static final PGResourceBundle msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");

    public static SessionFactory getSessionFactory() {
        return HOLDER.INSTANCE;
    }

    private static final class HOLDER {
        private static final SessionFactory INSTANCE = new Configuration().configure().buildSessionFactory();
    }

    public static <T extends JpaBase> void update(@NotNull Session session, @Nullable T entity, boolean deep) {
        if(entity != null) {
            if(deep) Reflection.forEachField(entity.getClass(), field -> {
                if(field.isAnnotationPresent(ManyToOne.class) && JpaBase.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        update(session, (JpaBase)field.get(entity), true);
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

    public static void withTransaction(@NotNull Session session, @NotNull SessionUpdateAction delegate) {
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
}
