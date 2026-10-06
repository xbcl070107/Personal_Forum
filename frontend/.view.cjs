// 用 CDP 拍几张当前状态的截图。
// 登录态不靠 token：先访问项目自带的 public/login.html（它往 localStorage 塞
// 一个管理员身份再跳进应用），这样谁都能看到登录后的页面。
const fs = require('node:fs')
const http = require('node:http')
const net = require('node:net')
const { randomBytes } = require('node:crypto')

const PORT = 9222
const BASE = process.env.SHOT_BASE || 'http://localhost:5173'
const OUT = process.env.SHOT_OUT || 'shots'
const POST_ID = process.env.SHOT_POST_ID || '10'

const PAGES = [
  { name: '01-home', path: '/#/' },
  { name: '02-post-detail', path: `/#/post/${POST_ID}` },
  { name: '03-publish', path: '/#/publish' },
  { name: '04-my-posts', path: '/#/my' },
  { name: '05-audit', path: '/#/audit' },
  { name: '06-login', path: '/#/login' },
  { name: '07-register', path: '/#/register' },
]

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const getJson = (url) =>
  new Promise((res, rej) => {
    http.get(url, (r) => {
      let d = ''
      r.on('data', (c) => (d += c))
      r.on('end', () => res(JSON.parse(d)))
    }).on('error', rej)
  })

