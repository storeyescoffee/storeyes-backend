# Coffee Tracker API Documentation

## Overview

The Coffee Tracker API provides endpoints to fetch coffee tracker events for a store. Events represent tracked quantities with a status (e.g. GENESIS, INITIAL, COMPLETED). All endpoints require authentication via Keycloak JWT tokens.

**Base URL:** `/api/coffee-tracker`

**Authentication:** All endpoints require a valid JWT token in the `Authorization` header:
```
Authorization: Bearer <jwt_token>
```

**Store resolution:** The store is automatically resolved from the authenticated user's role mapping. Users can only access events for their own store.

---

## Endpoints

### Get completed events by store and date

Retrieves all **COMPLETED** state events for the current user's store on the given date. Results are ordered by timestamp ascending.

**Endpoint:** `GET /api/coffee-tracker/completed`

**Authentication:** Required

**Query parameters:**

| Parameter | Type   | Required | Description                    |
|-----------|--------|----------|--------------------------------|
| `date`    | String | Yes      | Date in ISO format `YYYY-MM-DD` |

**Response:** `200 OK`

**Response body:** Array of event objects. Empty array `[]` if there are no COMPLETED events for that store and date.

```json
[
  {
    "id": 101,
    "date": "2025-03-07",
    "timestamp": "2025-03-07T14:30:00",
    "quantity": 2,
    "status": "COMPLETED",
    "previousId": 100
  },
  {
    "id": 102,
    "date": "2025-03-07",
    "timestamp": "2025-03-07T16:45:00",
    "quantity": 1,
    "status": "COMPLETED",
    "previousId": 101
  }
]
```

**Response fields:**

| Field       | Type   | Description                                      |
|------------|--------|--------------------------------------------------|
| `id`       | Long   | Unique event ID                                  |
| `date`     | String | Event date (YYYY-MM-DD)                          |
| `timestamp`| String | Event date-time (ISO-8601)                       |
| `quantity` | Integer| Tracked quantity                                 |
| `status`   | String | Event status; this endpoint returns `COMPLETED` |
| `previousId` | Long | ID of the previous event in the chain, or `null` |

**Error responses:**

- `400 Bad Request` – Missing or invalid `date` (must be `YYYY-MM-DD`)
- `401 Unauthorized` – User is not authenticated
- `500 Internal Server Error` – Store context not found for current user (user has no store/role mapping)

**Example request:**

```bash
curl -X GET "https://api.example.com/api/coffee-tracker/completed?date=2025-03-07" \
  -H "Authorization: Bearer <jwt_token>"
```

**Example (JavaScript):**

```javascript
const date = '2025-03-07';
const response = await fetch(
  `https://api.example.com/api/coffee-tracker/completed?date=${encodeURIComponent(date)}`,
  {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
  }
);
const events = await response.json();
```
