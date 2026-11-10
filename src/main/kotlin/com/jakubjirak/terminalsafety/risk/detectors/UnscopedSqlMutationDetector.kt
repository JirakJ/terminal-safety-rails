/*
 * Copyright 2026 Jakub Jirák
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package com.jakubjirak.terminalsafety.risk.detectors

import com.jakubjirak.terminalsafety.risk.RiskSignal
import com.jakubjirak.terminalsafety.risk.RiskSignalDetector

/**
 * Flags SQL data-mutation statements that are missing a `WHERE` clause —
 * the `DELETE FROM orders;` / `UPDATE users SET active=0;` class of mistake
 * that wipes or rewrites an entire table in one keystroke.
 *
 * The detector is statement-scoped: for each `DELETE FROM <table>` or
 * `UPDATE <table> SET …` it inspects only that statement (up to the next `;`
 * or end of input) for a `WHERE` clause, so a safe statement earlier in the
 * line does not mask an unscoped one later, and vice-versa.
 *
 * It is intentionally conservative — it does **not** fire when a `WHERE`
 * clause is present, and it ignores `DELETE`/`UPDATE` that are not real SQL
 * verbs (e.g. `git update-index`, `kubectl delete`) because those require the
 * `FROM`/`SET` keyword to match.
 *
 * Recognises statements embedded in shell invocations such as
 * `psql -c "DELETE FROM t"` and `mysql -e 'UPDATE t SET x=1'` because it scans
 * the whole command string.
 *
 * Pure, deterministic, no IntelliJ dependencies.
 */
class UnscopedSqlMutationDetector : RiskSignalDetector {
    override val id: String = "unscoped-sql-mutation"

    override fun detect(command: String): List<RiskSignal> {
        val out = mutableListOf<RiskSignal>()

        for (m in DELETE_FROM.findAll(command)) {
            if (!statementHasWhere(command, m.range.last)) {
                out += RiskSignal(
                    id = "sql.delete-without-where",
                    description = "SQL DELETE without a WHERE clause — every row in `${m.groupValues[1]}` is removed",
                    weight = 60,
                    matched = m.value.trim(),
                )
            }
        }

        for (m in UPDATE_SET.findAll(command)) {
            if (!statementHasWhere(command, m.range.last)) {
                out += RiskSignal(
                    id = "sql.update-without-where",
                    description = "SQL UPDATE without a WHERE clause — every row in `${m.groupValues[1]}` is rewritten",
                    weight = 55,
                    matched = m.value.trim(),
                )
            }
        }

        return out
    }

    /**
     * Returns true when the SQL statement starting at [matchEnd] contains a
     * `WHERE` keyword before the next statement terminator (`;`) or end of input.
     */
    private fun statementHasWhere(command: String, matchEnd: Int): Boolean {
        val rest = command.substring(matchEnd)
        val terminator = rest.indexOf(';')
        val statement = if (terminator >= 0) rest.substring(0, terminator) else rest
        return WHERE_CLAUSE.containsMatchIn(statement)
    }

    private companion object {
        val DELETE_FROM = Regex("""\bdelete\s+from\s+([`"\[\]\w.]+)""", RegexOption.IGNORE_CASE)
        val UPDATE_SET = Regex("""\bupdate\s+([`"\[\]\w.]+)\s+set\b""", RegexOption.IGNORE_CASE)
        val WHERE_CLAUSE = Regex("""\bwhere\b""", RegexOption.IGNORE_CASE)
    }
}
