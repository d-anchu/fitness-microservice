# Fitness App Frontend

A React single-page application built with Vite. The current code is an early
authentication-focused skeleton: it shows login/logout controls, while the
activity form, list, and detail components are placeholders.

## Run the app locally

### Requirements

- Node.js and npm
- The OAuth identity provider configured in `src/store/authConfig.js`

The current OAuth configuration expects a local provider at
`http://localhost:8180`, with the `fitness-oauth2` realm. Start that service
before trying to sign in. The app's local Vite URL is `http://localhost:5173`.
For logout to end the provider session as well as clear the frontend session,
allow `http://localhost:5173` as a valid post-logout redirect URI for the
`oauth2-pkce-client` client in the identity provider.

### Install and start

Run these commands from this folder:

```sh
npm install
npm run dev
```

Open the local URL printed by Vite. The frontend can start without signing in,
but authentication requires the identity provider and matching OAuth client
configuration.

## Useful commands

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start the local development server with hot reload. |
| `npm run build` | Create an optimized production bundle in `dist/`. |
| `npm run preview` | Serve the most recently built bundle locally. |
| `npm run lint` | Check JavaScript and JSX against the ESLint rules. |

There is currently no test script in `package.json`.

## Project map

| Path | What it does |
| --- | --- |
| `index.html` | The HTML page and the `root` element where React is mounted. |
| `src/main.jsx` | Starts React and provides OAuth and Redux context to the app. |
| `src/App.jsx` | Contains the current login/logout interface. |
| `src/store/authConfig.js` | OAuth client, endpoint, callback, and scope settings. |
| `src/store/authSlice.js` | Authentication state and its local-storage persistence. |
| `src/store/store.js` | Creates the Redux store and registers the auth reducer. |
| `src/components/ActivityForm.jsx` | Placeholder for an activity-entry form. |
| `src/components/ActivityList.jsx` | Placeholder for an activity list. |
| `src/components/ActivityDetail.jsx` | Placeholder for activity details. |
| `src/index.css`, `src/App.css` | Global and component-level styling. |

## Beginner walkthrough

1. **React starts in `src/main.jsx`.** `createRoot` attaches the React app to
   the `root` element from `index.html`.
2. **Providers make shared services available.** `AuthProvider` makes OAuth
   state and login methods available to child components. Redux's `Provider`
   makes the shared Redux store available.
3. **`src/App.jsx` displays the current interface.** It uses the OAuth
   provider's login/logout methods and dispatches Redux actions when the token
   changes.
4. **The auth slice stores the session.** `src/store/authSlice.js` updates the
   Redux auth state and also writes the user and token to browser
   `localStorage`, so they can be read after a page reload.
5. **The activity components are not implemented yet.** Each currently
   displays a placeholder label; they do not submit or retrieve activities.

In simple terms, **React draws the page**, **OAuth handles sign-in**, and
**Redux shares the signed-in user's state between components**.

## Review notes (2026-10-09)

The checks below describe the current frontend state. No application behavior
or authentication logic was changed during this review.

### Checks run

- `npm run build` — **passed** in this workspace.
- `npm run lint` — **failed** with 9 ESLint errors.
- `npm audit --omit=dev` — **0 production dependency vulnerabilities**
  reported by the audit run. This command does not include development
  dependencies.
- No high-confidence exploitable vulnerability was identified in the
  frontend review.

### Known issues

- **The app's authentication hook usage needs correction.** In
  `src/App.jsx`, `useContext` and `useState` are used without being imported.
  Also, `useAuthContext` is a hook and should be called directly rather than
  passed to React's `useContext`. These issues can prevent the app from
  rendering. They were not changed because correcting them changes
  application code behavior.
- **Lint currently fails.** The 9 reported errors include the missing hook
  names above, unused `isAuthenticated` and `authReady` values, the state
  update inside the effect, unused `React` imports in the three activity
  components, and the unused `StrictMode` import in `src/main.jsx`.
- **Production OAuth settings need deployment values.** The endpoints and
  redirect URI in `src/store/authConfig.js` use `localhost` and plain HTTP for
  local development. Configure the real identity-provider URLs and use HTTPS
  before deployment.
- **Treat browser-stored and displayed tokens as sensitive.** The current auth
  slice stores the token in `localStorage`, and `src/App.jsx` displays the
  token and token data on screen. Avoid exposing real tokens in a production
  UI. Changing that display or storage behavior was outside this
  documentation-only change.

The production build succeeding does not mean the app is ready to use: a
build can complete while lint and browser runtime checks still identify
problems. Fixes to the known application-code issues require approval before
they are made.