class MiniWs {
  constructor(url) {
    this.url = url
    this.buf = Buffer.alloc(0)
  }
  connect() {
    const u = new URL(this.url)
    const key = randomBytes(16).toString('base64')
    this.sock = net.connect(Number(u.port), u.hostname, () => {
      this.sock.write(
        `GET ${u.pathname} HTTP/1.1\r\nHost: ${u.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ${key}\r\nSec-WebSocket-Version: 13\r\n\r\n`,
      )
    })
    this.sock.on('data', (chunk) => {
      if (!this.upgraded) {
        this.buf = Buffer.concat([this.buf, chunk])
        const idx = this.buf.indexOf('\r\n\r\n')
        if (idx === -1) return
        this.upgraded = true
        this.buf = this.buf.subarray(idx + 4)
        this.onopen && this.onopen()
      } else {
        this.buf = Buffer.concat([this.buf, chunk])
      }
      this.drain()
    })
    this.sock.on('error', (e) => this.onerror && this.onerror(e))
    return this
  }
  drain() {
    for (;;) {
      if (this.buf.length < 2) return
      const b0 = this.buf[0]
      const b1 = this.buf[1]
      const opcode = b0 & 0x0f
      const masked = (b1 & 0x80) === 0x80
      let len = b1 & 0x7f
      let off = 2
      if (len === 126) {
        if (this.buf.length < 4) return
        len = this.buf.readUInt16BE(2)
        off = 4
      } else if (len === 127) {
        if (this.buf.length < 10) return
        len = Number(this.buf.readBigUInt64BE(2))
        off = 10
      }
      let mask
      if (masked) {
        if (this.buf.length < off + 4) return
        mask = this.buf.subarray(off, off + 4)
        off += 4
      }
      if (this.buf.length < off + len) return
      let payload = this.buf.subarray(off, off + len)
      if (masked) {
        payload = Buffer.from(payload)
        for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4]
      }
      this.buf = this.buf.subarray(off + len)
      if (opcode === 0x1) {
        this.onmessage && this.onmessage({ data: payload.toString('utf8') })
      } else if (opcode === 0x8) {
        this.sock.end()
      }
    }
  }
  send(str) {
    const data = Buffer.from(str, 'utf8')
    const len = data.length
    let header
    if (len < 126) header = Buffer.from([0x81, 0x80 | len])
    else if (len < 65536) {
      header = Buffer.alloc(4)
      header[0] = 0x81
      header[1] = 0x80 | 126
      header.writeUInt16BE(len, 2)
    } else {
      header = Buffer.alloc(10)
      header[0] = 0x81
      header[1] = 0x80 | 127
      header.writeBigUInt64BE(BigInt(len), 2)
    }
    const mask = randomBytes(4)
    const masked = Buffer.from(data)
    for (let i = 0; i < masked.length; i++) masked[i] ^= mask[i % 4]
    this.sock.write(Buffer.concat([header, mask, masked]))
  }
  close() {
    this.sock && this.sock.end()
  }
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })

  const targets = await getJson(`http://127.0.0.1:${PORT}/json/list`)
  const page = targets.find((t) => t.type === 'page')
  if (!page) throw new Error('no page target')

  const ws = new MiniWs(page.webSocketDebuggerUrl).connect()
  await new Promise((res, rej) => {
    ws.onopen = res
    ws.onerror = rej
  })

  let id = 0
  const pending = new Map()
  const problems = []
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data)
    if (msg.id && pending.has(msg.id)) {
      const { res, rej } = pending.get(msg.id)
      pending.delete(msg.id)
      msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result)
      return
    }
    if (msg.method === 'Runtime.exceptionThrown') {
      problems.push('EXCEPTION: ' + (msg.params.exceptionDetails.exception?.description || ''))
    }
    if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
      problems.push('CONSOLE.ERROR: ' + msg.params.args.map((a) => a.value ?? a.description).join(' '))
    }
  }
  const send = (method, params = {}) =>
    new Promise((res, rej) => {
      const mid = ++id
      pending.set(mid, { res, rej })
      ws.send(JSON.stringify({ id: mid, method, params }))
    })

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1280,
    height: 900,
    deviceScaleFactor: 2,
    mobile: false,
  })

  // login.html 塞的是假 token，能骗过前端守卫，但过不了后端 JWT 拦截器 ——
  // 一发受保护请求就 401，然后 request.js 会把 token 清掉。
  // 所以要真的走一遍登录表单，拿一个后端签过的 token。
  await send('Page.navigate', { url: `${BASE}/#/login` })
  await sleep(400)
  await send('Page.reload', { ignoreCache: true })
  await sleep(1800)

  const setField = async (selector, value) => {
    const r = await send('Runtime.evaluate', {
      expression: `(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return 'MISSING ' + ${JSON.stringify(selector)};
        const setter = Object.getOwnPropertyDescriptor(
          window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, ${JSON.stringify(value)});
        el.dispatchEvent(new Event('input', { bubbles: true }));
        return 'ok';
      })()`,
      returnByValue: true,
    })
    return r.result.value
  }

  console.log('  user field:', await setField('input[autocomplete="username"]', 'xbcl'))
  console.log('  pass field:', await setField('input[autocomplete="current-password"]', '123456'))

  await sleep(300)
  // 点「进入协会」。el-button 在 el-form 里默认是原生 submit，点一下就走 handleSubmit
  await send('Runtime.evaluate', {
    expression: `document.querySelector('.submit')?.click(), 'clicked'`,
    returnByValue: true,
  })
  await sleep(2600)

  const after = await send('Runtime.evaluate', {
    expression: `'hash=' + location.hash + ' | token=' + (localStorage.getItem('token')||'').slice(0,12) +
      ' | role=' + (JSON.parse(localStorage.getItem('user')||'null')||{}).role +
      ' | nav=' + (document.querySelector('.nav')?.innerText||'').replace(/\\s+/g,' ').trim()`,
    returnByValue: true,
  })
  console.log('after login ->', after.result.value)

  for (const p of PAGES) {
    await send('Page.navigate', { url: BASE + p.path })
    await sleep(400)
    await send('Page.reload', { ignoreCache: true })
    await sleep(2300)
    const probe = await send('Runtime.evaluate', {
      expression: `(document.querySelector('.nav')?.innerText || '').replace(/\\s+/g,' ').trim()
        + ' || cards=' + document.querySelectorAll('.post').length
        + ' || rows=' + document.querySelectorAll('.el-table__row').length`,
      returnByValue: true,
    })
    const { data } = await send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: true,
    })
    fs.writeFileSync(`${OUT}/${p.name}.png`, Buffer.from(data, 'base64'))
    console.log('shot', p.name, '|', probe.result.value)
  }

  console.log('page problems:', problems.length ? problems : 'none')
  ws.close()
}

main().catch((e) => {
  console.error('FAILED', e.message)
  process.exit(1)
})
