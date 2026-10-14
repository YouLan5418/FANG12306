import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { createRequire } from "node:module"

const frontendRequire = createRequire(new URL("../frontend-next/package.json", import.meta.url))
const ts = frontendRequire("typescript")
const source = readFileSync(new URL("../frontend-next/src/api/customer.ts", import.meta.url), "utf8")
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
function api(fetchImpl, store = { token: "test-token", clearSession() {} }) {
  const exports = {}
  new Function("require", "exports", "fetch", compiled)(
    name => name === "./request" ? { default: {}, unwrap() {} } : { useAuthStore: () => store }, exports, fetchImpl)
  return exports
}
function body(text) {
  const encoded = new TextEncoder().encode(text)
  return new ReadableStream({ start(controller) {
    // 分割到UTF-8字节边界，验证TextDecoder能拼回中文。
    for (const byte of encoded) controller.enqueue(new Uint8Array([byte]))
    controller.close()
  } })
}
const tests = []
tests.push(async () => {
  const events = []
  let completed = 0
  await api(async (_, options) => {
    assert.equal(options.headers.Authorization, "test-token")
    return { ok: true, body: body('data: {"type":"token","content":"你好"}\n\ndata: {"type":"done","sessionId":"s"}\n\n') }
  }).streamCustomerChat({ message: "你好" }, {
    onEvent: event => events.push(event), onComplete: () => completed++, onError: error => { throw error }
  })
  assert.equal(events[0].content, "你好")
  assert.equal(completed, 1)
})
tests.push(async () => {
  let error
  let completed = false
  await api(async () => ({ ok: true, body: body('data: {"type":"token","content":"partial"}\n\n') }))
    .streamCustomerChat({ message: "hello" }, { onEvent() {}, onComplete() { completed = true }, onError(value) { error = value } })
  assert.match(error.message, /意外中断/)
  assert.equal(completed, false)
})
tests.push(async () => {
  const controller = new AbortController()
  let cancelled = false
  let errors = 0
  let completed = 0
  const reader = { async read() { controller.abort(); throw new DOMException("aborted", "AbortError") },
    async cancel() { cancelled = true }, releaseLock() {} }
  await api(async (_, options) => {
    assert.equal(options.signal, controller.signal)
    return { ok: true, body: { getReader: () => reader } }
  }).streamCustomerChat({ message: "hello" }, { onEvent() {}, onError() { errors++ }, onComplete() { completed++ } }, controller.signal)
  assert.equal(cancelled, true)
  assert.equal(errors, 0)
  assert.equal(completed, 0)
})
tests.push(async () => {
  let cleared = false
  await assert.rejects(api(async () => ({ ok: false, status: 401 }), { token: "old", clearSession() { cleared = true } })
    .streamCustomerChat({ message: "hello" }, { onEvent() {} }), /401/)
  assert.equal(cleared, true)
})
for (const test of tests) await test()
console.log(`Customer stream tests: ${tests.length} passed`)
