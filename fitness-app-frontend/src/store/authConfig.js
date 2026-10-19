// OAuth endpoints and callback settings for the local identity-provider setup.
// Replace localhost URLs with the deployed identity provider before production use.
export const authConfig = {
  clientId: 'oauth2-pkce-client',
  authorizationEndpoint: 'http://localhost:8180/realms/fitness-oauth2/protocol/openid-connect/auth',
  tokenEndpoint: 'http://localhost:8180/realms/fitness-oauth2/protocol/openid-connect/token',
  logoutEndpoint: 'http://localhost:8180/realms/fitness-oauth2/protocol/openid-connect/logout',
  redirectUri: 'http://localhost:5173',
  logoutRedirect: 'http://localhost:5173',
  scope: 'openid profile email offline_access',
  autoLogin: false,
  onRefreshTokenExpire: (event) => event.login(),
}
