package com.projectgalen.lib.jpa.utils.events;

// ===========================================================================
//     PROJECT: JPAUtils
//    FILENAME: JpaEntityRelationshipEvent.java
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

import com.projectgalen.lib.jpa.utils.HibernateUtil;
import com.projectgalen.lib.jpa.utils.base.JpaBase;
import com.projectgalen.lib.utils.U;

public class JpaEntityRelationshipEvent<S extends JpaBase, T extends JpaBase> extends JpaEntityEvent<S> {
    private final T target;

    public JpaEntityRelationshipEvent(S source, T target, EventType eventType) {
        super(source, eventType);
        this.target = target;
        if(!U.isObjIn(eventType, EventType.RelationshipAdded, EventType.RelationshipRemoved)) throw new IllegalArgumentException(HibernateUtil.msgs.format("msg.err.bad_event_type", eventType));
    }

    public T getTarget() {
        return target;
    }
}
