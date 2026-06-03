import api from './index'

// 分页查询问题列表
export const getIssues = (params) => api.get('/issues', {params})

// 获取问题详情
export const getIssueDetail = (id) => api.get(`/issues/${id}`)

// 更新问题状态
export const updateIssueStatus = (id, status) => api.put(`/issues/${id}/status`, {status})

// 获取关联日志
export const getIssueLogs = (id) => api.get(`/issues/${id}/logs`)
