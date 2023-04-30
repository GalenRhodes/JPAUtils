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

import com.projectgalen.lib.jpa.utils.HibernateUtil;
import com.projectgalen.lib.jpa.utils.enums.JpaState;
import com.projectgalen.lib.utils.PGProperties;
import com.projectgalen.lib.utils.PGResourceBundle;
import org.jetbrains.annotations.NotNull;

import static com.projectgalen.lib.jpa.utils.HibernateUtil.withSessionDo;

@SuppressWarnings("unused")
public class JpaBase {
    private static final PGProperties     props = PGProperties.getXMLProperties("settings.xml", HibernateUtil.class);
    private static final PGResourceBundle msgs  = PGResourceBundle.getXMLPGBundle("com.projectgalen.lib.jpa.utils.messages");

    protected JpaState jpaState;

    public JpaBase() {
        jpaState = JpaState.NORMAL;
    }

    public JpaBase(boolean dummy) {
        jpaState = (dummy ? JpaState.NORMAL : JpaState.NEW);
    }

    public void delete() {
        if(!(isNew() || isDeleted())) {
            withSessionDo((session, tx) -> session.remove(this));
            setJpaState(JpaState.DELETED);
        }
    }

    public @NotNull JpaState getJpaState() {
        return jpaState;
    }

    public void initialize() {
        HibernateUtil.initialize(this);
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

    public boolean isNormal() {
        return (jpaState == JpaState.NORMAL);
    }

    public void refresh() {
        HibernateUtil.refresh(this);
    }

    public void saveChanges(boolean deep) {
        HibernateUtil.saveChanges(this, deep);
    }

    public void saveChanges() {
        HibernateUtil.saveChanges(this, true);
    }

    public void setAsDirty() {
        if(isNormal()) jpaState = JpaState.DIRTY;
    }

    public void setJpaState(@NotNull JpaState jpaState) {
        this.jpaState = jpaState;
    }
}
