import request from './request'

/**
 * 文件上传。
 *
 * 【为什么单独一个 api 文件】
 * 发帖、发商品、改店铺头像都要传图，但后端只有一个接口。
 * 如果每个页面自己拼 FormData、自己 import axios，token 和错误处理
 * 就得抄三遍 —— 以后上传地址一改，三个地方一起漏。
 *
 * 【为什么字段名必须是 file】
 * 后端 FileController 的签名是 @RequestParam("file") MultipartFile file。
 * 前端 append 成 'image'、'upload' 都会 400，这是最常见的上传失败原因。
 */

/**
 * 上传一张图片，成功返回可访问的 URL 字符串（在 data 里）。
 *
 * 注意不要手动设 Content-Type: multipart/form-data。
 * multipart 的 header 里必须带一个 boundary 分隔符，浏览器会根据
 * FormData 自动生成；手写一个不带 boundary 的 header 反而会让后端解析失败。
 * axios 检测到 FormData 会自己处理，这里什么都不用加。
 */
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData)
}
