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

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public final class JpaListenerList {
    private final Set<ListenerData> listeners = new LinkedHashSet<>();

    public JpaListenerList() { }

    public void addListener(@NotNull Class<? extends EventListener> listenerClass, @NotNull EventListener listener) {
        synchronized(listeners) { listeners.add(new ListenerData(listenerClass, listener)); }
    }

    public <L extends EventListener, E extends EventObject> void fireEvent(@NotNull Class<L> listenerClass, @NotNull E event, @NotNull ListenerCaller<L, E> delegate) {
        getList(listenerClass).forEach(listener -> delegate.callListener(listener, event));
    }

    public <T extends EventListener> T @NotNull [] getArray(@NotNull Class<T> listenerClass) {
        return getList(listenerClass).toArray((T[])Array.newInstance(listenerClass, 0));
    }

    public <T extends EventListener> List<T> getList(@NotNull Class<T> listenerClass) {
        synchronized(listeners) { return listeners.stream().filter(l -> l.listenerClass == listenerClass).map(l -> (T)l.listener).collect(Collectors.toList()); }
    }

    public <T extends EventListener> Stream<T> getStream(@NotNull Class<T> listenerClass) {
        return getList(listenerClass).stream();
    }

    public void removeListener(@NotNull Class<? extends EventListener> listenerClass, @NotNull EventListener listener) {
        synchronized(listeners) { listeners.remove(new ListenerData(listenerClass, listener)); }
    }

    public interface ListenerCaller<L extends EventListener, E extends EventObject> {
        void callListener(@NotNull L listener, @NotNull E event);
    }

    private static final class ListenerData {
        private final Class<? extends EventListener> listenerClass;
        private final EventListener                  listener;

        public ListenerData(Class<? extends EventListener> listenerClass, EventListener listener) {
            this.listenerClass = listenerClass;
            this.listener      = listener;
        }

        @Override
        public boolean equals(Object o) {
            return ((this == o) || ((o instanceof ListenerData) && _equals((ListenerData)o)));
        }

        public EventListener getListener() {
            return listener;
        }

        public Class<? extends EventListener> getListenerClass() {
            return listenerClass;
        }

        @Override
        public int hashCode() {
            return Objects.hash(listenerClass, listener);
        }

        private boolean _equals(@NotNull ListenerData other) {
            return (Objects.equals(listenerClass, other.listenerClass) && Objects.equals(listener, other.listener));
        }
    }
}
