package com.projectgalen.lib.jpa.utils.base;

// ===========================================================================
//     PROJECT: PGBudgetDB
//    FILENAME: Base.java
//         IDE: IntelliJ IDEA
//      AUTHOR: Galen Rhodes
//        DATE: February 10, 2023
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

import com.projectgalen.lib.jpa.utils.enums.JpaState;
import jakarta.persistence.Column;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@SuppressWarnings("unused")
public class JpaBase {

    protected JpaState jpaState;

    public JpaBase() {
        jpaState = JpaState.NORMAL;
    }

    public JpaBase(boolean dummy) {
        jpaState = (dummy ? JpaState.NORMAL : JpaState.NEW);
    }

    public @Override boolean equals(@Nullable Object o) {
        try {
            if(this == o) return true;
            if((o == null) || (getClass() != o.getClass())) return false;

            Map<String, Field> fieldsA = getComparableFields();
            Map<String, Field> fieldsB = ((JpaBase)o).getComparableFields();

            if(fieldsA.size() != fieldsB.size()) return false;

            for(Map.Entry<String, Field> a : fieldsA.entrySet()) {
                Field fb = fieldsB.get(a.getKey());
                if((fb == null) || !Objects.equals(a.getValue().get(this), fb.get(o))) return false;
            }

            return true;
        }
        catch(Exception ignore) {
            return false;
        }
    }

    public @Override int hashCode() {
        try {
            int h = 1;
            for(Map.Entry<String, Field> e : getComparableFields().entrySet()) {
                Object o = e.getValue().get(this);
                h = ((31 * h) + ((o == null) ? 0 : o.hashCode()));
            }
            return h;
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull JpaState getJpaState() {
        return jpaState;
    }

    public boolean isNormal() {
        return (jpaState == JpaState.NORMAL);
    }

    public boolean isDeleted() {
        return (jpaState == JpaState.DELETED);
    }

    public boolean isDirty() {
        return ((jpaState == JpaState.DIRTY) || isNew());
    }

    public boolean isNew() {
        return (jpaState == JpaState.NEW);
    }

    public void setJpaState(@NotNull JpaState jpaState) {
        this.jpaState = jpaState;
    }

    protected @NotNull Map<String, Field> getComparableFields() {
        Class<?>           cls    = getClass();
        Map<String, Field> fields = new TreeMap<>();

        while(cls != null) {
            for(Field f : cls.getDeclaredFields()) {
                if(f.isAnnotationPresent(Column.class) && !fields.containsKey(f.getName())) {
                    fields.put(f.getName(), f);
                }
            }
            cls = cls.getSuperclass();
        }

        return fields;
    }

    public void setAsDirty() {
        if(isNormal()) jpaState = JpaState.DIRTY;
    }
}
