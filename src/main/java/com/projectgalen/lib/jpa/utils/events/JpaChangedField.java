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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@SuppressWarnings("unused")
public class JpaChangedField<T> implements Comparable<JpaChangedField<T>> {

    public final @NotNull  String  fieldName;
    public final           boolean isJpa;
    public final @Nullable T       oldValue;
    public @Nullable       T       newValue;

    public JpaChangedField(@NotNull String fieldName, boolean isJpa, @Nullable T oldValue, @Nullable T newValue) {
        this.fieldName = fieldName;
        this.oldValue  = oldValue;
        this.newValue  = newValue;
        this.isJpa     = isJpa;
    }

    @Override
    public int compareTo(@NotNull JpaChangedField<T> o) {
        int cc = fieldName.compareTo(o.fieldName);
        return ((cc == 0) ? Boolean.compare(isJpa, o.isJpa) : cc);
    }

    @Override
    public boolean equals(Object o) {
        return ((this == o) || ((o instanceof JpaChangedField) && _equals((JpaChangedField<?>)o)));
    }

    public @NotNull String getFieldName() {
        return fieldName;
    }

    public @Nullable T getNewValue() {
        return newValue;
    }

    public @Nullable T getOldValue() {
        return oldValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, isJpa);
    }

    public boolean isJpa() {
        return isJpa;
    }

    public void setNewValue(@Nullable T newValue) {
        this.newValue = newValue;
    }

    private boolean _equals(JpaChangedField<?> oo) {
        return (Objects.equals(fieldName, oo.fieldName) && (isJpa == oo.isJpa));
    }
}
