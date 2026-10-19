import { useState } from 'react'
import { useSelector } from 'react-redux'
import { Alert, Box, Button, MenuItem, TextField } from '@mui/material'
import { addActivity } from '../services/api'

const initialActivity = {
  type: 'RUNNING',
  duration: '',
  caloriesBurned: '',
}

/*
 * onActivityAdded – optional callback prop supplied by ActivitiesPage.
 * Called after a successful activity submission so the parent can
 * refresh the ActivityList without a full page reload.
 */
const ActivityForm = ({ onActivityAdded }) => {
  const userId = useSelector((state) => state.auth.userId)
  const [activity, setActivity] = useState(initialActivity)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState(null)

  const handleSubmit = async (event) => {
    event.preventDefault()

    


    setMessage(null)

    if (!userId) {
      setMessage({
        severity: 'error',
        text: 'Please log in before adding an activity.',
      })
      return
    }

    setSubmitting(true)
    try {
      await addActivity({
        userId,
        type: activity.type,
        duration: Number(activity.duration),
        caloriesBurned: Number(activity.caloriesBurned),
        additionalMetrics: {},
      })
      setActivity(initialActivity)
      setMessage({ severity: 'success', text: 'Activity added successfully.' })

      /*
       * Notify the parent (ActivitiesPage) that a new activity was added.
       * The optional-chaining (?.) means the form still works if the prop
       * is not provided (e.g. if ActivityForm is used outside ActivitiesPage).
       */
      if (onActivityAdded) {
        onActivityAdded()
      }
    } catch (error) {
      console.error(error);
      const responseMessage = error.response?.data?.message
      const text =
        (typeof responseMessage === 'string' && responseMessage) ||
        error.message ||
        'Could not add the activity. Please try again.'
      setMessage({ severity: 'error', text })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box component="form" onSubmit={handleSubmit} sx={{ mb: 4 }}>
      {message && (
        <Alert severity={message.severity} sx={{ mb: 2 }}>
          {message.text}
        </Alert>
      )}

      <TextField
        select
        fullWidth
        required
        label="Activity Type *"
        value={activity.type}
        onChange={(event) =>
          setActivity({ ...activity, type: event.target.value })
        }
        sx={{ mb: 2 }}
        helperText="Select the type of activity you performed"
      >
        <MenuItem value="RUNNING">🏃 Running</MenuItem>
        <MenuItem value="CYCLING">🚴 Cycling</MenuItem>
        <MenuItem value="SWIMMING">🏊 Swimming</MenuItem>
        <MenuItem value="WALKING">🚶 Walking</MenuItem>
        <MenuItem value="WEIGHT_TRAINING">🏋️ Weight Training</MenuItem>
        <MenuItem value="YOGA">🧘 Yoga</MenuItem>
        <MenuItem value="CARDIO">💪 Cardio</MenuItem>
        <MenuItem value="STRETCHING">🤸 Stretching</MenuItem>
        <MenuItem value="OTHER">➕ Other</MenuItem>
      </TextField>

      <TextField
        fullWidth
        required
        label="Duration (Minutes) *"
        type="number"
        slotProps={{ htmlInput: { min: 1, max: 999, step: 1 } }}
        value={activity.duration}
        onChange={(event) =>
          setActivity({ ...activity, duration: event.target.value })
        }
        sx={{ mb: 2 }}
        helperText="How long did you exercise? (in minutes)"
        placeholder="e.g., 30"
      />

      <TextField
        fullWidth
        required
        label="Calories Burned *"
        type="number"
        slotProps={{ htmlInput: { min: 0, max: 9999, step: 1 } }}
        value={activity.caloriesBurned}
        onChange={(event) =>
          setActivity({ ...activity, caloriesBurned: event.target.value })
        }
        sx={{ mb: 2 }}
        helperText="Estimated calories burned during activity"
        placeholder="e.g., 250"
      />

      <Button type="submit" variant="contained" disabled={submitting}>
        {submitting ? 'Adding...' : 'Add Activity'}
      </Button>
    </Box>
  )
}

export default ActivityForm
