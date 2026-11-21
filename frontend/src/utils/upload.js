import { ElMessage } from 'element-plus'
import { uploadImage } from '../api/file'

/**
 * 选图 → 校验 → 上传，返回图片 URL；任何一步失败返回 null。
 *
 * 【为什么要包一层，而不是页面里直接调 uploadImage】
 * 三个页面（发布商品、改店铺头像、以后的头像）都要做同样三件事：
 * 判类型、判大小、失败时说人话。抄三遍的结果是某天只改了其中一处，
 * 另外两处还在收 10MB 的图。
 *
 * 大小限制放在前端只是「早点告诉用户」，不是安全边界 ——
 * 真正的上限在 Spring 的 multipart 配置里，前端拦不住绕过浏览器的请求。
 *
 * @param {File} file 用户选中的文件
 * @param {{maxMB?: number}} [options] 大小上限，默认 5MB（和 application.yaml 对齐）
 * @returns {Promise<string|null>} 可访问的 URL，失败为 null
 */
export async function uploadImageFile(file, { maxMB = 5 } = {}) {
  if (!file) return null

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    return null
  }

  if (file.size > maxMB * 1024 * 1024) {
    ElMessage.warning(`图片不能超过 ${maxMB}MB`)
    return null
  }

  const res = await uploadImage(file)

  if (res.code !== 200 || !res.data) {
    ElMessage.error(res.message || '图片上传失败，稍后再试')
    return null
  }

  return res.data
}
