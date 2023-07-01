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
import com.projectgalen.lib.jpa.utils.events.JpaUpdateEvent;
import com.projectgalen.lib.jpa.utils.events.JpaUpdateListener;
import com.projectgalen.lib.utils.EventListeners;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;
import static com.projectgalen.lib.utils.reflection.Reflection2.getAnnotatedFields;
import static com.projectgalen.lib.utils.reflection.Reflection2.getFields;

@SuppressWarnings("unused")
public class JpaBase {

    protected final @Transient EventListeners updateEventListeners = new EventListeners();
    protected final @Transient String         syncLock             = UUID.randomUUID().toString();

    private @Transient JpaState jpaState;

    public JpaBase() {
        jpaState = CURRENT;
    }

    public JpaBase(boolean dummy) {
        jpaState = NEW;
        HibernateUtil.addToDirtyList(this);
    }

    public @Transient void addUpdateListener(@NotNull JpaUpdateListener listener) {
        synchronized(syncLock) { updateEventListeners.add(JpaUpdateListener.class, listener); }
    }

    public @Transient void delete() {
        synchronized(syncLock) {
            jpaState = DELETED;
            HibernateUtil.addToDirtyList(this);
        }
    }

    public @Transient @NotNull JpaState getJpaState() {
        synchronized(syncLock) { return jpaState; }
    }

    public @Transient boolean isCurrent() {
        synchronized(syncLock) { return (jpaState == CURRENT); }
    }

    public @Transient boolean isDeleted() {
        synchronized(syncLock) { return (jpaState == DELETED); }
    }

    public @Transient boolean isDirty() {
        synchronized(syncLock) { return ((jpaState == DIRTY) || (jpaState == NEW)); }
    }

    public @Transient boolean isNew() {
        synchronized(syncLock) { return (jpaState == NEW); }
    }

    public @Transient void removeUpdateListener(@NotNull JpaUpdateListener listener) {
        synchronized(syncLock) { updateEventListeners.remove(JpaUpdateListener.class, listener); }
    }

    public @Transient void saveChanges(boolean deep) {
        HibernateUtil.withSessionDo(session -> saveChanges(session, deep));
    }

    public @Transient void saveChanges(@NotNull Session session) {
        saveChanges(session, true);
    }

    public @Transient void saveChanges() {
        saveChanges(true);
    }

    public @Transient void saveChanges(@NotNull Session session, boolean deep) {
        if(deep) getManyToOneStream().forEach(c -> c.saveChanges(session, true));
        synchronized(syncLock) {
            switch(jpaState) {/*@f0*/
                case NEW: saveNew(session); break;
                case DIRTY: saveDirty(session); break;
                case DELETED: saveDeleted(session); break;
            }/*@f1*/
        }
    }

    public @Transient void setAsDirty() {
        synchronized(syncLock) {
            if(jpaState == CURRENT) {
                jpaState = DIRTY;
                HibernateUtil.addToDirtyList(this);
            }
        }
    }

    public @Transient void setJpaState(@NotNull JpaState jpaState) {
        synchronized(syncLock) { this.jpaState = jpaState; }
    }

    protected @Transient void fireUpdatedEvent() {
        JpaUpdateEvent event = new JpaUpdateEvent(this);
        updateEventListeners.forEach(JpaUpdateListener.class, l -> l.entityUpdated(event));
    }

    protected @Transient @NotNull Stream<JpaBase> getManyToOneStream() {
        return getFields(getClass()).filter(JpaBase::isChildField).map(f -> (JpaBase)Reflection.getFieldValue(f, this)).filter(Objects::nonNull);
    }

    protected void setField(@NotNull String name, @Nullable Object value) {
        Field fld = getAnnotatedFields(getClass(), Column.class, ManyToOne.class, OneToOne.class).filter(f -> f.getName().equals(name)).findFirst().orElse(null);
        if((fld != null) && !Objects.equals(Reflection.getFieldValue(fld, this), value)) {
            Reflection.setFieldValue(fld, this, value);
            setAsDirty();
        }
    }

    private void postSave(@NotNull Session session) {
        session.flush();
        session.refresh(this);
        jpaState = CURRENT;
        postSave();
    }

    private void postSave() {
        HibernateUtil.removeFromDirtyList(this);
        fireUpdatedEvent();
    }

    private void saveDeleted(@NotNull Session session) {
        session.remove(this);
        session.flush();
        postSave();
    }

    private void saveDirty(@NotNull Session session) {
        session.merge(this);
        postSave(session);
    }

    private void saveNew(@NotNull Session session) {
        session.persist(this);
        postSave(session);
    }

    private static boolean isChildField(@NotNull Field f) {
        return (Reflection.hasAnyAnnotation(f, ManyToOne.class, OneToOne.class, OneToMany.class, ManyToMany.class) && JpaBase.class.isAssignableFrom(f.getType()));
    }
}
