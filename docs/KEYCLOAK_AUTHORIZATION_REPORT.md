# Keycloak Authorization Code + PKCE Troubleshooting Report

## Executive finding

The screenshot shows Keycloak rendering **“Client not found”** while Postman is in its browser-based OAuth 2.0 authorization step. This is an authorization-endpoint failure: Keycloak has not returned an authorization code, Postman has not exchanged a code for tokens, and the request to `http://localhost:8080/api/users/register` has not yet reached the API Gateway.

The leading cause is that the `client_id` in Postman's authorization request does not identify an enabled client in the realm addressed by the authorization URL. A wrong realm/authorization URL can produce the same symptom. The screenshot does not show the Postman client ID or full authorization URL, and the repository contains no Keycloak realm/client export or Admin Console settings. Therefore the exact incorrect value cannot be determined from the available evidence.

This is separate from the Gateway's later JWT validation. Its current configuration makes it a bearer-token resource server; it does not configure Postman or register an OAuth client with Keycloak.

## Evidence reviewed

- `gateway/gateway/src/main/java/com/fitness/gateway/SecurityConfig.java` defines a servlet `SecurityFilterChain`, permits `/actuator/**` and `ERROR` dispatches, requires authentication for every other request, and enables JWT bearer-token validation.
- `gateway/gateway/src/main/resources/application.yml` sets the application name to `api-gateway` and imports configuration from the Config Server at `http://localhost:8888`.
- `configserverc/configserverc/src/main/resources/config/api-gateway.yml` supplies the Gateway routes and this JWK Set URL:

  ```text
  http://127.0.0.1:8180/realms/fitness-oauth2/protocol/openid-connect/certs
  ```

  The configured realm slug is `fitness-oauth2`. The authorization and token endpoints are not configured in the application; they are used by the OAuth client (Postman) during the authorization flow.
- The Gateway routes `/api/users/**` to `USER-SERVICE` without stripping the path.
- `userservice/src/main/java/com/fitness/userservice/controller/UserController.java` exposes `POST /api/users/register`.
- The Gateway has a resource-server dependency and no OAuth2 client/login dependency or registration. That is consistent with an API that validates bearer tokens and does not itself perform browser login.
- The screenshot shows the Postman request to `POST http://localhost:8080/api/users/register`, PKCE challenge method `S256`, the “Send as Basic Auth header” client-authentication selection, and Keycloak's “Client not found” page branded `FITNESS-OAUTH2`. It does not reveal the actual `client_id`, authorization URL, token URL, redirect URI, or Keycloak client settings.
- The maintained source/configuration/tests and relevant documentation were reviewed for Keycloak/OAuth configuration. Generated `target` output and local secret-bearing environment files were excluded. No Keycloak client definition was found in the repository.

## Diagnose by flow stage

| Stage | Expected result | What a failure means |
|---|---|---|
| Postman opens Keycloak authorization URL | Keycloak login/consent page | `Client not found` here points first to an unknown `client_id` or wrong realm/authorization endpoint. The Gateway has not handled the API request. |
| Keycloak redirects to Postman callback | An authorization code is returned | A redirect URI mismatch usually produces a redirect-URI error, not the Gateway's `401`. |
| Postman exchanges code at token endpoint | Access token returned | `invalid_client` here points to client authentication/secret settings; PKCE verifier/challenge errors point to a PKCE mismatch. |
| Postman sends the API request with a bearer access token | Gateway validates JWT and forwards to service | Gateway `401` points to an absent/invalid token, signature, expiry, issuer, or decoder configuration; `403` generally means authenticated but not authorized by a policy. |

The current screenshot fails at the first row. Do not troubleshoot `SecurityConfig.java` as the cause of this particular Keycloak error.

## Ordered fix and verification steps

### 1. Confirm the realm and discovery metadata

Use the same Keycloak host and port that Postman can reach. Based on the repository's JWK URL, first check:

```text
http://127.0.0.1:8180/realms/fitness-oauth2/.well-known/openid-configuration
```

If that address is not reachable from Postman, use the actual Keycloak host/port and check its discovery document instead. Confirm that:

- the document loads successfully;
- `issuer` identifies the expected `fitness-oauth2` realm;
- `authorization_endpoint`, `token_endpoint`, and `jwks_uri` are all for that same realm and host.

Use the endpoint URLs from discovery rather than manually mixing hosts, ports, realm names, or Keycloak base paths. `localhost` and `127.0.0.1` may reach the same local server, but they are not interchangeable issuer strings in JWT validation.

