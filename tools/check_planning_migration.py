#!/usr/bin/env python3
"""Check migration 12→13 against Room-generated DDL using real SQLite.

Run after `gradle kspDebugKotlin`. This complements, not replaces, an Android
upgrade/restore test with an actual version-12 user database.
"""
import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
TABLES = {"task_preferences", "inbox_captures", "day_plan_blocks", "life_commitments", "os_settings"}


def sql_statements(text):
    return [json.loads('"' + raw + '"') for raw in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', text)]


def main():
    generated = ROOT / "app/build/generated/ksp/debug/java/com/uplb/punla/data/PunlaDatabase_Impl.java"
    expected = sql_statements(generated.read_text())
    migration_source = (ROOT / "app/src/main/java/com/uplb/punla/data/PunlaDatabase.kt").read_text().split("val MIGRATION_12_13 =", 1)[1].split("fun get(context:", 1)[0]
    migration = sql_statements(migration_source)
    assert len(migration) == len(TABLES), "Missing planning migration statements"
    full, upgraded = sqlite3.connect(":memory:"), sqlite3.connect(":memory:")
    for sql in expected:
        if sql.startswith("CREATE TABLE"):
            full.execute(sql)
            if not any(f"`{name}`" in sql for name in TABLES):
                upgraded.execute(sql)
    upgraded.execute("INSERT INTO deadlines (id,title,due,type,priority,done,isRecurring) VALUES ('existing','Keep this','2026-09-24','Task','High',0,0)")
    old_schema = dict(upgraded.execute("SELECT name, sql FROM sqlite_master WHERE type='table'"))
    with upgraded:
        for sql in migration:
            upgraded.execute(sql)
    for table in TABLES:
        assert full.execute(f"PRAGMA table_info(`{table}`)").fetchall() == upgraded.execute(f"PRAGMA table_info(`{table}`)").fetchall(), table
        assert full.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall() == upgraded.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(), table
    new_schema = dict(upgraded.execute("SELECT name, sql FROM sqlite_master WHERE type='table'"))
    assert all(new_schema[name] == sql for name, sql in old_schema.items()), "Existing schema changed"
    assert upgraded.execute("SELECT title FROM deadlines WHERE id='existing'").fetchone() == ("Keep this",)
    assert upgraded.execute("PRAGMA quick_check").fetchone() == ("ok",)
    print("PASS: five migration tables match Room; existing schema and deadline preserved; SQLite quick_check=ok")


if __name__ == "__main__":
    main()
