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

import com.projectgalen.lib.jpa.utils.enums.JpaState;
import com.projectgalen.lib.jpa.utils.events.JpaUpdateEvent;
import com.projectgalen.lib.jpa.utils.events.JpaUpdateListener;
import com.projectgalen.lib.utils.EventListeners;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;
import static com.projectgalen.lib.utils.reflection.Reflection2.getAnnotatedFields;
import static com.projectgalen.lib.utils.reflection.Reflection2.getFields;

@SuppressWarnings("unused")
public class JpaBase {

    protected final @Transient EventListeners updateEventListeners = new EventListeners();

    protected @Transient JpaState jpaState;

    public JpaBase() {
        jpaState = CURRENT;
    }

    public JpaBase(boolean dummy) {
        jpaState = NEW;
    }

    public @Transient void addUpdateListener(@NotNull JpaUpdateListener listener) {
        updateEventListeners.add(JpaUpdateListener.class, listener);
    }

    public @Transient void delete() {
        _HibernateUtils.withSessionDo(this::delete);
    }

    public @Transient void delete(@NotNull Session session) {
        if((jpaState != NEW) && (jpaState != DELETED)) {
            session.remove(this);
            jpaState = DELETED;
        }
    }

    public @Transient @NotNull Stream<JpaBase> getManyToOneStream() {
        return getFields(getClass()).filter(JpaBase::isChildField).map(f -> (JpaBase)Reflection.getFieldValue(f, this)).filter(Objects::nonNull);
    }

    public @Transient @NotNull JpaState getJpaState() {
        return jpaState;
    }

    public @Transient void saveChanges(boolean deep) {
        _HibernateUtils.withSessionDo(session -> saveChanges(session, deep));
    }

    public @Transient boolean isCurrent() {
        return (jpaState == CURRENT);
    }

    public @Transient boolean isDeleted() {
        return (jpaState == DELETED);
    }

    public @Transient boolean isDirty() {
        return ((jpaState == DIRTY) || (jpaState == NEW));
    }

    public @Transient boolean isNew() {
        return (jpaState == NEW);
    }

    public @Transient void removeUpdateListener(@NotNull JpaUpdateListener listener) {
        updateEventListeners.remove(JpaUpdateListener.class, listener);
    }

    public @Transient void saveChanges(@NotNull Session session) {
        saveChanges(session, true);
    }

    protected void setField(@NotNull String name, @Nullable Object value) {
        Field fld = getAnnotatedFields(getClass(), Column.class, ManyToOne.class, OneToOne.class).filter(f -> f.getName().equals(name)).findFirst().orElse(null);
        if((fld != null) && !Objects.equals(Reflection.getFieldValue(fld, this), value)) {
            Reflection.setFieldValue(fld, this, value);
            setAsDirty();
        }
    }

    public @Transient void saveChanges() {
        saveChanges(true);
    }

    public @Transient void saveChanges(@NotNull Session session, boolean deep) {
        if(deep) getManyToOneStream().forEach(c -> c.saveChanges(session, true));
        if(jpaState != CURRENT) {
            switch(jpaState) {
                case DIRTY:
                    session.merge(this);
                    session.refresh(this);
                    break;
                case NEW:
                    session.persist(this);
                    session.refresh(this);
                    break;
            }

            jpaState = CURRENT;
            fireUpdatedEvent();
        }
    }

    public @Transient void setAsDirty() {
        if(jpaState == CURRENT) jpaState = DIRTY;
    }

    public @Transient void setJpaState(@NotNull JpaState jpaState) {
        this.jpaState = jpaState;
    }

    protected @Transient void fireUpdatedEvent() {
        JpaUpdateEvent event = new JpaUpdateEvent(this);
        updateEventListeners.forEach(JpaUpdateListener.class, l -> l.entityUpdated(event));
    }

    private static boolean isChildField(@NotNull Field f) {
        return (f.isAnnotationPresent(ManyToOne.class) && JpaBase.class.isAssignableFrom(f.getType()));
    }
}
