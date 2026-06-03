import axios from 'axios'
import {message} from 'antd'

const api = axios.create({
    baseURL: '/api',
    timeout: 10000,
})

// 响应拦截器
api.interceptors.response.use(
    (response) => {
        const res = response.data
        // 后端 Result 统一返回格式：code=0 表示成功
        if (res && res.code !== undefined && res.code !== 0) {
            message.error(res.message || '请求失败')
            return Promise.reject(new Error(res.message || '请求失败'))
        }
        return res
    },
    (error) => {
        const msg = error.response?.data?.message || error.message || '网络异常'
        message.error(msg)
        return Promise.reject(error)
    }
)

export default api
