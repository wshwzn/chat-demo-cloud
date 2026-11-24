import { spawn } from 'node:child_process'
import { rmSync } from 'node:fs'

const args = process.argv.slice(2)
const mode = args.includes('serve') ? 'serve' : 'build'

if (mode === 'build') {
  rmSync('dist', { recursive: true, force: true })
}

const env = { ...process.env }
if (mode === 'build') {
  env.NODE_ENV = 'production'
}

// 参数都是常量，拼成一条命令交给 shell 执行，避免 spawn(args, {shell:true}) 的弃用告警
const child = spawn(`rspack ${mode} -c rspack.config.ts`, {
  stdio: 'inherit',
  env,
  shell: true,
})

child.on('exit', (code) => process.exit(code ?? 0))
child.on('error', (error) => {
  console.error(error)
  process.exit(1)
})