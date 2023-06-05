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

    public void addListener(@NotNull JpaRelationshipListener listener, @Nullable String sourceFieldName, @Nullable Class<? extends JpaBase> targetClass, JpaEventType... eventTypes) {
        synchronized(listeners) { listeners.add(new ListenerItem(listener, sourceFieldName, targetClass, eventTypes)); }
    }

    public void fireRelationshipEvent(@NotNull JpaRelationshipEvent event) {
        synchronized(listeners) { listeners.stream().filter(l -> l.matches(event)).forEach(l -> l.listener.handleEntityRelationshipEvent(event)); }
    }

    public void removeListener(@NotNull JpaRelationshipListener listener, @Nullable String sourceFieldName, @Nullable Class<? extends JpaBase> targetClass, JpaEventType... eventTypes) {
        synchronized(listeners) { listeners.removeIf(l -> l.matches(listener, sourceFieldName, targetClass, eventTypes)); }
    }

    private static final class ListenerItem {

        public final @NotNull  JpaRelationshipListener  listener;
        public final @Nullable String                   fieldName;
        public final @Nullable Class<? extends JpaBase> targetClass;
        public final @NotNull  Set<JpaEventType>        eventTypes;

        public ListenerItem(@NotNull JpaRelationshipListener listener, @Nullable String fieldName, @Nullable Class<? extends JpaBase> targetClass, JpaEventType[] eventTypes) {
            this.listener    = listener;
            this.fieldName   = fieldName;
            this.targetClass = targetClass;
            this.eventTypes  = new HashSet<>(Arrays.asList(eventTypes));
        }

        public boolean matches(@NotNull JpaRelationshipEvent event) {
            return (((this.fieldName == null) || this.fieldName.equals(event.getFieldName()))/*@f0*/
                    && ((this.targetClass == null) || (this.targetClass == event.getTargetClass()))
                    && (eventTypes.isEmpty() || eventTypes.contains(event.getEventType())));
        }/*@f1*/

        public boolean matches(@NotNull JpaRelationshipListener listener, @Nullable String fieldName, @Nullable Class<? extends JpaBase> targetClass, JpaEventType[] eventTypes) {
            return (this.listener.equals(listener)
                    && Objects.equals(this.fieldName, fieldName)
                    && Objects.equals(this.targetClass, targetClass)
                    && this.eventTypes.equals(new HashSet<>(Arrays.asList(eventTypes))));
        }
    }
}
