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
import com.projectgalen.lib.utils.U;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.base.Utils.*;
import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;
import static com.projectgalen.lib.utils.reflection.Reflection2.getAnnotatedFields;
import static com.projectgalen.lib.utils.reflection.Reflection2.getMethods;

@SuppressWarnings({ "unused", "unchecked", "SameParameterValue", "UnusedReturnValue", "RedundantCast" })
public class JpaBase<E> {

    protected final @Transient EventListeners                          updateEventListeners = new EventListeners();
    protected final @Transient String                                  syncLock             = UUID.randomUUID().toString();
    protected final @Transient Map<String, List<? extends JpaBase<?>>> cachedToManyMap      = new TreeMap<>();

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

    public @Transient E delete() {/*@f0*/
        synchronized(syncLock) { Utils.doLocked(() -> { switch(jpaState) { case NEW -> removeFromDirtyList(this); case CURRENT -> addToDirtyList(this); } jpaState = DELETED; }); return (E)this; }
    }/*@f1*/

    public @Transient E getCachedVersion() {
        synchronized(syncLock) { return Utils.locked(() -> (E)Utils.replaceWithCached(this)); }
    }

    public @Transient @NotNull JpaState getJpaState() {
        synchronized(syncLock) { return jpaState; }
    }

    public @Transient @NotNull String getPKey() {
        return U.concat(getClass().getSimpleName(), getFieldStream(Id.class).map(f -> Objects.toString(Reflection.getFieldValue(f, this), NULL_PK_TAG)).collect(Collectors.joining("][", "[", "]")));
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

    public @Transient void resetInToManyCache(@NotNull String key) {
        synchronized(syncLock) { cachedToManyMap.remove(key); }
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
        if(deep) getToOneStream().forEach(e -> e.saveChanges(session, true));
        synchronized(syncLock) {
            if(jpaState != CURRENT) {
                Utils.doLocked(() -> {
                    JpaState oldState = jpaState;

                    switch(jpaState) {/*@f0*/
                        case NEW     -> { session.persist(this); jpaState = CURRENT;    }
                        case DIRTY   -> { session.merge(this);   jpaState = CURRENT;    }
                        case DELETED -> { session.remove(this);  removeFromCache(this); }
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

    public @Transient void setPersistedField(@NotNull String fieldName, @Nullable Object newValue) {
        synchronized(syncLock) {
            getFieldStream(Column.class, ManyToOne.class, OneToOne.class).filter(f -> f.getName().equals(fieldName)).findFirst().ifPresent(fld -> setFieldValue(fld, newValue));
        }
    }

    protected @Transient void fireUpdatedEvent() {
        JpaUpdateEvent event = new JpaUpdateEvent(this);
        updateEventListeners.forEach(JpaUpdateListener.class, l -> l.entityUpdated(event));
    }

    protected <T extends JpaBase<T>> @Transient @NotNull List<T> getCachedToMany(@NotNull Class<T> cls, @NotNull String key, @NotNull String hql, @NotNull Map<String, Object> prms) {
        synchronized(syncLock) {
            return (List<T>)cachedToManyMap.computeIfAbsent(key, k -> HibernateUtil.fetch(cls, hql, prms));
        }
    }

    protected @Transient @NotNull Stream<? extends JpaBase<?>> getToOneStream() {
        return getFieldStream(ManyToOne.class, OneToOne.class).filter(Utils::isJpaBaseField).map(f -> (JpaBase<?>)getPropertyValue(f)).filter(Objects::nonNull);
    }

    private @NotNull Optional<Method> findGetter(@NotNull String name, @NotNull Class<?> returnType) {
        return getMethods(getClass()).filter(m -> ((m.getParameterCount() == 0) && (m.getReturnType() == returnType) && m.getName().equals(name))).findFirst();
    }

    private @Transient @NotNull Stream<Field> getFieldStream(Class<? extends Annotation> @NotNull ... types) {
        return getAnnotatedFields(getClass(), types);
    }

    private @Transient @Nullable Object getPropertyValue(@NotNull Field f) {
        return findGetter(getFieldGetterName(f), f.getType()).map(this::getPropertyValueFromGetter).orElseGet(() -> getPropertyValueFromField(f)).orElse(null);
    }

    private @Transient @NotNull Optional<Object> getPropertyValueFromField(@NotNull Field f) {
        return Optional.ofNullable(Reflection.getFieldValue(f, this));
    }

    private @Transient @NotNull Optional<Object> getPropertyValueFromGetter(Method m) {
        return Optional.ofNullable(Reflection.callMethod(m, this));
    }

    private void setFieldValue(@NotNull Field fld, @Nullable Object newValue) {
        if(!Objects.equals(getPropertyValue(fld), newValue)) {
            Reflection.setFieldValue(fld, this, newValue);
            if(jpaState == CURRENT) {
                jpaState = DIRTY;
                Utils.doLocked(() -> addToDirtyList(this));
            }
        }
    }
}
