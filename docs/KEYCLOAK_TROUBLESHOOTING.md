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

If you get a 403 when calling your backend API:

**Check JWT Token:**

- Is the token being sent in the `Authorization: Bearer <token>` header?
- Is the token expired? (Check the `exp` claim)
- Does the token have the correct `audience`? (Should be `storeyes-mobile`)
- Does the token have the correct `issuer`? (Should be `http://15.216.37.183/realms/storeyes`)

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
  -d "username=your-username" \
  -d "password=your-password"

# Use the access_token from the response to call your API
curl -X GET https://api.storeyes.io/api/alerts \
  -H "Authorization: Bearer <access_token_from_above>"
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

## Quick Checklist

- [ ] User account has no required actions in Keycloak
- [ ] Keycloak client has correct redirect URIs configured
- [ ] Keycloak client has Direct Access Grants enabled (for password flow)
- [ ] Backend can reach Keycloak JWKS endpoint
- [ ] JWT token has correct audience and issuer
- [ ] CORS is properly configured in both Keycloak and backend
- [ ] Backend OPTIONS requests are allowed for CORS preflight
