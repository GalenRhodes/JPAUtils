package com.projectgalen.lib.jpa.utils.errors;

// ===========================================================================
//     PROJECT: PGBudgetDB
//    FILENAME: SvcConstraintViolation.java
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

@SuppressWarnings("unused")
public class SvcConstraintViolation extends DaoException {
    private final String data;
    private final String table;
    private final String column;
    private final String constraint;

    public SvcConstraintViolation(String data, String table, String column, String constraint) {
        this.data       = data;
        this.table      = table;
        this.column     = column;
        this.constraint = constraint;
    }

    public String getColumn() {
        return column;
    }

    public String getConstraint() {
        return constraint;
    }

    public String getData() {
        return data;
    }

    public String getTable() {
        return table;
    }
}
