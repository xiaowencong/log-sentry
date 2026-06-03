import api from './index'

// 规则列表
export const getRules = () => api.get('/rules')

// 获取单个规则
export const getRule = (id) => api.get(`/rules/${id}`)

// 创建规则
export const createRule = (data) => api.post('/rules', data)

// 更新规则
export const updateRule = (id, data) => api.put(`/rules/${id}`, data)

// 删除规则
export const deleteRule = (id) => api.delete(`/rules/${id}`)

// 启停切换
export const toggleRule = (id) => api.post(`/rules/${id}/toggle`)