### 2. Check the exact `client_id`

In Keycloak Admin Console, select realm `fitness-oauth2`, then find the client whose **Client ID** is entered in Postman. Confirm the client exists and is enabled. Use the exact Client ID value; do not substitute the realm name unless a client was deliberately created with that ID.

Then inspect the authorization request in Postman or the browser URL and confirm its `client_id` exactly matches that Keycloak Client ID. A mismatch here is the primary explanation for “Client not found.”

### 3. Align Postman and Keycloak client settings for PKCE

For a local Postman public-client setup, configure:

- Authorization Code / Authorization Code + PKCE grant.
- Authorization URL and Access Token URL copied from the realm's discovery document.
- Client ID exactly as configured in the realm.
- Postman's displayed callback URL exactly in Keycloak's **Valid Redirect URIs**. Postman commonly uses `https://oauth.pstmn.io/v1/callback`; use the value actually displayed by the installed Postman version.
- PKCE method `S256` (as shown in the screenshot).
- Keycloak **Standard Flow** enabled.
- A public client (Keycloak client authentication off) if using PKCE without a client secret; no client secret should be sent in the token request. In Postman, select the no-client-authentication option if available.
- Scope `openid` when requesting OpenID Connect identity tokens; add other scopes only if configured and needed.

If the Keycloak client is intentionally confidential, it can also use PKCE, but Postman must send that client's valid secret using the matching authentication method. The screenshot's “Send as Basic Auth header” applies to client authentication at the **token exchange**; it is not the likely cause of the earlier authorization-page “Client not found.” It can become a separate `invalid_client` failure after the redirect if it does not match the Keycloak client type and credentials.

Avoid broad redirect URI wildcards. For a local-only test, register the exact callback displayed by Postman.

### 4. Confirm the authorization request before retrying

The authorization URL should be the realm's discovered `authorization_endpoint` and the request should contain values equivalent to:

```text
client_id=<exact enabled Keycloak Client ID>
response_type=code
redirect_uri=<exact Postman callback>
scope=openid
code_challenge=<Postman-generated challenge>
code_challenge_method=S256
state=<Postman-generated value>
```

Postman generates the challenge and state. Do not copy a stale authorization URL from a previous realm/client setup.

### 5. Verify the token and Gateway separately

After Keycloak redirects successfully, confirm Postman completes the token exchange and receives an access token. Then send the API request with that access token as a bearer token. If the Gateway responds `401`, inspect the token's `iss`, `exp`, signing key, and audience as applicable, and confirm Gateway can retrieve the configured JWK Set.

The Gateway currently configures `jwk-set-uri` but not an explicit issuer. For stronger and less ambiguous validation, configure issuer validation against the exact `issuer` from Keycloak discovery, using a host consistent with the token's `iss`. Retain a direct JWK Set URL only if required by the chosen Spring configuration; do not make issuer validation accept arbitrary realms.

## Separate Gateway policy issue for the shown request

Even after PKCE succeeds, `POST /api/users/register` is currently covered by `.anyRequest().authenticated()`. Therefore it requires a bearer token. If registration is intended to be open to users who do not yet have an account, add a narrowly scoped public rule for only `POST /api/users/register`; do not permit all `/api/users/**` routes. Whether registration should be public is a product/security decision, not the cause of Keycloak's current error.

Before making registration public, address a directly related credential-handling issue found in `userservice`: `UserService.register` stores the submitted password without hashing it, and `UserResponse` includes a `password` field which the service populates from the stored password. Hash passwords with an appropriate password encoder and never serialize them in responses before exposing registration.

## What is not established by repository evidence

- The actual Postman Client ID, authorization URL, token URL, callback URL, scope, and generated authorization query.
- Whether the Keycloak realm slug is exactly `fitness-oauth2` at runtime and whether Keycloak is reachable at port `8180`.
- Whether the expected Keycloak client exists, is enabled, is public or confidential, and has Standard Flow/PKCE/redirect URIs configured.
- Whether a token can be obtained or what response the Gateway returns after token acquisition.

These values need to be checked in Postman and the Keycloak Admin Console; they are not present in the repository or legible in the screenshot.

## Bottom line

First compare Postman's authorization request `client_id` and realm endpoint with the enabled client in Keycloak realm `fitness-oauth2`. Correct that mismatch before changing Gateway security. Once Postman obtains a token, diagnose any subsequent token-exchange or Gateway response as a separate stage.
