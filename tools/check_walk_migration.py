#!/usr/bin/env python3
"""Validate migration 13→14 for Punla walk recording against Room's generated schema."""

import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
TABLES = {"walk_sessions", "walk_points"}
NEWER_TABLES = {"learned_path_nodes", "learned_path_edges", "learned_walk_sessions"}


def quoted_sql_statements(text):
    return [
        json.loads('"' + raw + '"')
        for raw in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', text)
    ]


def migration_sql_statements(text):
    """Return quoted and Kotlin triple-quoted execSQL calls in source order."""
    statements = []

    quoted = re.compile(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)')
    for match in quoted.finditer(text):
        statements.append(
            (match.start(), json.loads('"' + match.group(1) + '"'))
        )

    triple = re.compile(
        r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\s*\)',
        flags=re.S,
    )
    for match in triple.finditer(text):
        statements.append((match.start(), match.group(1).strip()))

    return [sql for _, sql in sorted(statements, key=lambda item: item[0])]


def column_shape(rows, excluded=None):
    excluded = excluded or set()
    return {
        row[1]: (row[2], row[3], row[4], row[5])
        for row in rows
        if row[1] not in excluded
    }


def main():
    generated = ROOT / "app/build/generated/ksp/debug/java/com/uplb/punla/data/PunlaDatabase_Impl.java"
    generated_sql = quoted_sql_statements(generated.read_text())

    source = (ROOT / "app/src/main/java/com/uplb/punla/data/PunlaDatabase.kt").read_text()
    migration_source = source.split("val MIGRATION_13_14 =", 1)[1].split("val MIGRATION_14_15 =", 1)[0]
    migration_sql = migration_sql_statements(migration_source)

    assert len(migration_sql) == 5, (
        f"Expected 5 walk migration statements, found {len(migration_sql)}"
    )

    full = sqlite3.connect(":memory:")
    upgraded = sqlite3.connect(":memory:")
    full.execute("PRAGMA foreign_keys=ON")
    upgraded.execute("PRAGMA foreign_keys=ON")

    for sql in generated_sql:
        if not (sql.startswith("CREATE TABLE") or sql.startswith("CREATE INDEX")):
            continue
        full.execute(sql)
        if not any(f"`{name}`" in sql for name in TABLES | NEWER_TABLES):
            upgraded.execute(sql)

    # Prove an unrelated v13 row survives the upgrade.
    upgraded.execute(
        "INSERT INTO deadlines (id,title,due,type,priority,done,isRecurring) "
        "VALUES ('existing','Keep this','2026-09-25','Task','High',0,0)"
    )

    with upgraded:
        for sql in migration_sql:
            upgraded.execute(sql)

    for table in TABLES:
        excluded = {"segment"} if table == "walk_points" else set()
        expected_columns = column_shape(
            full.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
            excluded,
        )
        actual_columns = column_shape(
            upgraded.execute(f"PRAGMA table_info(`{table}`)").fetchall()
        )
        assert expected_columns == actual_columns, table
        assert full.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall() == upgraded.execute(
            f"PRAGMA foreign_key_list(`{table}`)"
        ).fetchall(), table

        expected_indexes = {
            row[1]
            for row in full.execute(f"PRAGMA index_list(`{table}`)").fetchall()
        }
        upgraded_indexes = {
            row[1]
            for row in upgraded.execute(f"PRAGMA index_list(`{table}`)").fetchall()
        }
        assert expected_indexes == upgraded_indexes, table

    assert upgraded.execute(
        "SELECT title FROM deadlines WHERE id='existing'"
    ).fetchone() == ("Keep this",)
    assert upgraded.execute("PRAGMA quick_check").fetchone() == ("ok",)

    print(
        "PASS: walk migration matches Room; existing data preserved; "
        "SQLite quick_check=ok"
    )


if __name__ == "__main__":
    main()
