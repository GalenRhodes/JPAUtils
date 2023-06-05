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

import java.util.EventObject;

public class JpaRelationshipEvent extends EventObject {
    private final String                   fieldName;
    private final Class<? extends JpaBase> targetClass;
    private final JpaBase                  target;
    private final JpaEventType             eventType;

    public JpaRelationshipEvent(String fieldName, JpaBase source, Class<? extends JpaBase> targetClass, JpaBase target, JpaEventType eventType) {
        super(source);
        if(!U.isObjIn(eventType, JpaEventType.RelationshipAdded, JpaEventType.RelationshipRemoved)) throw new IllegalArgumentException(HibernateUtil.msgs.format("msg.err.bad_event_type", eventType));
        this.fieldName   = fieldName;
        this.target      = target;
        this.targetClass = targetClass;
        this.eventType   = eventType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public JpaEventType getEventType() {
        return eventType;
    }

    @Override
    public JpaBase getSource() {
        return (JpaBase)super.getSource();
    }

    public JpaBase getTarget() {
        return target;
    }

    public Class<? extends JpaBase> getTargetClass() {
        return targetClass;
    }
}
