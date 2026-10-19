import { configureStore } from '@reduxjs/toolkit'
import authReducer from './authSlice'

// The Redux store combines application state reducers in one place.
export const store = configureStore({
  reducer: {
    auth: authReducer,
  },
})
