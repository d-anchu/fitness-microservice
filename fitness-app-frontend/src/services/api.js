import axios from 'axios'

// Use the Vite proxy in development so API requests go to the HTTP gateway.
const api = axios.create({
  baseURL: '/api',
})

// HELPER: Decode JWT payload to extract user ID (sub claim)
// This is client-side decoding WITHOUT signature verification
// The server still validates the JWT - this is just for extracting the userId
const decodeJwt = (token) => {
  try {
    const payload = token.split('.')[1] // JWT format: header.payload.signature
    const decoded = atob(payload) // Base64 decode
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

// HELPER: Get token from either localStorage or the OAuth library
// The react-oauth2-code-pkce library stores tokens under 'ROCP_token' key
// but our code expects it under plain 'token' key
// This creates a timing race on first login - this helper fixes it
const getToken = () => {
  // First check the standard location (set by App.jsx after login)
  let token = localStorage.getItem('token')
  if (token) return token

  // Fallback: read directly from react-oauth2-code-pkce's storage
  // This runs BEFORE App.jsx's useEffect has time to copy the token
  try {
    const ropcTokenData = localStorage.getItem('ROCP_token')
    if (ropcTokenData) {
      const parsed = JSON.parse(ropcTokenData)
      return parsed.token || parsed.access_token || null
    }
  } catch {
    return null
  }
  return null
}

// REQUEST INTERCEPTOR: Automatically add Authorization and X-User-Id headers
// Runs before every API request to attach authentication credentials
api.interceptors.request.use((config) => {
  // Get token using helper (handles OAuth library's storage location)
  const token = getToken()
  
  // Get userId from localStorage or decode from JWT if missing
  let userId = localStorage.getItem('userId')

  // FIX: If userId is missing, extract it from the JWT token
  // This happens on first login before App.jsx's useEffect runs
  if (!userId && token) {
    const payload = decodeJwt(token)
    if (payload && payload.sub) {
      userId = payload.sub // Keycloak stores user UUID in 'sub' claim
      // Cache it for next time to avoid repeated decoding
      localStorage.setItem('userId', userId)
    }
  }

  // Attach Authorization header (required by API Gateway JWT validation)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  
  // Attach X-User-Id header (required by Activity Service endpoints)
  if (userId) {
    config.headers['X-User-Id'] = userId
  }

  // Axios request interceptors must return the config to continue the request.
  return config
})

export const getActivities = () => api.get('/activities')
export const addActivity = (activity) => api.post('/activities', activity)
export const getActivityDetail = (id) =>
  api.get(`/recommendations/activity/${encodeURIComponent(id)}`)