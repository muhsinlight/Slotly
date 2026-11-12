import { spawnSync } from 'node:child_process'

const container = process.argv[2]
if (!container || !/^[a-zA-Z0-9_-]+$/.test(container)) {
  throw new Error('Provide the isolated PostgreSQL test container ID.')
}

function run(...args) {
  const result = spawnSync('docker', ['exec', container, ...args], { encoding: 'utf8' })
  if (result.status !== 0) throw new Error(result.stderr || 'PostgreSQL verification failed.')
  return result.stdout.trim()
}

const restored = 'slotly_restore_test'
const tables = ['app_users', 'places', 'offerings', 'business_hours', 'appointments', 'waitlist_entries']
run('pg_dump', '-U', 'slotly', '-d', 'slotly_test', '--format=custom', '--no-owner', '--file=/tmp/slotly-check.dump')
run('createdb', '-U', 'slotly', restored)
try {
  run('pg_restore', '-U', 'slotly', '--exit-on-error', '--no-owner', '--dbname=' + restored, '/tmp/slotly-check.dump')
  for (const table of tables) {
    const sql = "SELECT count(*) || ':' || coalesce(md5(string_agg(row_to_json(t)::text, ',' ORDER BY id)), '') FROM " + table + ' t'
    const original = run('psql', '-U', 'slotly', '-d', 'slotly_test', '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql)
    const copy = run('psql', '-U', 'slotly', '-d', restored, '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql)
    if (original !== copy) throw new Error('Restore mismatch: ' + table)
  }
  console.log('Backup restore verified: six tables match row counts and content checksums.')
} finally {
  run('dropdb', '-U', 'slotly', restored)
}
