import { spawn, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import process from 'node:process'
import { createInterface } from 'node:readline'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backendDirectory = path.join(root, 'backend')
const frontendDirectory = path.join(root, 'frontend')
const isWindows = process.platform === 'win32'
const apiUrl = 'http://localhost:8080/actuator/health'
const frontendUrl = 'http://127.0.0.1:5173/'
const runningProcesses = []
let shuttingDown = false

function commandInvocation(command, args) {
  if (!isWindows || !/\.(cmd|bat)$/i.test(command)) {
    return { command, args, options: {} }
  }

  const commandLine = [command, ...args]
    .map((argument) => {
      const value = String(argument)
      return /[\s&|<>^()]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
    })
    .join(' ')

  return {
    command: process.env.ComSpec || 'cmd.exe',
    args: ['/d', '/s', '/c', `"${commandLine}"`],
    options: { windowsVerbatimArguments: true },
  }
}

function runCommand(command, args, options = {}) {
  const invocation = commandInvocation(command, args)
  const result = spawnSync(invocation.command, invocation.args, {
    cwd: root,
    stdio: 'inherit',
    windowsHide: false,
    ...invocation.options,
    ...options,
  })

  if (result.error) {
    throw result.error
  }

  if (result.status !== 0) {
    throw new Error(`${command} failed with exit code ${result.status ?? 'unknown'}`)
  }
}

function resolveMavenCommand() {
  const wrapperPropertiesPath = path.join(
    backendDirectory,
    '.mvn',
    'wrapper',
    'maven-wrapper.properties',
  )
  const wrapperProperties = readFileSync(wrapperPropertiesPath, 'utf8')
  const distributionUrl = wrapperProperties.match(/^distributionUrl=(.+)$/m)?.[1]

  if (!distributionUrl) {
    throw new Error(`Could not find distributionUrl in ${wrapperPropertiesPath}`)
  }

  const archiveName = path.posix.basename(distributionUrl)
  const distributionName = archiveName.replace(/\.[^.]+$/, '').replace(/-bin$/, '')
  const hash = createHash('sha256').update(distributionUrl, 'ascii').digest('hex')
  const mavenUserHome = process.env.MAVEN_USER_HOME
    || path.join(process.env.USERPROFILE || process.env.HOME || '', '.m2')
  const cachedCommand = path.join(
    mavenUserHome,
    'wrapper',
    'dists',
    distributionName,
    hash,
    'bin',
    'mvn.cmd',
  )

  if (existsSync(cachedCommand)) {
    return cachedCommand
  }

  return path.join(backendDirectory, isWindows ? 'mvnw.cmd' : 'mvnw')
}

function isPortOpen(port, host = '127.0.0.1') {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port })
    socket.setTimeout(500)
    socket.once('connect', () => {
      socket.destroy()
      resolve(true)
    })
    socket.once('timeout', () => {
      socket.destroy()
      resolve(false)
    })
    socket.once('error', () => resolve(false))
  })
}

async function isHttpReady(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(1500) })
    if (!response.ok) {
      return false
    }

    if (url === apiUrl) {
      const health = await response.json()
      return health.status === 'UP'
    }

    const html = await response.text()
    return html.includes('<div id="app">')
  } catch {
    return false
  }
}

function pipeWithPrefix(name, stream) {
  const lines = createInterface({ input: stream })
  lines.on('line', (line) => {
    console.log(`[${name}] ${line}`)
  })
}

function launch(name, command, args, cwd, env) {
  const invocation = commandInvocation(command, args)
  const child = spawn(invocation.command, invocation.args, {
    cwd,
    env,
    windowsHide: false,
    stdio: ['ignore', 'pipe', 'pipe'],
    ...invocation.options,
  })

  runningProcesses.push({ name, child })
  pipeWithPrefix(name, child.stdout)
  pipeWithPrefix(name, child.stderr)

  child.on('error', (error) => {
    console.error(`[${name}] Could not start: ${error.message}`)
    shutdown(1)
  })

  child.on('exit', (code, signal) => {
    if (shuttingDown) {
      return
    }

    console.error(`[${name}] stopped (code ${code ?? 'none'}, signal ${signal ?? 'none'}).`)
    shutdown(code && code !== 0 ? code : 1)
  })

  return child
}

function stopProcess(child) {
  if (!child.pid) {
    return
  }

  if (isWindows) {
    spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {
      stdio: 'ignore',
      windowsHide: true,
    })
    return
  }

  child.kill('SIGTERM')
}

