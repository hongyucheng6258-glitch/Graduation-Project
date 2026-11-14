import request from './request'

export const listFriends = () => request.get('/friends')
export const friendStatus = (id) => request.get(`/friends/${id}`)
export const requestFriend = (id, greeting = '') => request.post(`/friends/${id}`, { greeting })
export const respondToFriend = (id, accept) => request.put(`/friends/${id}`, { accept })
export const removeFriend = (id) => request.delete(`/friends/${id}`)
