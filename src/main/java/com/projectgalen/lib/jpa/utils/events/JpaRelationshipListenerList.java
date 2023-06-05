package com.projectgalen.lib.jpa.utils.events;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: JpaRelationshipListenerList.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: June 04, 2023
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("unused")
public final class JpaRelationshipListenerList {

    private final Set<ListenerItem> listeners = new LinkedHashSet<>();

    public JpaRelationshipListenerList() { }

    public void addListener(@NotNull JpaEntityRelationshipListener listener, @Nullable String sourceFieldName, @Nullable Class<? extends JpaBase> targetClass, EventType... eventTypess) {
        synchronized(listeners) { listeners.add(new ListenerItem(listener, sourceFieldName, targetClass, eventTypess)); }
    }

    public void fireRelationshipEvent(@NotNull JpaEntityRelationshipEvent event) {
        synchronized(listeners) {
            listeners.stream().filter(l -> l.matches(event.getFieldName(), event.getTargetClass(), event.getEventType())).forEach(l -> l.listener.handleEntityRelationshipEvent(event));
        }
    }

    public void removeListener(@NotNull JpaEntityRelationshipListener listener, @Nullable String sourceFieldName, @Nullable Class<? extends JpaBase> targetClass, EventType... eventTypes) {
        synchronized(listeners) { listeners.removeIf(l -> l.matches(listener, sourceFieldName, targetClass, eventTypes)); }
    }

    private static final class ListenerItem {

        public final @NotNull  JpaEntityRelationshipListener listener;
        public final @Nullable String                        fieldName;
        public final @Nullable Class<? extends JpaBase>      targetClass;
        public final @NotNull  Set<EventType>                eventTypes;

        public ListenerItem(@NotNull JpaEntityRelationshipListener listener, @Nullable String fieldName, @Nullable Class<? extends JpaBase> targetClass, EventType[] eventTypes) {
            this.listener    = listener;
            this.fieldName   = fieldName;
            this.targetClass = targetClass;
            this.eventTypes  = new HashSet<>(Arrays.asList(eventTypes));
        }

        public boolean matches(@Nullable String fieldName, @Nullable Class<? extends JpaBase> targetClass, EventType eventType) {
            return (((this.fieldName == null) || this.fieldName.equals(fieldName))/*@f0*/
                    && ((this.targetClass == null) || (this.targetClass == targetClass))
                    && (eventTypes.isEmpty() || eventTypes.contains(eventType)));
        }/*@f1*/

        public boolean matches(@NotNull JpaEntityRelationshipListener listener, @Nullable String fieldName, @Nullable Class<? extends JpaBase> targetClass, EventType[] eventTypes) {
            return (this.listener.equals(listener)
                    && Objects.equals(this.fieldName, fieldName)
                    && Objects.equals(this.targetClass, targetClass)
                    && this.eventTypes.equals(new HashSet<>(Arrays.asList(eventTypes))));
        }
    }
}