function shutdown(exitCode = 0) {
  if (shuttingDown) {
    return
  }

  shuttingDown = true
  for (const { child } of runningProcesses) {
    stopProcess(child)
  }

  process.exitCode = exitCode
}

function databaseEndpoint() {
  const jdbcUrl = process.env.DATABASE_URL || 'jdbc:postgresql://localhost:5432/slotly'
  let url

  try {
    if (!jdbcUrl.startsWith('jdbc:postgresql://')) {
      throw new Error('Invalid JDBC URL')
    }
    url = new URL(jdbcUrl.slice('jdbc:'.length))
  } catch {
    throw new Error('DATABASE_URL must be a PostgreSQL JDBC URL, such as jdbc:postgresql://localhost:5432/slotly.')
  }

  if (url.protocol !== 'postgresql:' || !url.hostname || url.pathname.length < 2) {
    throw new Error('DATABASE_URL must be a PostgreSQL JDBC URL, such as jdbc:postgresql://localhost:5432/slotly.')
  }

  return { host: url.hostname, port: Number(url.port || 5432) }
}

async function waitForPostgres(host, port) {
  const deadline = Date.now() + 10_000

  while (Date.now() < deadline) {
    if (await isPortOpen(port, host) || (host === 'localhost' && await isPortOpen(port, '127.0.0.1'))) {
      return
    }

    await delay(1000)
  }

  throw new Error(`PostgreSQL is unavailable at ${host}:${port}. Start the local server and check DATABASE_URL.`)
}

async function waitForApplications() {
  const deadline = Date.now() + 60_000

  while (Date.now() < deadline) {
    const [backendReady, frontendReady] = await Promise.all([
      isHttpReady(apiUrl),
      isHttpReady(frontendUrl),
    ])

    if (backendReady && frontendReady) {
      return
    }

    await delay(1000)
  }

  throw new Error('Backend and frontend did not both become ready within 60 seconds.')
}

async function main() {
  const { host, port } = databaseEndpoint()
  console.log(`[dev] Waiting for PostgreSQL at ${host}:${port}...`)
  await waitForPostgres(host, port)

  if (!existsSync(path.join(frontendDirectory, 'node_modules'))) {
    console.log('[dev] Installing frontend dependencies...')
    runCommand(isWindows ? 'pnpm.cmd' : 'pnpm', ['install', '--frozen-lockfile'], { cwd: frontendDirectory })
  }

  const backendReady = await isHttpReady(apiUrl)
  const frontendReady = await isHttpReady(frontendUrl)

  if (!backendReady && await isPortOpen(8080)) {
    throw new Error('Port 8080 is occupied by a process that is not Slotly.')
  }
  if (!frontendReady && await isPortOpen(5173)) {
    throw new Error('Port 5173 is occupied by a process that is not the Slotly Vite app.')
  }

  if (!backendReady) {
    const mavenCommand = resolveMavenCommand()
    const mavenOptions = process.env.MAVEN_OPTS || ''
    const env = {
      ...process.env,
      SPRING_PROFILES_ACTIVE: process.env.SPRING_PROFILES_ACTIVE || 'local',
      MAVEN_OPTS: /(^|\s)-Duser\.home=/.test(mavenOptions)
        ? mavenOptions
        : `${mavenOptions} -Duser.home=${process.env.USERPROFILE || process.env.HOME}`.trim(),
    }
    launch('backend', mavenCommand, ['spring-boot:run'], backendDirectory, env)
  } else {
    console.log('[dev] Backend is already responding.')
  }

  if (!frontendReady) {
    launch(
      'frontend',
      isWindows ? 'pnpm.cmd' : 'pnpm',
      ['run', 'dev', '--', '--host', '127.0.0.1'],
      frontendDirectory,
      process.env,
    )
  } else {
    console.log('[dev] Frontend is already responding.')
  }

  await waitForApplications()
  console.log('\n[dev] Slotly is ready.')
  console.log('[dev] Login:    http://127.0.0.1:5173/login')
  console.log('[dev] Register: http://127.0.0.1:5173/register')
  console.log(`[dev] API:      ${apiUrl}`)
  console.log('[dev] Press Ctrl+C to stop the backend and frontend. Local PostgreSQL stays running.')
}

process.on('SIGINT', () => {
  console.log('\n[dev] Stopping backend and frontend...')
  shutdown(0)
})

process.on('SIGTERM', () => shutdown(0))

main().catch((error) => {
  console.error(`[dev] ${error.message}`)
  shutdown(1)
})
