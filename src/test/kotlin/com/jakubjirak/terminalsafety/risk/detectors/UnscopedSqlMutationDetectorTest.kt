/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnscopedSqlMutationDetectorTest {
    private val d = UnscopedSqlMutationDetector()

    @Test fun deleteWithoutWhereFires() {
        assertTrue(d.detect("DELETE FROM orders;").any { it.id == "sql.delete-without-where" })
    }

    @Test fun deleteWithWhereDoesNotFire() {
        assertEquals(0, d.detect("DELETE FROM orders WHERE id = 5;").size)
    }

    @Test fun updateWithoutWhereFires() {
        assertTrue(d.detect("UPDATE users SET active = 0;").any { it.id == "sql.update-without-where" })
    }

    @Test fun updateWithWhereDoesNotFire() {
        assertEquals(0, d.detect("UPDATE users SET active = 0 WHERE id = 1;").size)
    }

    @Test fun caseInsensitive() {
        assertTrue(d.detect("delete from Orders").any { it.id == "sql.delete-without-where" })
    }

    @Test fun embeddedInPsql() {
        assertTrue(d.detect("psql -c \"DELETE FROM sessions\"").any { it.id == "sql.delete-without-where" })
    }

    @Test fun embeddedInMysql() {
        assertTrue(d.detect("mysql -e 'UPDATE accounts SET balance = 0'").any { it.id == "sql.update-without-where" })
    }

    @Test fun statementScopedWhereDoesNotMaskLaterUnscoped() {
        // First DELETE is safe; the second is not — only the second must fire.
        val s = d.detect("DELETE FROM a WHERE id=1; DELETE FROM b;")
        assertEquals(1, s.size)
        assertTrue(s.single().matched.contains("b"))
    }

    @Test fun safeStatementAfterUnscopedStillFiresForUnscoped() {
        val s = d.detect("DELETE FROM a; DELETE FROM b WHERE id=2;")
        assertEquals(1, s.size)
        assertTrue(s.single().matched.contains("a"))
    }

    @Test fun gitUpdateIndexDoesNotFire() {
        assertEquals(0, d.detect("git update-index --assume-unchanged file").size)
    }

    @Test fun kubectlDeleteDoesNotFire() {
        assertEquals(0, d.detect("kubectl delete pod x").size)
    }

    @Test fun reportsTableNameInDescription() {
        val sig = d.detect("DELETE FROM customers").first { it.id == "sql.delete-without-where" }
        assertTrue(sig.description.contains("customers"))
    }

    @Test fun quotedTableName() {
        assertTrue(d.detect("DELETE FROM `orders`").any { it.id == "sql.delete-without-where" })
    }

    @Test fun blankCommandNoFire() {
        assertEquals(0, d.detect("").size)
    }
}
