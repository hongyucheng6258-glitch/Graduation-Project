import request from './request'
import { createDrawGuessApi } from '../features/drawGuess/drawGuessApi.mjs'

const drawGuessApi = createDrawGuessApi(request)

export const listDrawGuessRooms = drawGuessApi.listRooms
export const listRecentDrawGuessRooms = drawGuessApi.recentRooms
export const listDrawGuessRecords = drawGuessApi.listRecords
export const getDrawGuessRoom = drawGuessApi.getRoom
export const createDrawGuessRoom = drawGuessApi.createRoom
export const joinDrawGuessRoom = drawGuessApi.joinRoom
export const startDrawGuessRoom = drawGuessApi.startRoom
export const leaveDrawGuessRoom = drawGuessApi.leaveRoom
export const createDrawGuessWsTicket = drawGuessApi.createWsTicket
export const uploadDrawGuessSnapshot = drawGuessApi.uploadSnapshot
