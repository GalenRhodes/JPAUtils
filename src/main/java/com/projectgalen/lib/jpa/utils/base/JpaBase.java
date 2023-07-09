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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.base.Utils.*;
import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;
import static com.projectgalen.lib.utils.reflection.Reflection.getFieldValue;
import static com.projectgalen.lib.utils.reflection.Reflection2.getAnnotatedFields;

@SuppressWarnings({ "unused", "unchecked", "SameParameterValue", "UnusedReturnValue" })
public class JpaBase<E> {

    protected final @Transient EventListeners updateEventListeners = new EventListeners();
    protected final @Transient String         syncLock             = UUID.randomUUID().toString();

    @Transient JpaState jpaState;

    public JpaBase() {
        jpaState = CURRENT;
    }

    public JpaBase(boolean dummy) {
        jpaState = NEW;
        Utils.doLocked(() -> addToDirtyList(this));
    }

    public @Transient void addUpdateListener(@NotNull JpaUpdateListener listener) {
        updateEventListeners.add(JpaUpdateListener.class, listener);
    }

    public @Transient E delete() {
        synchronized(syncLock) {
            Utils.doLocked(() -> {
                if(jpaState == NEW) removeFromDirtyList(this);
                else if(jpaState == CURRENT) addToDirtyList(this);
                jpaState = DELETED;
            });
            return (E)this;
        }
    }

    public @Transient E getCachedVersion() {
        return Utils.locked(() -> Utils.replaceWithCached(this));
    }

    public @Transient @NotNull JpaState getJpaState() {
        synchronized(syncLock) { return jpaState; }
    }

    public @Transient @NotNull String getPKey() {
        return getClass().getSimpleName() + getIdFields().map(f -> Objects.toString(getFieldValue(f, this), NULL_PK_TAG)).collect(Collectors.joining("][", "[", "]"));
    }

    public @Transient boolean isCurrent() {
        synchronized(syncLock) { return (jpaState == CURRENT); }
    }

    public @Transient boolean isDeleted() {
        synchronized(syncLock) { return (jpaState == DELETED); }
    }

    public @Transient boolean isDirty() {
        synchronized(syncLock) { return (jpaState == DIRTY); }
    }

    public @Transient boolean isDirtyOrNew() {
        synchronized(syncLock) { return ((jpaState == DIRTY) || (jpaState == NEW)); }
    }

    public @Transient boolean isNew() {
        synchronized(syncLock) { return (jpaState == NEW); }
    }

    public @Transient void removeUpdateListener(@NotNull JpaUpdateListener listener) {
        updateEventListeners.remove(JpaUpdateListener.class, listener);
    }

    public @Transient E saveChanges(boolean deep) {
        return HibernateUtil.withSessionGet(session -> saveChanges(session, deep));
    }

    public @Transient E saveChanges(@NotNull Session session) {
        return saveChanges(session, false);
    }

    public @Transient E saveChanges() {
        return saveChanges(false);
    }

    public @Transient E saveChanges(@NotNull Session session, boolean deep) {
        if(deep) getManyToOneStream().forEach(e -> e.saveChanges(session, true));
        synchronized(syncLock) {
            if(jpaState != CURRENT) {
                Utils.doLocked(() -> {
                    JpaState oldState = jpaState;

                    switch(jpaState) {/*@f0*/
                        case NEW:     session.persist(this); jpaState = CURRENT;    break;
                        case DIRTY:   session.merge(this);   jpaState = CURRENT;    break;
                        case DELETED: session.remove(this);  removeFromCache(this); break;
                    }/*@f1*/

                    removeFromDirtyList(this);
                    session.flush();

                    if(oldState != DELETED) session.refresh(this);
                    if(oldState == NEW) addToCache(this);

                    fireUpdatedEvent();
                });
            }
        }
        return (E)this;
    }

    protected @Transient void fireUpdatedEvent() {
        JpaUpdateEvent event = new JpaUpdateEvent(this);
        updateEventListeners.forEach(JpaUpdateListener.class, l -> l.entityUpdated(event));
    }

    protected @Transient @NotNull Stream<Field> getIdFields() {
        return getAnnotatedFields(getClass(), Id.class);
    }

    protected @Transient @NotNull Stream<? extends JpaBase<?>> getManyToOneStream() {
        return getAnnotatedFields(getClass(), ManyToOne.class, OneToOne.class).filter(f -> JpaBase.class.isAssignableFrom(f.getType()))
                                                                              .map(f -> (JpaBase<?>)getFieldValue(f, this))
                                                                              .filter(Objects::nonNull);
    }

    protected @Transient void setField(@NotNull String name, @Nullable Object value) {
        setField(name, value, Column.class, ManyToOne.class, OneToOne.class);
    }

    protected @Transient void setField(@NotNull String name, @Nullable Object value, Class<? extends Annotation> @NotNull ... types) {
        synchronized(syncLock) {
            Field fld = getAnnotatedFields(getClass(), types).filter(f -> f.getName().equals(name)).findFirst().orElse(null);
            if((fld != null) && !Objects.equals(getFieldValue(fld, this), value)) {
                Reflection.setFieldValue(fld, this, value);
                if(jpaState == CURRENT) {
                    jpaState = DIRTY;
                    Utils.doLocked(() -> addToDirtyList(this));
                }
            }
        }
    }
}
