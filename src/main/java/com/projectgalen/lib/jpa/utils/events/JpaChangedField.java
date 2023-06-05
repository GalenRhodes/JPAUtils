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
    public final @NotNull  String fieldName;
    public final @Nullable T      oldValue;
    public final @Nullable T      newValue;

    public JpaChangedField(@NotNull String fieldName, @Nullable T oldValue, @Nullable T newValue) {
        this.fieldName = fieldName;
        this.oldValue  = oldValue;
        this.newValue  = newValue;
    }

    @Override
    public int compareTo(@NotNull JpaChangedField<T> o) {
        return fieldName.compareTo(o.fieldName);
    }

    @Override
    public boolean equals(Object o) {
        return ((this == o) || ((o instanceof JpaChangedField) && Objects.equals(fieldName, ((JpaChangedField<?>)o).fieldName)));
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
        return Objects.hash(fieldName);
    }
}
