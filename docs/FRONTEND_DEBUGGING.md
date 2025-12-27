# Frontend Debugging Guide - 403 Errors

## ⚠️ Important: Backend is Working Correctly

The backend has been tested and verified working with curl:

```bash
curl -i -X GET https://api.storeyes.io/api/alerts \
  -H "Authorization: Bearer <token>"
# Returns: HTTP/1.1 200
```

**If you're getting 403 errors from the frontend, the issue is in how the request is being sent, not the backend configuration.**

## ✅ Backend Configuration (Verified Working)

- ✅ **ES256 algorithm**: Supported (automatically via JWKS)
- ✅ **Audience validation**: Accepts `aud="account"` when `azp="storeyes-mobile"`
- ✅ **CORS**: Configured to allow all origins
- ✅ **JWT validation**: Working correctly

## 🔍 Frontend Debugging Steps

### 1. Verify Authorization Header Format

**✅ CORRECT Format:**

```javascript
headers: {
  'Authorization': `Bearer ${accessToken}`,  // Space after "Bearer" is REQUIRED
  'Content-Type': 'application/json'
}
```

**❌ WRONG Formats (will cause 403):**

```javascript
// Missing space
'Authorization': `Bearer${accessToken}`

// Wrong case
'Authorization': `bearer ${accessToken}`

// Custom header
'X-Auth-Token': accessToken

// Query parameter
fetch(`${url}?token=${accessToken}`)
```

### 2. Verify Token Source

Make sure you're using the correct token field from Keycloak response:

```javascript
// ✅ CORRECT - Use access_token
const tokenResponse = await fetch(
  "http://15.216.37.183/realms/storeyes/protocol/openid-connect/token",
  {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=password&client_id=storeyes-mobile&username=...&password=...",
  }
);

const data = await tokenResponse.json();
const accessToken = data.access_token; // ✅ Use this

// ❌ WRONG - Don't use these
// const wrongToken = data.token;        // ❌ Doesn't exist
// const wrongToken = data.id_token;     // ❌ Wrong token type
// const wrongToken = data;              // ❌ Entire object
```

### 3. Check Request Headers (Browser DevTools)

**Open Browser DevTools → Network Tab → Check the failed request:**

1. **Headers Tab:**

   - Look for `Authorization` header
   - Value should be: `Bearer eyJhbGci...` (starts with "Bearer " followed by space)
   - Header name is case-sensitive: `Authorization` (capital A)

2. **Request Headers should look like:**

   ```
   Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJqRWJuY3VIS3E5Y1NxNGdzVmVMQlBTV0ZCdU5DR2NTTzVfbFY0eGRXTWJnIn0...
   Content-Type: application/json
   ```

3. **If Authorization header is missing or malformed:**
   - Check your fetch/axios configuration
   - Ensure token is not null/undefined
   - Verify interceptor/middleware is adding the header correctly

### 4. Verify Token is Not Expired

Tokens expire after 300 seconds (5 minutes). Check token expiration:

```javascript
// Decode JWT to check expiration
function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    const expirationTime = payload.exp * 1000; // Convert to milliseconds
    return Date.now() >= expirationTime;
  } catch (e) {
    return true; // If can't decode, consider expired
  }
}

if (isTokenExpired(accessToken)) {
  // Refresh the token
  const newToken = await refreshAccessToken();
}
```

### 5. Compare Your Request with Working cURL

**Working cURL command:**

```bash
curl -i -X GET https://api.storeyes.io/api/alerts \
  -H "Authorization: Bearer eyJhbGciOiJFUzI1NiIs..."
```

**Your JavaScript should match exactly:**

```javascript
// Should produce the EXACT same request as above cURL
fetch("https://api.storeyes.io/api/alerts", {
  method: "GET",
  headers: {
    Authorization: `Bearer ${accessToken}`,
    // Don't add extra headers unless necessary
  },
});
```

### 6. Check for CORS Preflight Issues

If you see an OPTIONS request failing:

- The backend allows OPTIONS requests
- Check that your request doesn't add custom headers that trigger preflight
- Standard headers like `Authorization` and `Content-Type` are fine

### 7. Test with Exact Same Token

**Copy the exact token from your curl test and use it in frontend:**

```javascript
// Use the EXACT token that works in curl
const testToken = "eyJhbGciOiJFUzI1NiIs..."; // From working curl command

fetch("https://api.storeyes.io/api/alerts", {
  headers: {
    Authorization: `Bearer ${testToken}`,
  },
})
  .then((r) => console.log("Status:", r.status))
  .catch((e) => console.error("Error:", e));
```

If this works, the issue is with token retrieval/refresh logic.

### 8. Common Mistakes to Avoid

❌ **Don't:**

- Add token to URL: `?token=...`
- Use custom headers: `X-Auth-Token`, `X-Access-Token`
- Store token incorrectly (losing part of it)
- Send expired tokens without refreshing
- Use `id_token` instead of `access_token`

✅ **Do:**

- Always use `Authorization: Bearer <token>` header
- Always use `access_token` from Keycloak response
- Handle 401 responses by refreshing token
- Log the actual request headers before sending

### 9. Debugging Code Example

```javascript
async function debugAPIRequest() {
  const accessToken = getAccessToken(); // Your token retrieval function

  console.log("Token exists:", !!accessToken);
  console.log("Token length:", accessToken?.length);
  console.log("Token starts with:", accessToken?.substring(0, 20));

  // Check if token is expired
  try {
    const payload = JSON.parse(atob(accessToken.split(".")[1]));
    console.log("Token payload:", payload);
    console.log("Token expires:", new Date(payload.exp * 1000));
    console.log("Token expired?", Date.now() >= payload.exp * 1000);
    console.log("Token azp:", payload.azp);
    console.log("Token aud:", payload.aud);
  } catch (e) {
    console.error("Could not decode token:", e);
  }

  // Make request and log everything
  const response = await fetch("https://api.storeyes.io/api/alerts", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  console.log("Response status:", response.status);
  console.log("Response headers:", Object.fromEntries(response.headers));

  if (!response.ok) {
    const errorText = await response.text();
    console.error("Error response:", errorText);
  } else {
    const data = await response.json();
    console.log("Success:", data);
  }
}
```

## 📋 Checklist for Frontend Team

- [ ] Authorization header is exactly: `Bearer <token>` (with space)
- [ ] Using `access_token` from Keycloak response (not `id_token`)
- [ ] Token is not null/undefined before sending request
- [ ] Token is not expired (check `exp` claim)
- [ ] Request URL is correct: `https://api.storeyes.io/api/alerts`
- [ ] No extra custom headers that might cause issues
- [ ] Network tab shows the Authorization header is being sent
- [ ] Tested with the exact same token that works in curl

## 🆘 Still Getting 403?

1. **Capture the exact request** from Browser DevTools Network tab
2. **Compare with working curl command** - what's different?
3. **Share the request details** (headers, URL, method) for backend team to check
4. **Check backend logs** - are there any authentication errors?

## ✅ Expected Behavior

When working correctly, you should see:

- **Status**: 200 OK
- **Response**: JSON array (empty `[]` if no alerts)
- **Headers**: `Content-Type: application/json`

If you see 403, the backend is rejecting the authentication, which means:

- Token is missing, malformed, or expired
- Authorization header format is incorrect
- Request is somehow different from the working curl command
