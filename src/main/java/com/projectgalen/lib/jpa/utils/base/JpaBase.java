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
import com.projectgalen.lib.jpa.utils.events.*;
import com.projectgalen.lib.utils.Null;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.projectgalen.lib.jpa.utils.HibernateUtil.withSessionDo;
import static com.projectgalen.lib.jpa.utils.enums.JpaState.*;

@SuppressWarnings("unused")
public class JpaBase {

    protected final @Transient JpaFieldListenerList        fieldEventListeners        = new JpaFieldListenerList();
    protected final @Transient JpaRelationshipListenerList relationshipEventListeners = new JpaRelationshipListenerList();
    protected final @Transient Set<ChangedField>           changedFields              = new TreeSet<>();

    protected @Transient JpaState jpaState;

    public JpaBase() {
        jpaState = CURRENT;
    }

    public JpaBase(boolean dummy) {
        jpaState = (dummy ? CURRENT : NEW);
    }

    @Transient
    public void addFieldEventListener(@NotNull JpaFieldListener listener, @Nullable String fieldName, @Nullable Class<?> fieldClass, JpaEventType... eventTypes) {
        fieldEventListeners.addListener(listener, fieldName, fieldClass, eventTypes);
    }

    public void addRelationshipEventListener(@NotNull JpaRelationshipListener listener, @Nullable String sourceFieldName, @Nullable Class<? extends JpaBase> targetClass, JpaEventType... eventTypes) {
        relationshipEventListeners.addListener(listener, sourceFieldName, targetClass, eventTypes);
    }

    public void delete() {
        if((jpaState != NEW) && (jpaState != DELETED)) {
            withSessionDo((session, tx) -> session.remove(this));
            jpaState = DELETED;
        }
    }

    @Transient
    public Stream<ChangedField> getChangedFieldStream() {
        return changedFields.stream();
    }

    @Transient
    public Set<ChangedField> getChangedFields() {
        return getChangedFieldStream().collect(Collectors.toSet());
    }

    @Transient
    public @NotNull JpaState getJpaState() {
        return jpaState;
    }

    public void initialize() {
        Hibernate.initialize(this);
    }

    @Transient
    public boolean isCurrent() {
        return (jpaState == CURRENT);
    }

    @Transient
    public boolean isDeleted() {
        return (jpaState == DELETED);
    }

    @Transient
    public boolean isDirty() {
        return ((jpaState == DIRTY) || (jpaState == NEW));
    }

    @Transient
    public boolean isNew() {
        return (jpaState == NEW);
    }

    public void refresh() {
        HibernateUtil.refresh(this);
        changedFields.clear();
        jpaState = CURRENT;
    }

    @Transient
    public void removeFieldEventListener(@Nullable String fieldName, @Nullable Class<?> fieldType, @NotNull JpaFieldListener listener, JpaEventType... eventTypes) {
        fieldEventListeners.removeListener(listener, fieldName, fieldType, eventTypes);
    }

    @Transient
    public void saveChanges(boolean deep) {
        if(getJpaState() != JpaState.CURRENT) {
            // Do this before we actually save so we capture the changed fields.
            List<JpaFieldEvent>        fldEvents = getFieldEvent();
            List<JpaRelationshipEvent> relEvents = getEntityRelationshipEvents();
            // Now save the data.
            HibernateUtil.withSessionDo((session, tx) -> saveChanges(session, tx, deep));
            // Finally, fire the events.
            fldEvents.forEach(fieldEventListeners::fireEntityEvent);
            relEvents.forEach(relationshipEventListeners::fireRelationshipEvent);
        }
        else if(deep) {
            HibernateUtil.withSessionDo((session, tx) -> getManyToOneFieldsStream().forEach(f -> saveChild(session, tx, f)));
        }
    }

    public void saveChanges() {
        saveChanges(true);
    }

    public void setAsDirty() {
        if(jpaState == CURRENT) jpaState = DIRTY;
    }

