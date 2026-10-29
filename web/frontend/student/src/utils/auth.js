/**
 * 登录态工具：token 与用户信息存 localStorage（共享约定 #3）。
 */
export function getToken() {
  return localStorage.getItem('token')
}

export function setLogin(token, userInfo) {
  localStorage.setItem('token', token)
  localStorage.setItem('userInfo', JSON.stringify(userInfo))
}

export function getUserInfo() {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || 'null')
  } catch (e) {
    return null
  }
}

export function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}

export function isLoggedIn() {
  return !!getToken()
}
