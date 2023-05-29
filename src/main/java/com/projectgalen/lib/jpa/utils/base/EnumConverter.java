package com.projectgalen.lib.jpa.utils.base;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: EnumConverter.java
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

import com.projectgalen.lib.utils.PGResourceBundle;
import jakarta.persistence.AttributeConverter;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public abstract class EnumConverter<E extends Enum<E>, T> implements AttributeConverter<E, T> {

    private static final @NotNull PGResourceBundle msgs = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");
    private final                 T                defaultValue;

    public EnumConverter(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    public EnumConverter() {
        this(null);
    }

    @Override
    public T convertToDatabaseColumn(E attribute) {
        return ((attribute == null) ? defaultValue : getValue(attribute));
    }

    @Override
    public E convertToEntityAttribute(T dbData) {
        if(dbData == null) dbData = defaultValue;
        if(dbData == null) return null;
        for(E e : getList()) if(getValue(e).equals(dbData)) return e;
        if(dbData.equals(defaultValue)) return null;
        throw new IllegalArgumentException(msgs.format("msg.err.not_supported", dbData));
    }

    public abstract E[] getList();

    public abstract T getValue(@NotNull E attribute);
}
