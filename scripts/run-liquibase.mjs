import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const actions = {
  status: ['resources:resources', 'liquibase:status'],
  update: ['resources:resources', 'liquibase:update'],
  'rollback-preview': [
    'resources:resources',
    'liquibase:rollbackSQL',
    '-Dliquibase.rollbackCount=1',
  ],
  rollback: ['resources:resources', 'liquibase:rollback', '-Dliquibase.rollbackCount=1'],
}

const action = process.argv[2]

if (!(action in actions)) {
  console.error(
    `Unknown database action "${action ?? ''}". Expected one of: ${Object.keys(actions).join(', ')}.`,
  )
  process.exit(1)
}

const requiredEnvironment = ['DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'DB_DRIVER']
const missingEnvironment = requiredEnvironment.filter((name) => !process.env[name])

if (missingEnvironment.length > 0) {
  console.error(
    `Missing database environment values: ${missingEnvironment.join(', ')}. Configure backend/.env first.`,
  )
  process.exit(1)
}

if (action === 'rollback' && process.env.DB_ALLOW_ROLLBACK !== 'true') {
  console.error(
    'Rollback refused. Preview it first, then set DB_ALLOW_ROLLBACK=true only for the disposable development database.',
  )
  process.exit(1)
}

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backendDirectory = path.join(projectRoot, 'backend')
const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'
const result = spawnSync(wrapper, actions[action], {
  cwd: backendDirectory,
  env: process.env,
  shell: false,
  stdio: 'inherit',
})

if (result.error) {
  console.error(`Unable to start the Maven Wrapper: ${result.error.message}`)
  process.exit(1)
}

process.exit(result.status ?? 1)
