import { createWorker } from 'tesseract.js'

/**
 * OCR 工具（第二阶段）：前端 tesseract.js 识别拍照/上传的题目图片。
 * - worker 懒加载，首次调用才初始化
 * - langPath / workerPath / corePath 全部指向本地 public/ocr/（chi_sim+eng 训练数据、
 *   worker.min.js、tesseract-core-*.wasm(.js) 均已内置），完全离线可用，无 CDN 依赖
 * - 识别失败由调用方降级为手动输入，不阻塞收录
 */

let workerPromise = null

function getWorker() {
  if (!workerPromise) {
    workerPromise = createWorker('chi_sim+eng', 1, {
      langPath: '/ocr',
      workerPath: '/ocr/worker.min.js',
      corePath: '/ocr',
      logger: () => {}
    }).catch((err) => {
      workerPromise = null
      throw err
    })
  }
  return workerPromise
}

/**
 * 识别图片中的文字。
 *
 * @param {File|Blob|string} image 图片文件、Blob 或 URL
 * @param {Object} [opts] { timeoutMs } 超时毫秒，超时抛错（调用方降级为手动输入）
 * @returns {Promise<string>} 识别出的文本（可能为空串）
 */
export async function ocrImage(image, opts = {}) {
  const { timeoutMs = 60000 } = opts
  const worker = await getWorker()
  const recognize = worker.recognize(image)
  if (!timeoutMs) {
    const result = await recognize
    return (result?.data?.text || '').trim()
  }
  let timer
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error('OCR_TIMEOUT')), timeoutMs)
  })
  try {
    const result = await Promise.race([recognize, timeout])
    return (result?.data?.text || '').trim()
  } finally {
    clearTimeout(timer)
  }
}

/** 释放 worker（页面卸载时调用，可选） */
export async function disposeOcr() {
  if (workerPromise) {
    try {
      const w = await workerPromise
      await w.terminate()
    } finally {
      workerPromise = null
    }
  }
}
