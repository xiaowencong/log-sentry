import api from './index'

// 报告列表
export const getReports = (params) => api.get('/reports', {params})

// 获取单个报告
export const getReport = (id) => api.get(`/reports/${id}`)

// 生成日报（可选筛选参数）
export const generateDaily = (filters) => api.post('/reports/generate/daily', filters || {})

// 生成周报（可选筛选参数）
export const generateWeekly = (filters) => api.post('/reports/generate/weekly', filters || {})

// 生成月报（可选筛选参数）
export const generateMonthly = (filters) => api.post('/reports/generate/monthly', filters || {})

// 生成自定义报告（含筛选参数）
export const generateCustom = (startTime, endTime, filters = {}) =>
    api.post('/reports/generate/custom', {startTime, endTime, ...filters})

// 删除报告
export const deleteReport = (id) => api.delete(`/reports/${id}`)

// 下载报告 .md 文件（直接 URL，不走 axios）
const BASE = import.meta.env.VITE_API_BASE || '/api'
export const getDownloadUrl = (id) => `${BASE}/reports/${id}/download`
