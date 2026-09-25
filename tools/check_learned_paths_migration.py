#!/usr/bin/env python3
"""Validate migration 14→15 for segmented walks and learned campus paths."""

import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
NEW_TABLES = {"learned_path_nodes", "learned_path_edges", "learned_walk_sessions"}
CHECK_TABLES = {"walk_points", *NEW_TABLES}


def quoted_sql_statements(text):
    return [
        json.loads('"' + raw + '"')
        for raw in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', text)
    ]


def migration_sql_statements(text):
    statements = []
    quoted = re.compile(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)')
    for match in quoted.finditer(text):
        statements.append((match.start(), json.loads('"' + match.group(1) + '"')))

    triple = re.compile(
        r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\s*\)',
        flags=re.S,
    )
    for match in triple.finditer(text):
        statements.append((match.start(), match.group(1).strip()))

    return [sql for _, sql in sorted(statements, key=lambda item: item[0])]


def table_indexes(db, table):
    return {row[1] for row in db.execute(f"PRAGMA index_list(`{table}`)").fetchall()}


def main():
    generated = ROOT / "app/build/generated/ksp/debug/java/com/uplb/punla/data/PunlaDatabase_Impl.java"
    generated_sql = quoted_sql_statements(generated.read_text())

    source = (ROOT / "app/src/main/java/com/uplb/punla/data/PunlaDatabase.kt").read_text()
    migration_source = source.split("val MIGRATION_14_15 =", 1)[1].split("fun get(context:", 1)[0]
    migration_sql = migration_sql_statements(migration_source)
    assert len(migration_sql) == 9, f"Expected 9 migration statements, found {len(migration_sql)}"

    full = sqlite3.connect(":memory:")
    upgraded = sqlite3.connect(":memory:")
    full.execute("PRAGMA foreign_keys=ON")
    upgraded.execute("PRAGMA foreign_keys=ON")

    skip_names = NEW_TABLES | {"walk_points"}
    for sql in generated_sql:
        if not (sql.startswith("CREATE TABLE") or sql.startswith("CREATE INDEX")):
            continue
        full.execute(sql)
        if not any(f"`{name}`" in sql for name in skip_names):
            upgraded.execute(sql)

    upgraded.execute(
        """CREATE TABLE IF NOT EXISTS `walk_points` (
            `sessionId` TEXT NOT NULL,
            `sequence` INTEGER NOT NULL,
            `lat` REAL NOT NULL,
            `lon` REAL NOT NULL,
            `accuracyMeters` REAL,
            `capturedAt` INTEGER NOT NULL,
            PRIMARY KEY(`sessionId`, `sequence`),
            FOREIGN KEY(`sessionId`) REFERENCES `walk_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )"""
    )
    upgraded.execute(
        "CREATE INDEX IF NOT EXISTS `index_walk_points_sessionId` ON `walk_points` (`sessionId`)"
    )

    upgraded.execute(
        "INSERT INTO walk_sessions "
        "(id,startedAt,endedAt,status,pausedAt,accumulatedPauseMillis,distanceMeters,pointCount) "
        "VALUES ('walk',1,2,'COMPLETED',NULL,0,12.0,1)"
    )
    upgraded.execute(
        "INSERT INTO walk_points "
        "(sessionId,sequence,lat,lon,accuracyMeters,capturedAt) "
        "VALUES ('walk',0,14.17,121.24,5.0,1)"
    )
    upgraded.execute(
        "INSERT INTO deadlines (id,title,due,type,priority,done,isRecurring) "
        "VALUES ('existing','Keep this','2026-09-26','Task','High',0,0)"
    )

    with upgraded:
        for sql in migration_sql:
            upgraded.execute(sql)

    for table in CHECK_TABLES:
        assert full.execute(f"PRAGMA table_info(`{table}`)").fetchall() == upgraded.execute(
            f"PRAGMA table_info(`{table}`)"
        ).fetchall(), table
        assert full.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall() == upgraded.execute(
            f"PRAGMA foreign_key_list(`{table}`)"
        ).fetchall(), table
        assert table_indexes(full, table) == table_indexes(upgraded, table), table

    assert upgraded.execute(
        "SELECT segment FROM walk_points WHERE sessionId='walk' AND sequence=0"
    ).fetchone() == (0,)
    assert upgraded.execute(
        "SELECT title FROM deadlines WHERE id='existing'"
    ).fetchone() == ("Keep this",)
    assert upgraded.execute("PRAGMA foreign_key_check").fetchall() == []
    assert upgraded.execute("PRAGMA quick_check").fetchone() == ("ok",)

    print(
        "PASS: learned-path migration matches Room; legacy walk segment defaults to 0; "
        "existing data preserved; foreign keys and quick_check=ok"
    )


if __name__ == "__main__":
    main()
