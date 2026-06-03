import api from './index'

// 日志源列表
export const getSources = () => api.get('/sources')

// 获取单个日志源
export const getSource = (id) => api.get(`/sources/${id}`)

// 创建日志源
export const createSource = (data) => api.post('/sources', data)

// 更新日志源
export const updateSource = (id, data) => api.put(`/sources/${id}`, data)

// 删除日志源
export const deleteSource = (id) => api.delete(`/sources/${id}`)

// 启停切换
export const toggleSource = (id) => api.post(`/sources/${id}/toggle`)

// 触发全量扫描
export const triggerFullScan = (id) => api.post(`/sources/${id}/full-scan`)

// 扫描进度
export const getScanProgress = (id) => api.get(`/sources/${id}/scan-progress`)
