let pending = null
let active = null

// Preload explicitly: the upstream cancelable loader leaves an unhandled rejection
// on network failures. Only pass an already loaded Monaco instance to the wrapper.
// Every caller attaches `.catch`; cancelCodeEditorLoad() settles the in-flight load
// when the page leaves, so no late rejection or <script> survives unmount.
export function loadCodeEditor() {
  if (window.monaco?.editor) return Promise.resolve(window.monaco)
  if (pending) return pending

  const base = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs'
  const script = document.createElement('script')
  let finish = () => {}
  let done = false

  const promise = new Promise((resolve, reject) => {
    finish = (error, monaco) => {
      if (done) return
      done = true
      clearTimeout(timer)
      script.remove()
      if (active === record) active = null
      if (pending === promise) pending = null
      if (error) reject(error)
      else resolve(monaco)
    }

    const timer = setTimeout(() => finish(new Error('编辑器加载超时')), 10000)
    script.src = `${base}/loader.js`
    script.async = true
    script.onerror = () => finish(new Error('编辑器资源暂时不可用'))
    script.onload = () => {
      try {
        window.require.config({ paths: { vs: base } })
        window.require(
          ['vs/editor/editor.main'],
          loaded => finish(null, loaded.m || loaded),
          error => finish(error || new Error('编辑器资源加载失败'))
        )
      } catch (error) {
        finish(error)
      }
    }

    const record = { finish, script, timer }
    active = record
    document.head.appendChild(script)
  })

  pending = promise
  return promise
}

// Page leave hook: remove the in-flight <script>, clear the timeout and settle the
// promise as a handled cancellation. The component's own `.catch` swallows it after
// its disposed flag is set, so no unhandled rejection reaches the console.
export function cancelCodeEditorLoad() {
  const current = active
  active = null
  pending = null
  current?.finish(new Error('页面已离开，取消编辑器加载'))
}
