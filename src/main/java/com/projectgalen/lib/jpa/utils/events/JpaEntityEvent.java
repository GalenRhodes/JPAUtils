package com.projectgalen.lib.jpa.utils.events;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: JpaEntityEvent.java
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

import java.util.Collections;
import java.util.EventObject;
import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings("unchecked")
public class JpaEntityEvent<S extends JpaBase> extends EventObject {
    private final EventType   eventType;
    private final Set<String> changedFields;

    public JpaEntityEvent(S source, EventType eventType) {
        this(source, new TreeSet<>(), eventType);
    }

    public JpaEntityEvent(S source, @NotNull Set<String> changedFields, EventType eventType) {
        super(source);
        this.eventType     = eventType;
        this.changedFields = Collections.unmodifiableSet(changedFields);
    }

    public Set<String> getChangedFields() {
        return changedFields;
    }

    public EventType getEventType() {
        return eventType;
    }

    @Override
    public S getSource() {
        return (S)super.getSource();
    }
}
