"""Capture schema metadata only from the project's local MySQL container.

Does not read business rows or credentials into this process/output.
Run after applying migrations: python3 docs/business/diagrams/capture_schema.py
"""
import json
from pathlib import Path
import subprocess
from datetime import datetime
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent.parent / 'database-schema.snapshot.json'


def query(sql):
    command = ['docker', 'compose', 'exec', '-T', 'mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=TCP -h127.0.0.1 -uhealth_app chronic_health "$@"',
               'sh', '-N', '-B', '-r', '-e', sql]
    result = subprocess.run(command, cwd=ROOT, check=True, text=True, capture_output=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line]


if __name__ == '__main__':
    scope = "TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history'"
    tables = query(f"SELECT JSON_OBJECT('name',TABLE_NAME) FROM information_schema.TABLES WHERE {scope} ORDER BY TABLE_NAME")
    columns = query(f"""SELECT JSON_OBJECT('table',TABLE_NAME,'name',COLUMN_NAME,'type',COLUMN_TYPE,
        'nullable',IS_NULLABLE='YES','default',COLUMN_DEFAULT,'extra',EXTRA,'comment',COLUMN_COMMENT)
        FROM information_schema.COLUMNS WHERE {scope} ORDER BY TABLE_NAME,ORDINAL_POSITION""")
    indexes = query(f"""SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'column',COLUMN_NAME,'position',SEQ_IN_INDEX)
        FROM information_schema.STATISTICS WHERE {scope} AND NON_UNIQUE=0 ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX""")
    foreign = query(f"""SELECT JSON_OBJECT('table',TABLE_NAME,'name',CONSTRAINT_NAME,'column',COLUMN_NAME,
        'parent',REFERENCED_TABLE_NAME,'parentColumn',REFERENCED_COLUMN_NAME,'position',ORDINAL_POSITION)
        FROM information_schema.KEY_COLUMN_USAGE WHERE {scope} AND REFERENCED_TABLE_NAME IS NOT NULL
        ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION""")
    checks = query("""SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',c.CONSTRAINT_NAME,'expression',c.CHECK_CLAUSE)
        FROM information_schema.CHECK_CONSTRAINTS c JOIN information_schema.TABLE_CONSTRAINTS t
        ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME
        WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME<>'flyway_schema_history' ORDER BY t.TABLE_NAME,c.CONSTRAINT_NAME""")
    migrations = query("SELECT JSON_OBJECT('version',version,'script',script,'success',success) FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")
    snapshot = {'capturedAt': datetime.now(ZoneInfo('Asia/Shanghai')).isoformat(timespec='seconds'),
                'source': 'local MySQL information_schema; no business rows', 'migrations': migrations, 'tables': {}}
    for table in tables:
        name = table['name']
        unique = {}
        for idx in indexes:
            if idx['table'] == name:
                unique.setdefault(idx['name'], []).append(idx['column'])
        fks = {}
        for fk in foreign:
            if fk['table'] == name:
                item = fks.setdefault(fk['name'], {'parent': fk['parent'], 'columns': [], 'parentColumns': []})
                item['columns'].append(fk['column'])
                item['parentColumns'].append(fk['parentColumn'])
        snapshot['tables'][name] = {'columns': [{k: v for k, v in col.items() if k != 'table'} for col in columns if col['table'] == name],
                                   'uniqueKeys': unique, 'foreignKeys': fks,
                                   'checks': [{k: v for k, v in c.items() if k != 'table'} for c in checks if c['table'] == name]}
    OUT.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + '\n')
    print(f'Captured {len(tables)} tables, {sum(len(t["foreignKeys"]) for t in snapshot["tables"].values())} foreign keys; latest V{migrations[-1]["version"]}.')
