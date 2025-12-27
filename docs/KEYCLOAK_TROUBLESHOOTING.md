# Keycloak Troubleshooting Guide

## Common 403 Errors and Solutions

### 1. 403 Error from Keycloak (During Login/Token Request)

If you get a 403 error when trying to authenticate with Keycloak, check your Keycloak client configuration:

**In Keycloak Admin Console:**

1. Go to: `Clients` → Select `storeyes-mobile`
2. Check these settings:
   - **Access Type**: Should be `public` (for mobile apps) or `confidential` (if using client secret)
   - **Standard Flow Enabled**: Should be `ON`
   - **Direct Access Grants Enabled**: Should be `ON` (if using username/password login)
   - **Valid Redirect URIs**: Should include your mobile app's redirect URI (e.g., `myapp://callback` or `http://localhost:*`)
   - **Web Origins**: Should be `*` or include your app's origin for CORS

### 2. 403 Error from Backend API

**⚠️ IMPORTANT: The backend is verified working (tested with curl). If you're getting 403 from frontend, see [FRONTEND_DEBUGGING.md](./FRONTEND_DEBUGGING.md) for detailed troubleshooting steps.**

If you get a 403 when calling your backend API:

**Check JWT Token:**

- Is the token being sent in the `Authorization: Bearer <token>` header? ✅ **REQUIRED FORMAT**
- Is the token expired? (Check the `exp` claim)
- Does the token have the correct `audience`? (Should be `storeyes-mobile` or `azp` claim should match)
- Does the token have the correct `issuer`? (Should be `http://15.216.37.183/realms/storeyes`)

**Frontend Requirements (CRITICAL):**

✅ **MUST use Authorization header:**

```javascript
headers: {
  'Authorization': `Bearer ${accessToken}`,  // Space after "Bearer" is required
  'Content-Type': 'application/json'
}
```

❌ **DO NOT use:**

- Query parameters: `?token=...`
- Custom headers: `X-Auth-Token`, `X-Access-Token`, etc.
- Cookie-based auth (unless specifically configured)

**Token Handling:**

- Use `access_token` from Keycloak token response (not `token` or `id_token`)
- Handle 401 responses (token expired) by refreshing the token
- Store token securely (localStorage, secure storage, or memory)

**Check Backend Logs:**

- Look for authentication/authorization errors in the backend logs
- Check if JWT validation is failing

**Test with cURL:**

```bash
# Get a token from Keycloak
curl -X POST http://15.216.37.183/realms/storeyes/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=storeyes-mobile" \
  -d "username=storeyesuser" \
  -d "password=userS123"

# Use the access_token from the response to call your API
curl -X GET https://api.storeyes.io/api/alerts \
  -H "Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJqRWJuY3VIS3E5Y1NxNGdzVmVMQlBTV0ZCdU5DR2NTTzVfbFY0eGRXTWJnIn0.eyJleHAiOjE3NjY4NDc1MjAsImlhdCI6MTc2Njg0NzIyMCwianRpIjoib25ydHJvOjg4ZDY5ZjU3LTJjOTktN2UzYS0yMjQ1LTQ3NDY0NzQwZTgyOSIsImlzcyI6Imh0dHA6Ly8xNS4yMTYuMzcuMTgzL3JlYWxtcy9zdG9yZXllcyIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiIwNWQ5ZmRiNi02YWMwLTRlMWMtYTMwOS0wODFlNDEwMTJhMmUiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJzdG9yZXllcy1tb2JpbGUiLCJzaWQiOiI3MzBkNzFhMi02OWZhLTQzNmMtZWNhZC0zMzkzNDg5YWRkNjIiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIioiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJkZWZhdWx0LXJvbGVzLXN0b3JleWVzIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5hbWUiOiJOZXcgU3RvcmV5ZXMiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzdG9yZXllc3VzZXIiLCJnaXZlbl9uYW1lIjoiTmV3IiwiZmFtaWx5X25hbWUiOiJTdG9yZXllcyIsImVtYWlsIjoic3RvcmV5ZXN1MUBnYW1pbC5jb20ifQ.5DtJnt7WvTn65bhtskt82dCaNx5bO0EOKZNpotpFpglrgUzrOz1U_IhyV_ovDyHnGpf1rIhZuyzRO5IMKtQIEg"
```

### 3. CORS 403 Errors

If you get CORS-related 403 errors:

**Backend CORS Configuration:**

- Check that OPTIONS requests are allowed (already configured)
- Verify CORS headers are being sent correctly

**Keycloak CORS:**

- Ensure `Web Origins` in Keycloak client settings includes your app origin
- Set to `*` for development (not recommended for production)

### 4. Common Keycloak Client Configuration Issues

**For Mobile Apps:**

```
Access Type: public
Standard Flow Enabled: ON
Direct Access Grants Enabled: ON
Implicit Flow Enabled: OFF (deprecated)
Valid Redirect URIs: myapp://callback (or your app's scheme)
Web Origins: * (or specific origins)
```

## Debugging Steps

1. **Check Keycloak Logs:**

   - SSH into your Keycloak server
   - Check Keycloak container logs: `docker logs <keycloak-container>`

2. **Check Backend Logs:**

   - Check application logs for authentication errors
   - Look for JWT validation failures

3. **Test Token Manually:**

   - Use Postman or cURL to get a token directly from Keycloak
   - Try to decode the JWT token at https://jwt.io
   - Verify the token claims match your configuration

4. **Verify Keycloak Realm Settings:**
   - Realm Settings → Security → Require SSL: `none` (for HTTP)
   - Realm Settings → Login → Verify email: `OFF` (if not needed)

## Frontend Integration Example

Here's a complete example of how to properly call the API from frontend:

```javascript
async function callAPI(endpoint, method = "GET", body = null) {
  const accessToken = getAccessToken(); // Retrieve from storage

  if (!accessToken) {
    throw new Error("No access token available");
  }

  const headers = {
    Authorization: `Bearer ${accessToken}`, // ✅ CRITICAL: Use this exact format
    "Content-Type": "application/json",
  };

  const config = {
    method: method,
    headers: headers,
  };

  if (body) {
    config.body = JSON.stringify(body);
  }

  const response = await fetch(`https://api.storeyes.io${endpoint}`, config);

  // Handle token expiration
  if (response.status === 401) {
    const newToken = await refreshAccessToken();
    headers["Authorization"] = `Bearer ${newToken}`;
    return await fetch(`https://api.storeyes.io${endpoint}`, config);
  }

  if (!response.ok) {
    throw new Error(`API error: ${response.status} ${response.statusText}`);
  }

  return await response.json();
}
```

## Quick Checklist

- [ ] User account has no required actions in Keycloak
- [ ] Keycloak client has correct redirect URIs configured
- [ ] Keycloak client has Direct Access Grants enabled (for password flow)
- [ ] Backend can reach Keycloak JWKS endpoint
- [ ] JWT token has correct audience (aud="account" with azp="storeyes-mobile" is valid)
- [ ] **Frontend uses `Authorization: Bearer <token>` header format**
- [ ] **Frontend uses `access_token` from Keycloak response (not id_token)**
- [ ] CORS is properly configured in both Keycloak and backend
- [ ] Backend OPTIONS requests are allowed for CORS preflight
- [ ] Frontend handles 401 responses and refreshes tokens