    @Transient
    public void setJpaState(@NotNull JpaState jpaState) {
        this.jpaState = jpaState;
    }

    protected void addValueToChangeMap(@NotNull String fieldName, @NotNull Class<?> fieldType, @Nullable Object originalValue, @Nullable Object newValue) {
        ChangedField ci = findChangeInfo(fieldName, fieldType);
        if(ci == null) changedFields.add(new ChangedField(fieldName, fieldType, originalValue, newValue));
        else ci.setNewValue(newValue);
    }

    protected boolean didValueChange(@NotNull String fieldName, @NotNull Class<?> fieldType) {
        return changedFields.stream().anyMatch(o -> o.equals(fieldName, fieldType));
    }

    protected @Nullable ChangedField findChangeInfo(@NotNull String fieldName, @NotNull Class<?> fieldType) {
        return changedFields.stream().filter(o -> o.equals(fieldName, fieldType)).findFirst().orElse(null);
    }

    @Transient
    protected @NotNull Set<ChangedField> getChangedFieldsForEvent() {
        return ((getJpaState() == CURRENT) ? new TreeSet<>() : changedFields.stream().filter(ChangedField::isJpa).collect(Collectors.toSet()));
    }

    @Transient
    protected @NotNull List<JpaRelationshipEvent> getEntityRelationshipEvents() {
        List<JpaRelationshipEvent> events = new ArrayList<>();
        getChangedFields().stream().filter(f -> f.isJpa() && !Objects.equals(f.getOldValue(), f.getNewValue())).forEach(f -> {
            JpaBase o = (JpaBase)f.getOldValue();
            JpaBase n = (JpaBase)f.getNewValue();
            if(o != null) events.add(new JpaRelationshipEvent(f.getFieldName(), this, o.getClass(), o, JpaEventType.RelationshipRemoved));
            if(n != null) events.add(new JpaRelationshipEvent(f.getFieldName(), this, n.getClass(), n, JpaEventType.RelationshipAdded));
        });
        return events;
    }

    @Transient
    protected @NotNull JpaEventType getEventType() {
        return (getJpaState() == JpaState.NEW) ? JpaEventType.Added : ((getJpaState() == JpaState.DELETED) ? JpaEventType.Removed : JpaEventType.Updated);
    }

    @Transient
    protected @NotNull List<JpaFieldEvent> getFieldEvent() {
        return changedFields.stream().filter(f -> !f.isJpa()).map(f -> new JpaFieldEvent(this, f, getEventType())).collect(Collectors.toList());
    }

    @Transient
    protected @NotNull Stream<Field> getManyToOneFieldsStream() {
        return Reflection.getFieldsWithAnyAnnotation(getClass(), ManyToOne.class).stream().filter(f -> JpaBase.class.isAssignableFrom(f.getType()));
    }

    protected @Nullable Object getOriginalValue(@NotNull String fieldName, @NotNull Class<?> fieldType) {
        return Null.get(findChangeInfo(fieldName, fieldType));
    }

    protected void saveChanges(@NotNull Session session, @NotNull Transaction tx, boolean deep) {
        if(deep) getManyToOneFieldsStream().forEach(f -> saveChild(session, tx, f));
        if(jpaState != CURRENT) {
            if(jpaState == DIRTY) session.merge(this); else session.persist(this);/*@f0*/
            if(jpaState != DELETED) session.refresh(this);/*@f1*/
            jpaState = CURRENT;
        }
        changedFields.clear();
    }

    private void saveChild(@NotNull Session session, @NotNull Transaction tx, Field field) {
        try { Null.doIfNotNull(((JpaBase)Reflection.getFieldValue(field, this)), o -> o.saveChanges(session, tx, true)); }
        catch(Exception e) { throw ((e instanceof DaoException) ? ((DaoException)e) : new DaoException(e)); }
    }
}
