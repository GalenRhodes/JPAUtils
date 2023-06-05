package com.projectgalen.lib.jpa.utils.events;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: JpaChangedField.java
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@SuppressWarnings("unused")
public class ChangedField implements Comparable<ChangedField> {

    public final @NotNull  String   fieldName;
    public final @NotNull  Class<?> fieldClass;
    public final @Nullable Object   oldValue;
    public @Nullable       Object   newValue;

    public ChangedField(@NotNull String fieldName, @NotNull Class<?> fieldClass, @Nullable Object oldValue, @Nullable Object newValue) {
        this.fieldName  = fieldName;
        this.fieldClass = fieldClass;
        this.oldValue   = oldValue;
        this.newValue   = newValue;
    }

    @Override
    public int compareTo(@NotNull ChangedField o) {
        int cc = fieldName.compareTo(o.fieldName);
        return ((cc == 0) ? fieldClass.getName().compareTo(o.fieldClass.getName()) : cc);
    }

    public boolean equals(@NotNull String fieldName, @NotNull Class<?> fieldType) {
        return (fieldName.equals(this.fieldName) && (fieldType == this.fieldClass));
    }

    @Override
    public boolean equals(Object o) {
        return ((this == o) || ((o instanceof ChangedField) && _equals((ChangedField)o)));
    }

    public @NotNull Class<?> getFieldClass() {
        return fieldClass;
    }

    public @NotNull String getFieldName() {
        return fieldName;
    }

    public @Nullable Object getNewValue() {
        return newValue;
    }

    public @Nullable Object getOldValue() {
        return oldValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, fieldClass);
    }

    public boolean isJpa() {
        return JpaBase.class.isAssignableFrom(fieldClass);
    }

    public void setNewValue(@Nullable Object newValue) {
        this.newValue = newValue;
    }

    @Contract(pure = true)
    private boolean _equals(@NotNull ChangedField o) {
        return (Objects.equals(fieldName, o.fieldName) && (fieldClass == o.fieldClass));
    }
}
