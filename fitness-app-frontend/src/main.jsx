import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { AuthProvider } from 'react-oauth2-code-pkce'
import './index.css'
import { store } from './store/store'
import { authConfig } from './store/authConfig'
import App from './App'

// The app is wrapped in both providers so components can access OAuth and Redux state.
// createRoot(document.getElementById('root')).render(
  
//   <StrictMode>
//     <Provider store={store}>
//       <App />
//     </Provider>
//   </StrictMode>,
// )
const root = createRoot(document.getElementById('root'))
root.render(
  <AuthProvider authConfig={authConfig}
                loadingComponent = {<div>Loading...</div>}>
  <Provider store={store}>
    <App />
  </Provider>
  </AuthProvider>,
) 