// ============================================================
// ActivityList.jsx
// Purpose: Fetches all activities for the logged-in user from
// the backend API and displays them as clickable Material-UI
// cards. Clicking a card navigates to the ActivityDetail view.
// ============================================================

// React hooks:
//   useEffect – runs side effects (API call) after render
//   useState  – manages local component state
import { useEffect, useState } from 'react'

// useSelector – reads values from the Redux store
// We need 'userId' which is stored in state.auth.userId after login
import { useSelector } from 'react-redux'

// useNavigate – lets us programmatically change the browser URL
import { useNavigate } from 'react-router-dom'

// Material-UI components used in this file:
import {
  Alert,            // Displays error or warning messages in a styled box
  Box,              // Generic layout div with sx styling support
  Card,             // White elevated card container for each activity
  CardActionArea,   // Wrapper that makes the entire card surface clickable
  CardContent,      // Padded content area inside a card
  CircularProgress, // Spinning loading indicator
  Typography,       // Styled text (headings, body text, captions)
} from '@mui/material'

// getActivities – calls GET /api/activities
// The axios interceptor in api.js automatically attaches the JWT
// Authorization header and the X-User-Id header to every request.
import { getActivities } from '../services/api'

const ActivityList = () => {
  // ----------------------------------------------------------
  // STATE MANAGEMENT
  // ----------------------------------------------------------

  // Read the userId that was stored in Redux during login.
  // This is used to guard against rendering before the user is authed.
  const userId = useSelector((state) => state.auth.userId)

  // activities: the array of activity objects returned by the API.
  // Starts empty; filled after a successful fetch.
  const [activities, setActivities] = useState([])

  // loading: true while the API request is in-flight.
  // Used to show the spinner and hide the list until data arrives.
  const [loading, setLoading] = useState(true)

  // error: holds an error message string when the fetch fails.
  // null when everything is fine.
  const [error, setError] = useState(null)

  // navigate: function from React Router to change the URL.
  // Called when the user clicks an activity card.
  const navigate = useNavigate()

  // ----------------------------------------------------------
  // FETCH ACTIVITIES ON MOUNT
  // useEffect with dependency [userId] runs once when the component
  // first mounts, and again any time userId changes (e.g. after login).
  // ----------------------------------------------------------
  useEffect(() => {
    const fetchActivities = async () => {
      // Guard: if there is no userId the user is not logged in yet.
      // Show an error instead of making an unauthenticated API call.
      if (!userId) {
        setError('Please log in to view activities.')
        setLoading(false)
        return
      }

      try {
        setLoading(true)

        // API call: GET /api/activities
        // The axios interceptor (api.js) adds:
        //   Authorization: Bearer <JWT token>
        //   X-User-Id: <userId>
        // The backend filters activities by userId from the token/header.
        const response = await getActivities()

        // response.data is the array of activity objects from Spring Boot.
        setActivities(response.data)
        setError(null) // Clear any leftover error from a previous failed attempt
      } catch (err) {
        console.error('Failed to fetch activities:', err)
        // Use the backend error message if available, otherwise a generic one.
        setError(err.response?.data?.message || 'Failed to load activities.')
      } finally {
        // Always clear the loading flag so the spinner disappears.
        setLoading(false)
      }
    }

    fetchActivities()
  }, [userId]) // Re-run if userId changes

  // ----------------------------------------------------------
  // RENDER: LOADING STATE
  // Show a centered spinner while the API request is in-flight.
  // ----------------------------------------------------------
  if (loading) {
    return (
      <Box sx={{display: 'flex', justifyContent: 'center', p: 4}}>
        <CircularProgress />
      </Box>
    )
  }

  // ----------------------------------------------------------
  // RENDER: ERROR STATE
  // Show a red alert if the fetch failed or the user is not logged in.
  // ----------------------------------------------------------
  if (error) {
    return <Alert severity="error" sx={{mb: 2}}>{error}</Alert>
  }

  // ----------------------------------------------------------
  // RENDER: EMPTY STATE
  // Show a friendly message when the user has no activities yet.
  // ----------------------------------------------------------
  if (activities.length === 0) {
    return (
      <Typography variant="body1" sx={{p: 2, color: 'text.secondary'}}>
        No activities recorded yet. Add your first activity above!
      </Typography>
    )
  }

  // ----------------------------------------------------------
  // RENDER: MAIN LIST
  // Render a clickable card for each activity in the array.
  // ----------------------------------------------------------
  return (
    <Box sx={{mt: 4}}>
      <Typography variant="h5" gutterBottom>
        Your Activities
      </Typography>

      {/* activities.map iterates over the array and returns one <Card> per item */}
      {activities.map((activity) => (
        <Card key={activity.id} sx={{mb: 2}}>
          {/*
            CardActionArea wraps the entire card content so that
            clicking anywhere on the card navigates to the detail page.
            The URL /activities/:id matches the Route in App.jsx.
          */}
          <CardActionArea onClick={() => navigate(`/activities/${activity.id}`)}>
            <CardContent>
              {/* Activity type label (e.g. RUNNING, CYCLING) */}
              <Typography variant="h6" component="div">
                {activity.type}
              </Typography>

              {/* Duration and calories on one line */}
              <Typography variant="body2" color="text.secondary">
                Duration: {activity.duration} minutes | Calories: {activity.caloriesBurned} kcal
              </Typography>

              {/* Formatted creation timestamp */}
              <Typography variant="caption" color="text.secondary">
                {new Date(activity.createdAt).toLocaleString()}
              </Typography>
            </CardContent>
          </CardActionArea>
        </Card>
      ))}
    </Box>
  )
}

export default ActivityList
