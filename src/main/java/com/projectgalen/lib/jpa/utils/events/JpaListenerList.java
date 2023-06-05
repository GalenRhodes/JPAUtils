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

import com.projectgalen.lib.jpa.utils.base.JpaBase;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public final class JpaListenerList<S extends JpaBase> {
    private final Set<JpaEntityListener<S>> listeners = new LinkedHashSet<>();

    public JpaListenerList() { }

    public void addListener(@NotNull JpaEntityListener<S> listener) { synchronized(listeners) { listeners.add(listener); } }

    public void fireEntityEvent(@NotNull JpaEntityEvent<S> event) { getListenerStream().forEach(l -> l.handleEntityEvent(event)); }

    public @NotNull Set<JpaEntityListener<S>> getListenerSet() { synchronized(listeners) { return getListenerStream().collect(Collectors.toSet()); } }

    public @NotNull JpaEntityListener<S> @NotNull [] getListeners() { synchronized(listeners) { return getListenerStream().toArray(JpaEntityListener[]::new); } }

    public void removeListener(@NotNull JpaEntityListener<S> listener) { synchronized(listeners) { listeners.remove(listener); } }

    private @NotNull Stream<JpaEntityListener<S>> getListenerStream() { return listeners.stream(); }
}
