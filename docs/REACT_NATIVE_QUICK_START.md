# React Native Keycloak Quick Start Guide

This is a quick reference guide for integrating Keycloak authentication in React Native. For detailed documentation, see [REACT_NATIVE_KEYCLOAK_INTEGRATION.md](./REACT_NATIVE_KEYCLOAK_INTEGRATION.md).

## Installation

```bash
npm install axios @react-native-async-storage/async-storage
# or
yarn add axios @react-native-async-storage/async-storage
```

## Configuration Constants

```javascript
const KEYCLOAK_URL = "http://15.216.37.183/realms/storeyes";
const CLIENT_ID = "storeyes-mobile";
const API_BASE_URL = "https://api.storeyes.io";
```

## 1. Sign In Function

```javascript
import axios from "axios";

const signIn = async (username, password) => {
  try {
    const response = await axios.post(
      `${KEYCLOAK_URL}/protocol/openid-connect/token`,
      new URLSearchParams({
        grant_type: "password",
        client_id: CLIENT_ID,
        username: username,
        password: password,
      }),
      {
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
      }
    );

    // Store the access_token
    const { access_token, refresh_token, expires_in } = response.data;
    // Save to AsyncStorage or secure storage
    await AsyncStorage.setItem("access_token", access_token);
    await AsyncStorage.setItem("refresh_token", refresh_token);

    return { success: true, token: access_token };
  } catch (error) {
    return {
      success: false,
      error: error.response?.data?.error_description || "Sign in failed",
    };
  }
};
```

## 2. Make Authenticated API Call

```javascript
import axios from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

const callAPI = async (endpoint, method = "GET", data = null) => {
  // Get token from storage
  const token = await AsyncStorage.getItem("access_token");

  if (!token) {
    throw new Error("No access token available");
  }

  const config = {
    method: method,
    url: `${API_BASE_URL}${endpoint}`,
    headers: {
      Authorization: `Bearer ${token}`, // ⚠️ CRITICAL: Space after "Bearer"
      "Content-Type": "application/json",
    },
  };

  if (data) {
    config.data = data;
  }

  try {
    const response = await axios(config);
    return response.data;
  } catch (error) {
    if (error.response?.status === 401) {
      // Token expired, refresh or redirect to login
      throw new Error("Session expired");
    }
    throw error;
  }
};

// Usage
const alerts = await callAPI("/api/alerts");
```

## 3. Complete Sign In Screen Example

```javascript
import React, { useState } from "react";
import { View, Text, TextInput, TouchableOpacity, Alert } from "react-native";
import axios from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

const LoginScreen = ({ navigation }) => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSignIn = async () => {
    setLoading(true);

    try {
      // Sign in to Keycloak
      const response = await axios.post(
        "http://15.216.37.183/realms/storeyes/protocol/openid-connect/token",
        new URLSearchParams({
          grant_type: "password",
          client_id: "storeyes-mobile",
          username: username,
          password: password,
        }),
        {
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
          },
        }
      );

      // Store tokens
      await AsyncStorage.multiSet([
        ["access_token", response.data.access_token],
        ["refresh_token", response.data.refresh_token],
      ]);

      // Navigate to home
      navigation.replace("Home");
    } catch (error) {
      Alert.alert(
        "Sign In Failed",
        error.response?.data?.error_description || "Invalid credentials"
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={{ flex: 1, justifyContent: "center", padding: 20 }}>
      <TextInput
        placeholder="Username"
        value={username}
        onChangeText={setUsername}
        style={{ borderWidth: 1, padding: 10, marginBottom: 10 }}
      />
      <TextInput
        placeholder="Password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        style={{ borderWidth: 1, padding: 10, marginBottom: 10 }}
      />
      <TouchableOpacity
        onPress={handleSignIn}
        disabled={loading}
        style={{ backgroundColor: "#007AFF", padding: 15, borderRadius: 8 }}
      >
        <Text style={{ color: "white", textAlign: "center" }}>
          {loading ? "Signing in..." : "Sign In"}
        </Text>
      </TouchableOpacity>
    </View>
  );
};
```

## 4. API Client with Auto Token Refresh

