import request from '@/utils/request'

/** 9.1 上传图片（返回 { url }） */
export const uploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  // 注意：这里不要手动设置 Content-Type，
  // 浏览器要自己加 multipart 的 boundary，手写会让后端解析失败
  return request.post('/upload/image', form)
}
