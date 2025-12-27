# React Native Keycloak Integration Guide

This guide explains how to implement Keycloak authentication in a React Native application and consume the Storeyes backend API.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Keycloak Configuration](#keycloak-configuration)
4. [Authentication Service](#authentication-service)
5. [Token Storage](#token-storage)
6. [API Client Setup](#api-client-setup)
7. [Sign In Implementation](#sign-in-implementation)
8. [Protected API Calls](#protected-api-calls)
9. [Token Refresh](#token-refresh)
10. [Complete Example](#complete-example)

## Prerequisites

- React Native project (0.60+)
- Keycloak server running at: `http://15.216.37.183`
- Backend API available at: `https://api.storeyes.io`
- Client ID: `storeyes-mobile`

## Installation

### 1. Install Required Packages

```bash
npm install axios
npm install @react-native-async-storage/async-storage
# or
yarn add axios @react-native-async-storage/async-storage
```

### 2. For iOS (if using CocoaPods)

```bash
cd ios && pod install && cd ..
```

## Keycloak Configuration

Your Keycloak client is already configured with:

- **Client ID**: `storeyes-mobile`
- **Access Type**: `public`
- **Direct Access Grants Enabled**: `ON` (for username/password flow)

## Authentication Service

Create an authentication service to handle Keycloak interactions:

```javascript
// services/AuthService.js
import axios from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

const KEYCLOAK_URL = "http://15.216.37.183/realms/storeyes";
const CLIENT_ID = "storeyes-mobile";
const TOKEN_STORAGE_KEY = "@storeyes:access_token";
const REFRESH_TOKEN_STORAGE_KEY = "@storeyes:refresh_token";
const TOKEN_EXPIRY_KEY = "@storeyes:token_expiry";

class AuthService {
  /**
   * Sign in with username and password
   * @param {string} username - User username
   * @param {string} password - User password
   * @returns {Promise<Object>} Token response from Keycloak
   */
  async signIn(username, password) {
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

      const { access_token, refresh_token, expires_in } = response.data;

      // Store tokens
      await this.storeTokens(access_token, refresh_token, expires_in);

      return {
        success: true,
        accessToken: access_token,
        refreshToken: refresh_token,
      };
    } catch (error) {
      console.error("Sign in error:", error.response?.data || error.message);

      // Handle specific error cases
      if (error.response?.status === 401) {
        return {
          success: false,
          error: "Invalid username or password",
        };
      }

      return {
        success: false,
        error:
          error.response?.data?.error_description || "Authentication failed",
      };
    }
  }

  /**
   * Store tokens securely
   * @param {string} accessToken - Access token
   * @param {string} refreshToken - Refresh token
   * @param {number} expiresIn - Expiration time in seconds
   */
  async storeTokens(accessToken, refreshToken, expiresIn) {
    const expiryTime = Date.now() + expiresIn * 1000; // Convert to milliseconds

    await AsyncStorage.multiSet([
      [TOKEN_STORAGE_KEY, accessToken],
      [REFRESH_TOKEN_STORAGE_KEY, refreshToken],
      [TOKEN_EXPIRY_KEY, expiryTime.toString()],
    ]);
  }

  /**
   * Get stored access token
   * @returns {Promise<string|null>} Access token or null
   */
  async getAccessToken() {
    try {
      const token = await AsyncStorage.getItem(TOKEN_STORAGE_KEY);
      return token;
    } catch (error) {
      console.error("Error getting token:", error);
      return null;
    }
  }

  /**
   * Get stored refresh token
   * @returns {Promise<string|null>} Refresh token or null
   */
  async getRefreshToken() {
    try {
      const token = await AsyncStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
      return token;
    } catch (error) {
      console.error("Error getting refresh token:", error);
      return null;
    }
  }

  /**
   * Check if token is expired
   * @returns {Promise<boolean>} True if token is expired or missing
   */
  async isTokenExpired() {
    try {
      const expiryTime = await AsyncStorage.getItem(TOKEN_EXPIRY_KEY);
      if (!expiryTime) return true;

      return Date.now() >= parseInt(expiryTime, 10);
    } catch (error) {
      return true;
    }
  }

  /**
   * Refresh access token using refresh token
   * @returns {Promise<Object>} New token response
   */
  async refreshAccessToken() {
    try {
      const refreshToken = await this.getRefreshToken();
      if (!refreshToken) {
        throw new Error("No refresh token available");
      }

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

      const { access_token, refresh_token, expires_in } = response.data;
      await this.storeTokens(access_token, refresh_token, expires_in);

      return {
        success: true,
        accessToken: access_token,
        refreshToken: refresh_token,
      };
    } catch (error) {
      console.error(
        "Token refresh error:",
        error.response?.data || error.message
      );

      // If refresh fails, user needs to sign in again
      await this.signOut();

      return {
        success: false,
        error: "Token refresh failed. Please sign in again.",
      };
    }
  }

  /**
   * Sign out and clear stored tokens
   */
  async signOut() {
    try {
      await AsyncStorage.multiRemove([
        TOKEN_STORAGE_KEY,
        REFRESH_TOKEN_STORAGE_KEY,
        TOKEN_EXPIRY_KEY,
      ]);
    } catch (error) {
      console.error("Sign out error:", error);
    }
  }

  /**
   * Get current authenticated user info (decode token)
   * @returns {Promise<Object|null>} User info or null
   */
  async getCurrentUser() {
    try {
      const token = await this.getAccessToken();
      if (!token) return null;

      // Decode JWT token (simple base64 decode, no signature verification needed on client)
      const payload = JSON.parse(atob(token.split(".")[1]));

      return {
        id: payload.sub,
        username: payload.preferred_username,
        email: payload.email,
        name: payload.name,
        givenName: payload.given_name,
        familyName: payload.family_name,
      };
    } catch (error) {
      console.error("Error getting user info:", error);
      return null;
    }
  }
}

export default new AuthService();
```

## Token Storage

We use `AsyncStorage` for token storage. For production apps, consider using:

- `react-native-keychain` for more secure storage
- `expo-secure-store` if using Expo

## API Client Setup

Create an API client that automatically handles authentication:

```javascript
// services/ApiClient.js
import axios from "axios";
import AuthService from "./AuthService";

const API_BASE_URL = "https://api.storeyes.io";

// Create axios instance
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

// Request interceptor - Add auth token to requests
apiClient.interceptors.request.use(
  async (config) => {
    // Check if token is expired and refresh if needed
    const isExpired = await AuthService.isTokenExpired();
    let accessToken = await AuthService.getAccessToken();

    if (isExpired && accessToken) {
      // Try to refresh token
      const refreshResult = await AuthService.refreshAccessToken();
      if (refreshResult.success) {
        accessToken = refreshResult.accessToken;
      }
    }

    // Add Authorization header if token exists
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor - Handle 401 errors
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If 401 and haven't retried yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Try to refresh token
        const refreshResult = await AuthService.refreshAccessToken();

        if (refreshResult.success) {
          // Retry original request with new token
          originalRequest.headers.Authorization = `Bearer ${refreshResult.accessToken}`;
          return apiClient(originalRequest);
        } else {
          // Refresh failed, redirect to login
          await AuthService.signOut();
          // You can navigate to login screen here
          // NavigationService.navigate('Login');
        }
      } catch (refreshError) {
        await AuthService.signOut();
        // NavigationService.navigate('Login');
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
```

## Sign In Implementation

Create a sign-in screen component:

```javascript
// screens/LoginScreen.js
import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from "react-native";
import AuthService from "../services/AuthService";

const LoginScreen = ({ navigation }) => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSignIn = async () => {
    if (!username.trim() || !password.trim()) {
      Alert.alert("Error", "Please enter both username and password");
      return;
    }

    setLoading(true);

    try {
      const result = await AuthService.signIn(username.trim(), password);

      if (result.success) {
        // Get user info
        const user = await AuthService.getCurrentUser();

        // Navigate to main app
        navigation.replace("Home"); // or your main screen
      } else {
        Alert.alert("Sign In Failed", result.error || "Invalid credentials");
      }
    } catch (error) {
      Alert.alert("Error", "An unexpected error occurred. Please try again.");
      console.error("Sign in error:", error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Sign In</Text>

      <TextInput
        style={styles.input}
        placeholder="Username"
        value={username}
        onChangeText={setUsername}
        autoCapitalize="none"
        autoCorrect={false}
      />

      <TextInput
        style={styles.input}
        placeholder="Password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoCapitalize="none"
        autoCorrect={false}
      />

      <TouchableOpacity
        style={[styles.button, loading && styles.buttonDisabled]}
        onPress={handleSignIn}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.buttonText}>Sign In</Text>
        )}
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    padding: 20,
    backgroundColor: "#fff",
  },
  title: {
    fontSize: 32,
    fontWeight: "bold",
    marginBottom: 40,
    textAlign: "center",
  },
  input: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 8,
    padding: 15,
    marginBottom: 15,
    fontSize: 16,
  },
  button: {
    backgroundColor: "#007AFF",
    borderRadius: 8,
    padding: 15,
    alignItems: "center",
    marginTop: 10,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "bold",
  },
});

export default LoginScreen;
```

## Protected API Calls

Make authenticated API calls using the API client:

```javascript
// services/AlertService.js
import apiClient from "./ApiClient";

class AlertService {
  /**
   * Get all alerts
   * @returns {Promise<Array>} List of alerts
   */
  async getAlerts() {
    try {
      const response = await apiClient.get("/api/alerts");
      return response.data;
    } catch (error) {
      console.error("Error fetching alerts:", error);
      throw error;
    }
  }

  /**
   * Get alerts by date range
   * @param {Date} startDate - Start date
   * @param {Date} endDate - End date
   * @returns {Promise<Array>} List of alerts
   */
  async getAlertsByDate(startDate, endDate) {
    try {
      const params = {};
      if (startDate) {
        params.date = startDate.toISOString();
      }
      if (endDate) {
        params.endDate = endDate.toISOString();
      }

      const response = await apiClient.get("/api/alerts", { params });
      return response.data;
    } catch (error) {
      console.error("Error fetching alerts by date:", error);
      throw error;
    }
  }

  /**
   * Get unprocessed alerts
   * @returns {Promise<Array>} List of unprocessed alerts
   */
  async getUnprocessedAlerts() {
    try {
      const response = await apiClient.get("/api/alerts", {
        params: { unprocessed: true },
      });
      return response.data;
    } catch (error) {
      console.error("Error fetching unprocessed alerts:", error);
      throw error;
    }
  }

  /**
   * Create a new alert
   * @param {Object} alertData - Alert data
   * @returns {Promise<void>}
   */
  async createAlert(alertData) {
    try {
      await apiClient.post("/api/alerts", alertData);
    } catch (error) {
      console.error("Error creating alert:", error);
      throw error;
    }
  }

  /**
   * Get alert by ID
   * @param {number} id - Alert ID
   * @returns {Promise<Object>} Alert object
   */
  async getAlertById(id) {
    try {
      const response = await apiClient.get(`/api/alerts/${id}`);
      return response.data;
    } catch (error) {
      console.error("Error fetching alert:", error);
      throw error;
    }
  }

  /**
   * Update secondary video for an alert
   * @param {number} id - Alert ID
   * @param {string} secondaryVideoUrl - Secondary video URL
   * @returns {Promise<void>}
   */
  async updateSecondaryVideo(id, secondaryVideoUrl) {
    try {
      await apiClient.put(`/api/alerts/${id}/secondary-video`, {
        secondaryVideoUrl,
      });
    } catch (error) {
      console.error("Error updating secondary video:", error);
      throw error;
    }
  }

  /**
   * Update human judgement for an alert
   * @param {number} id - Alert ID
   * @param {string} humanJudgement - Human judgement value
   * @returns {Promise<Object>} Updated alert
   */
  async updateHumanJudgement(id, humanJudgement) {
    try {
      const response = await apiClient.patch(
        `/api/alerts/${id}/human-judgement`,
        {
          humanJudgement,
        }
      );
      return response.data;
    } catch (error) {
      console.error("Error updating human judgement:", error);
      throw error;
    }
  }
}

export default new AlertService();
```

## Token Refresh

The API client automatically handles token refresh. You can also manually refresh:

```javascript
// Example: Manual token refresh
import AuthService from "./services/AuthService";

const refreshToken = async () => {
  const result = await AuthService.refreshAccessToken();
  if (result.success) {
    console.log("Token refreshed successfully");
  } else {
    // Redirect to login
    console.log("Token refresh failed:", result.error);
  }
};
```

## Complete Example

Here's a complete example of an alerts screen:

```javascript
// screens/AlertsScreen.js
import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
  Alert,
} from "react-native";
import AlertService from "../services/AlertService";
import AuthService from "../services/AuthService";

const AlertsScreen = ({ navigation }) => {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [user, setUser] = useState(null);

  useEffect(() => {
    loadUserInfo();
    loadAlerts();
  }, []);

  const loadUserInfo = async () => {
    const userInfo = await AuthService.getCurrentUser();
    setUser(userInfo);
  };

  const loadAlerts = async () => {
    try {
      setLoading(true);
      const data = await AlertService.getAlerts();
      setAlerts(data);
    } catch (error) {
      if (error.response?.status === 401) {
        Alert.alert(
          "Session Expired",
          "Your session has expired. Please sign in again.",
          [
            {
              text: "OK",
              onPress: () => {
                AuthService.signOut();
                navigation.replace("Login");
              },
            },
          ]
        );
      } else {
        Alert.alert("Error", "Failed to load alerts. Please try again.");
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const onRefresh = () => {
    setRefreshing(true);
    loadAlerts();
  };

  const handleSignOut = async () => {
    Alert.alert("Sign Out", "Are you sure you want to sign out?", [
      { text: "Cancel", style: "cancel" },
      {
        text: "Sign Out",
        style: "destructive",
        onPress: async () => {
          await AuthService.signOut();
          navigation.replace("Login");
        },
      },
    ]);
  };

  const renderAlert = ({ item }) => (
    <View style={styles.alertItem}>
      <Text style={styles.alertTitle}>Alert #{item.id}</Text>
      <Text style={styles.alertText}>
        Status: {item.processed ? "Processed" : "Unprocessed"}
      </Text>
      {item.humanJudgement && (
        <Text style={styles.alertText}>Judgement: {item.humanJudgement}</Text>
      )}
    </View>
  );

  if (loading && alerts.length === 0) {
    return (
      <View style={styles.container}>
        <Text>Loading alerts...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {user && (
        <View style={styles.header}>
          <Text style={styles.welcomeText}>
            Welcome, {user.username || user.name}!
          </Text>
          <TouchableOpacity onPress={handleSignOut}>
            <Text style={styles.signOutText}>Sign Out</Text>
          </TouchableOpacity>
        </View>
      )}

      <FlatList
        data={alerts}
        renderItem={renderAlert}
        keyExtractor={(item) => item.id.toString()}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No alerts found</Text>
          </View>
        }
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 15,
    backgroundColor: "#fff",
    borderBottomWidth: 1,
    borderBottomColor: "#ddd",
  },
  welcomeText: {
    fontSize: 16,
    fontWeight: "bold",
  },
  signOutText: {
    color: "#007AFF",
    fontSize: 16,
  },
  alertItem: {
    backgroundColor: "#fff",
    padding: 15,
    marginVertical: 5,
    marginHorizontal: 15,
    borderRadius: 8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
    elevation: 2,
  },
  alertTitle: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 5,
  },
  alertText: {
    fontSize: 14,
    color: "#666",
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 40,
  },
  emptyText: {
    fontSize: 16,
    color: "#999",
  },
});

export default AlertsScreen;
```

## Navigation Setup Example

```javascript
// App.js or navigation setup
import React, { useEffect, useState } from "react";
import { NavigationContainer } from "@react-navigation/native";
import { createStackNavigator } from "@react-navigation/stack";
import AuthService from "./services/AuthService";
import LoginScreen from "./screens/LoginScreen";
import AlertsScreen from "./screens/AlertsScreen";

const Stack = createStackNavigator();

const App = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const token = await AuthService.getAccessToken();
      const isExpired = await AuthService.isTokenExpired();
      setIsAuthenticated(!!token && !isExpired);
    } catch (error) {
      setIsAuthenticated(false);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return null; // Or your loading component
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {isAuthenticated ? (
          <Stack.Screen name="Alerts" component={AlertsScreen} />
        ) : (
          <Stack.Screen name="Login" component={LoginScreen} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
};

export default App;
```

## Important Notes

### Security Considerations

1. **Token Storage**: Use secure storage in production (e.g., `react-native-keychain`)
2. **HTTPS**: Always use HTTPS in production
3. **Token Expiry**: Tokens expire after 5 minutes - refresh automatically
4. **Error Handling**: Always handle 401 errors and redirect to login

### Common Issues

1. **403 Errors**: Ensure Authorization header format is exactly `Bearer <token>` (with space)
2. **Token Expired**: Check token expiry before making requests
3. **Network Errors**: Handle network connectivity issues gracefully
4. **CORS**: Backend CORS is configured, but ensure requests include proper headers

### Testing

Test your implementation:

```javascript
// Test authentication
const testAuth = async () => {
  const result = await AuthService.signIn("storeyesuser", "userS123");
  console.log("Sign in result:", result);

  const user = await AuthService.getCurrentUser();
  console.log("Current user:", user);

  const alerts = await AlertService.getAlerts();
  console.log("Alerts:", alerts);
};
```

## Summary

1. ✅ **Authentication**: Use `AuthService.signIn()` with username/password
2. ✅ **Token Management**: Tokens are automatically stored and refreshed
3. ✅ **API Calls**: Use `apiClient` which automatically adds Authorization header
4. ✅ **Error Handling**: 401 errors trigger token refresh or logout
5. ✅ **User Info**: Decode token to get user information

This implementation handles all authentication flows and provides a robust foundation for your React Native app.