```javascript
import axios from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

const API_BASE_URL = "https://api.storeyes.io";
const KEYCLOAK_URL = "http://15.216.37.183/realms/storeyes";
const CLIENT_ID = "storeyes-mobile";

// Create axios instance
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

// Add token to requests
apiClient.interceptors.request.use(
  async (config) => {
    const token = await AsyncStorage.getItem("access_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Handle 401 - refresh token
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      try {
        // Try to refresh token
        const refreshToken = await AsyncStorage.getItem("refresh_token");
        if (refreshToken) {
          const response = await axios.post(
            `${KEYCLOAK_URL}/protocol/openid-connect/token`,
            new URLSearchParams({
              grant_type: "refresh_token",
              client_id: CLIENT_ID,
              refresh_token: refreshToken,
            }),
            {
              headers: {
                "Content-Type": "application/x-www-form-urlencoded",
              },
            }
          );

          await AsyncStorage.setItem(
            "access_token",
            response.data.access_token
          );
          await AsyncStorage.setItem(
            "refresh_token",
            response.data.refresh_token
          );

          // Retry original request
          error.config.headers.Authorization = `Bearer ${response.data.access_token}`;
          return axios.request(error.config);
        }
      } catch (refreshError) {
        // Refresh failed, redirect to login
        await AsyncStorage.multiRemove(["access_token", "refresh_token"]);
        // navigation.navigate('Login'); // Uncomment and import navigation
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;

// Usage
import apiClient from "./apiClient";

const getAlerts = async () => {
  const response = await apiClient.get("/api/alerts");
  return response.data;
};
```

## 5. Sign Out

```javascript
import AsyncStorage from "@react-native-async-storage/async-storage";

const signOut = async () => {
  await AsyncStorage.multiRemove(["access_token", "refresh_token"]);
  // Navigate to login screen
  navigation.replace("Login");
};
```

## 6. Get Current User Info

```javascript
import AsyncStorage from "@react-native-async-storage/async-storage";

const getCurrentUser = async () => {
  const token = await AsyncStorage.getItem("access_token");
  if (!token) return null;

  // Decode JWT (simple base64 decode)
  const payload = JSON.parse(atob(token.split(".")[1]));

  return {
    id: payload.sub,
    username: payload.preferred_username,
    email: payload.email,
    name: payload.name,
  };
};
```

## Critical Requirements

### ✅ Authorization Header Format

**MUST be exactly:**

```javascript
headers: {
  'Authorization': `Bearer ${token}`  // Space after "Bearer" is REQUIRED
}
```

### ✅ Use access_token

```javascript
const { access_token } = response.data; // ✅ Correct
// NOT: response.data.token
// NOT: response.data.id_token
```

### ✅ Error Handling

```javascript
// Handle 401 - token expired
if (error.response?.status === 401) {
  // Refresh token or redirect to login
}
```

## Common API Endpoints

```javascript
// Get all alerts
GET /api/alerts

// Get alerts with filters
GET /api/alerts?unprocessed=true
GET /api/alerts?date=2025-12-27T10:00:00
GET /api/alerts?date=2025-12-27T00:00:00&endDate=2025-12-27T23:59:59

// Get alert by ID
GET /api/alerts/:id

// Create alert
POST /api/alerts
Body: { ... }

// Update secondary video
PUT /api/alerts/:id/secondary-video
Body: { secondaryVideoUrl: "..." }

// Update human judgement
PATCH /api/alerts/:id/human-judgement
Body: { humanJudgement: "..." }
```

## Testing

Test with your credentials:

```javascript
// Test sign in
const result = await signIn("storeyesuser", "userS123");
console.log(result);

// Test API call
const alerts = await callAPI("/api/alerts");
console.log(alerts);
```

## Troubleshooting

### 403 Forbidden Error

1. ✅ Check Authorization header format: `Bearer ${token}` (with space)
2. ✅ Verify you're using `access_token` not `id_token`
3. ✅ Ensure token is not expired
4. ✅ Check token is being sent in headers, not query params

### 401 Unauthorized Error

1. ✅ Token expired - refresh token or sign in again
2. ✅ Invalid token - get new token from Keycloak
3. ✅ Token not included in request

### Network Error

1. ✅ Check internet connectivity
2. ✅ Verify API URL is correct: `https://api.storeyes.io`
3. ✅ Check Keycloak URL is accessible: `http://15.216.37.183`

For more details, see the full [React Native Keycloak Integration Guide](./REACT_NATIVE_KEYCLOAK_INTEGRATION.md).
