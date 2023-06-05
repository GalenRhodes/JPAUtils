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
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

@SuppressWarnings("unused")
public class JpaFieldEvent extends EventObject {

    private final           JpaEventType eventType;
    private final @NotNull  String       fieldName;
    private final @NotNull  Class<?>     fieldClass;
    private final @Nullable Object       oldValue;
    private @Nullable       Object       newValue;

    public JpaFieldEvent(JpaBase source, @NotNull ChangedField changedField, JpaEventType eventType) {
        super(source);
        this.eventType  = eventType;
        this.fieldName  = changedField.fieldName;
        this.fieldClass = changedField.fieldClass;
        this.oldValue   = changedField.oldValue;
        this.newValue   = changedField.newValue;
    }

    public JpaEventType getEventType() {
        return eventType;
    }

    public @NotNull String getFieldName() {
        return fieldName;
    }

    public @NotNull Class<?> getFieldClass() {
        return fieldClass;
    }

    public @Nullable Object getNewValue() {
        return newValue;
    }

    public @Nullable Object getOldValue() {
        return oldValue;
    }

    @Override
    public JpaBase getSource() {
        return (JpaBase)super.getSource();
    }

    public void setNewValue(@Nullable Object newValue) {
        this.newValue = newValue;
    }
}
