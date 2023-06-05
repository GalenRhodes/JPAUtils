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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public final class JpaRelationshipListenerList<S extends JpaBase, T extends JpaBase> {

    private final Set<JpaEntityRelationshipListener<S, T>> listeners = new LinkedHashSet<>();

    public JpaRelationshipListenerList() { }

    public void addListener(@NotNull JpaEntityRelationshipListener<S, T> listener) { synchronized(listeners) { listeners.add(listener); } }

    public void fireEntityRelationshipEvent(@NotNull JpaEntityRelationshipEvent<S, T> event) { getListenerStream().forEach(l -> l.handleEntityRelationshipEvent(event)); }

    public @NotNull Set<JpaEntityRelationshipListener<S, T>> getListenerSet() { synchronized(listeners) { return getListenerStream().collect(Collectors.toSet()); } }

    public @NotNull JpaEntityRelationshipListener<S, T> @NotNull [] getListeners() { synchronized(listeners) { return getListenerStream().toArray(JpaEntityRelationshipListener[]::new); } }

    public void removeListener(@NotNull Class<S> entityClass, @NotNull JpaEntityRelationshipListener<S, T> listener) { synchronized(listeners) { listeners.remove(listener); } }

    private @NotNull Stream<JpaEntityRelationshipListener<S, T>> getListenerStream() { return listeners.stream(); }
}
