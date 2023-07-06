package com.projectgalen.lib.jpa.utils;

// ===========================================================================
//     PROJECT: PGBudgetDB
//    FILENAME: HibernateUtils.java
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

import com.projectgalen.lib.jpa.utils.base.JpaBase;
import com.projectgalen.lib.jpa.utils.base._HibernateUtil;
import com.projectgalen.lib.jpa.utils.interfaces.StreamConsumer;
import com.projectgalen.lib.jpa.utils.interfaces.VoidStreamConsumer;
import com.projectgalen.lib.utils.reflection.Reflection;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({ "unused", "UnusedReturnValue" })
public final class HibernateUtil extends _HibernateUtil {

    private HibernateUtil() { }

    public static <E> @NotNull List<E> fetch(@NotNull Session session, @NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        return stream(session, clazz, hql, params, startingRow, maxRows).collect(Collectors.toList());
    }

    public static <E> @NotNull List<E> fetch(@NotNull Session session, @NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params) {
        return fetch(session, clazz, hql, params, 0, 0);
    }

    public static <E> @NotNull List<E> fetch(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow, int maxRows) {
        return withSessionGet(session -> fetch(session, clazz, hql, params, startingRow, maxRows));
    }

    public static <E> @NotNull List<E> fetch(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params) {
        return fetch(clazz, hql, params, 0, 0);
    }

    public static <E> @Nullable E getFirst(@NotNull Session session, @NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow) {
        return stream(session, clazz, hql, params, startingRow, 1).findFirst().orElse(null);
    }

    public static <E> @Nullable E getFirst(@NotNull Session session, @NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params) {
        return getFirst(session, clazz, hql, params, 0);
    }

    public static <E> @Nullable E getFirst(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow) {
        return withSessionGet(session -> getFirst(session, clazz, hql, params, startingRow));
    }

    public static <E> @Nullable E getFirst(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params) {
        return getFirst(clazz, hql, params, 0);
    }

    public static @Transient boolean isChildField(@NotNull Field f) {
        return (Reflection.hasAnyAnnotation(f, ManyToOne.class, OneToOne.class, OneToMany.class, ManyToMany.class) && JpaBase.class.isAssignableFrom(f.getType()));
    }

    public static void saveAll() {
        withSessionDo(_HibernateUtil::saveAll);
    }

    public static <E> @NotNull Stream<E> stream(@NotNull Session session, @NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params) {
        return stream(session, clazz, hql, params, 0, 0);
    }

    public static <E> void withStreamDo(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow, int maxRows, @NotNull VoidStreamConsumer<E> consumer) {
        withSessionGet(session -> consumer.getWithStream(session, stream(session, clazz, hql, params, startingRow, maxRows)));
    }

    public static <E> void withStreamDo(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, @NotNull VoidStreamConsumer<E> consumer) {
        withStreamDo(clazz, hql, params, 0, 0, consumer);
    }

    public static <E, R> R withStreamGet(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, int startingRow, int maxRows, @NotNull StreamConsumer<E, R> consumer) {
        return withSessionGet(session -> consumer.getWithStream(session, stream(session, clazz, hql, params, startingRow, maxRows)));
    }

    public static <E, R> R withStreamGet(@NotNull Class<E> clazz, @NotNull String hql, @NotNull Map<String, Object> params, @NotNull StreamConsumer<E, R> consumer) {
        return withStreamGet(clazz, hql, params, 0, 0, consumer);
    }
}
