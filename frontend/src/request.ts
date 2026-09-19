import axios from 'axios'
import { UNAUTHORIZED_CODE } from '@/constants'
import { message } from 'ant-design-vue'
// 区分开发和生产环境
const isDev = import.meta.env.MODE === 'development'
const DEV_BASE_URL = 'http://localhost:8123'
const PROD_BASE_URL = 'http://39.96.23.157' // 或者你的域名

// 创建 Axios 实例
const myAxios = axios.create({
  baseURL: isDev ? DEV_BASE_URL : PROD_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

// 其他配置...

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录
    if (data.code === UNAUTHORIZED_CODE) {
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios
