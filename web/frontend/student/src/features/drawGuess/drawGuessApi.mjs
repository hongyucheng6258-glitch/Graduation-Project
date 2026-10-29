export function createDrawGuessApi(request, makeFormData = () => new FormData()) {
  return {
    listRooms: (params) => request.get('/draw-guess/rooms', { params }),
    recentRooms: () => request.get('/draw-guess/rooms/recent'),
    listRecords: () => request.get('/draw-guess/records'),
    getRoom: (roomId) => request.get(`/draw-guess/rooms/${roomId}`),
    createRoom: (data) => request.post('/draw-guess/rooms', data),
    joinRoom: (roomCode, data = {}) => request.post(`/draw-guess/rooms/${encodeURIComponent(roomCode)}/join`, data),
    startRoom: (roomId) => request.post(`/draw-guess/rooms/${roomId}/start`),
    leaveRoom: (roomId) => request.post(`/draw-guess/rooms/${roomId}/leave`),
    createWsTicket: (roomId) => request.post('/draw-guess/ws-ticket', { roomId }),
    uploadSnapshot: (roomId, roundId, file) => {
      const form = makeFormData()
      form.append('file', file)
      return request.post(`/draw-guess/rooms/${roomId}/rounds/${roundId}/snapshot`, form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
    }
  }
}
