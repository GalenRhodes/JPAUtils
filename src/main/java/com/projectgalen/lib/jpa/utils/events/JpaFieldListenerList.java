package com.projectgalen.lib.jpa.utils.events;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: JpaListenerList.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: June 02, 2023
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JpaFieldListenerList {
    private final Set<ListenerItem> listeners = new LinkedHashSet<>();

    public JpaFieldListenerList() { }

    public void addListener(@NotNull JpaEntityFieldListener listener, @Nullable String fieldName, @Nullable Class<?> fieldType, EventType... eventTypes) {
        synchronized(listeners) { listeners.add(new ListenerItem(listener, fieldName, fieldType, eventTypes)); }
    }

    public void fireEntityEvent(@NotNull JpaEntityFieldEvent event) {
        synchronized(listeners) { listeners.stream().filter(l -> l.matches(event)).forEach(l -> l.listener.handleEntityEvent(event)); }
    }

    public void removeListener(@NotNull JpaEntityFieldListener listener, @Nullable String fieldName, @Nullable Class<?> fieldType, EventType... eventTypes) {
        synchronized(listeners) { listeners.removeIf(l -> l.matches(listener, fieldName, fieldType, eventTypes)); }
    }

    private static final class ListenerItem {

        public final @NotNull  Set<EventType>         eventTypes;
        public final @NotNull  JpaEntityFieldListener listener;
        public final @Nullable String                 fieldName;
        public final @Nullable Class<?>               fieldType;

        public ListenerItem(@NotNull JpaEntityFieldListener listener, @Nullable String fieldName, @Nullable Class<?> fieldType, EventType[] eventTypes) {
            this.listener   = listener;
            this.fieldName  = fieldName;
            this.fieldType  = fieldType;
            this.eventTypes = new HashSet<>(Arrays.asList(eventTypes));
        }

        public boolean matches(JpaEntityFieldEvent event) {
            return (((this.fieldName == null) || this.fieldName.equals(event.getFieldName()))
                    && ((this.fieldType == null) || (this.fieldType == event.getFieldType()))
                    && this.eventTypes.contains(event.getEventType()));
        }

        public boolean matches(@NotNull JpaEntityFieldListener listener, @Nullable String fieldName, @Nullable Class<?> fieldType, EventType[] eventTypes) {
            return (Objects.equals(this.listener, listener)
                    && Objects.equals(this.fieldName, fieldName)
                    && Objects.equals(this.fieldType, fieldType)
                    && this.eventTypes.equals(new HashSet<>(Arrays.asList(eventTypes))));
        }
    }
}
