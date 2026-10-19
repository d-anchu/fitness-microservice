import { useEffect, useState } from 'react'
import { Button, Box } from '@mui/material'
import { useAuthContext } from 'react-oauth2-code-pkce'
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { logout, setCredentials } from './store/authSlice'
import ActivityForm from './components/ActivityForm'
import ActivityList from './components/ActivityList'
import ActivityDetail from './components/ActivityDetail'

const ActivitiesPage = () => {
  /*
   * refreshKey is a counter that starts at 0.
   * When handleActivityAdded increments it, React sees a new value
   * for the key prop on <ActivityList key={refreshKey} />.
   * A changed key forces React to UNMOUNT the old ActivityList and
   * MOUNT a fresh one, which triggers its useEffect and re-fetches
   * the activity list from the API automatically.
   */
  const [refreshKey, setRefreshKey] = useState(0)

  /*
   * handleActivityAdded is passed down to ActivityForm as a prop.
   * ActivityForm calls it after a successful POST /api/activities.
   * Incrementing refreshKey is all it takes to trigger the remount.
   */
  const handleActivityAdded = () => {
    setRefreshKey((prev) => prev + 1)
  }

  return (
    <Box component="section" sx={{p: 2, border: '1px dashed grey'}}>
      {/* onActivityAdded prop wires ActivityForm to the refresh logic above */}
      <ActivityForm onActivityAdded={handleActivityAdded} />

      {/*
        key={refreshKey} is the refresh trigger.
        React destroys and recreates ActivityList whenever refreshKey changes,
        causing a fresh API call so the new activity appears immediately.
      */}
      <ActivityList key={refreshKey} />
    </Box>
  )
}


function App() {
  const { token, tokenData, logIn, logOut } = useAuthContext()
  const dispatch = useDispatch();

  // Copy the OAuth session into Redux when a token becomes available.
  useEffect(() => {
    if(token)
    {
      dispatch(setCredentials({token:token,user:tokenData}));
    }
  },[token,tokenData,dispatch]);

  return (
    // The auth provider and Redux store are supplied by main.jsx.
    <Router>
      <Box component="section" sx={{ p: 2 }}>
        {!token ? (
          <Button
            variant="contained"
            sx={{ backgroundColor: '#dc004e' }}
            onClick={() => logIn()}
          >
            LOGIN
          </Button>
        ) : (
          <Button
            variant="contained"
            sx={{ backgroundColor: '#dc004e' }}
            onClick={() => {
              logOut()
              dispatch(logout())
            }}
          >
            LOG OUT
          </Button>
        )}
      </Box>
      <Routes>
        <Route
          path="/"
          element={token ? <Navigate to="/activities" replace /> : null}
        />
        {/* Protected route: redirect to login if not authenticated */}
        <Route 
          path="/activities" 
          element={token ? <ActivitiesPage /> : <Navigate to="/" replace />} 
        />
        {/* Protected route: redirect to login if not authenticated */}
        <Route 
          path="/activities/:id" 
          element={token ? <ActivityDetail /> : <Navigate to="/" replace />} 
        />
      </Routes>
    </Router>
  )
}

export default App
