# Reusable LLM Prompt: Diagnose Keycloak PKCE Authorization

Copy the prompt below into another LLM and attach the Postman/Keycloak screenshot if available.

---

You are helping diagnose a local Spring Boot microservices application that uses Keycloak for OAuth 2.0 / OpenID Connect and Postman for Authorization Code with PKCE. Analyze the evidence carefully. Do not assume or invent unavailable Keycloak Client IDs, client secrets, ports, realm settings, or Postman URLs. Distinguish confirmed facts from hypotheses and ask for the minimum missing values only if they are essential.

## Observed failure

In Postman, I start an OAuth 2.0 Authorization Code + PKCE flow for:

```text
POST http://localhost:8080/api/users/register
```

Postman shows PKCE `S256`. Its client-authentication selector is set to “Send as Basic Auth header.” In the browser authorization window, Keycloak displays a page branded `FITNESS-OAUTH2` with:

```text
We are sorry...
Client not found.
```

The screenshot does not show the complete authorization URL or the value of `client_id`. It also does not show Keycloak Admin Console client settings.

## Relevant repository facts

- The Gateway module is under `gateway/gateway`.
- `gateway/gateway/src/main/java/com/fitness/gateway/SecurityConfig.java` is a servlet Spring Security configuration. It disables CSRF, permits `/actuator/**` and ERROR dispatches, requires authentication for all other requests, and enables JWT resource-server validation.
- The Gateway is configured as a JWT resource server, not as an OAuth2 browser client. It does not configure Postman's authorization URL or register a client in Keycloak.
- `gateway/gateway/src/main/resources/application.yml` names the app `api-gateway` and imports configuration from `http://localhost:8888`.
- `configserverc/configserverc/src/main/resources/config/api-gateway.yml` routes `/api/users/**` to `USER-SERVICE` and configures the JWK Set URL:

  ```text
  http://127.0.0.1:8180/realms/fitness-oauth2/protocol/openid-connect/certs
  ```

- The apparent realm slug in the repository is `fitness-oauth2`; the runtime Keycloak host, realm configuration, and client registration are not verified.
- `userservice/src/main/java/com/fitness/userservice/controller/UserController.java` has `POST /api/users/register`.
- The Gateway's current `anyRequest().authenticated()` rule means that endpoint requires a bearer token after Postman successfully authenticates.
- The user-service currently stores registration passwords without hashing and returns a `UserResponse` containing a password field. Do not recommend exposing public registration without calling out that this must be corrected first.
- No Keycloak realm export/client config is present in the reviewed maintained repository files.

## Required analysis

1. Explain which component generates the observed `Client not found` page and whether the API Gateway has been called yet.
2. Rank plausible causes, prioritizing an unknown/wrong `client_id` or realm/authorization endpoint mismatch. Explain why the Basic client-auth setting is usually a later token-endpoint concern, not the explanation for this authorization-page response.
3. Give a concrete, ordered checklist to compare the authorization URL, realm discovery document, exact Client ID, enabled status, Standard Flow, PKCE `S256`, callback/Valid Redirect URIs, and public-versus-confidential client authentication.
4. Use placeholders for values not shown. If giving URLs, clearly state that the host must be the one Postman can reach and must agree with Keycloak's discovery `issuer`; do not treat `localhost` and `127.0.0.1` as equivalent issuer strings.
5. Separate authorization-endpoint failures from token-endpoint errors such as `invalid_client` or PKCE verifier errors, and from Gateway `401`/`403` responses after token acquisition.
6. Explain that the shown registration route is currently protected at the Gateway; public-registration policy is a separate decision. Flag password hashing and omission of passwords from API responses before making that endpoint public.
7. Recommend explicit JWT issuer validation at the Gateway against the exact issuer from Keycloak discovery, while avoiding an unverified code change or accepting tokens from arbitrary realms.
8. End with the exact next values to inspect in Postman and Keycloak Admin Console. Do not ask for secrets, access tokens, or raw `.env` contents.

Structure your response as: **Finding**, **Evidence vs. unknowns**, **Ordered fix**, **How to tell which layer fails**, and **Separate registration concern**. Be direct and avoid claiming the exact client ID is known.

---
