import { mergeConfig } from 'vite'
import base from './vite.config.mjs'
export default mergeConfig(base, { server: { host: '127.0.0.1', port: 5176, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:8081', changeOrigin: true } } } })
