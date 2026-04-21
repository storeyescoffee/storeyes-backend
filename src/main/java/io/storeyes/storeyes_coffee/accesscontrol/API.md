# Access control API

Base path: `/api/access-control`

All routes require a valid JWT (OAuth2 resource server). The **store** used for filtering is taken from the authenticated user’s context (`CurrentStoreContext`), not from the request body or a path variable.

---

## List events by date

Returns fingerprint (and other) access-control events for the **current user’s store** on a single **local calendar day**, ordered by event time ascending.

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/access-control/events` |
| **Query** | `date` (required) |

### Query parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `date` | `string` (ISO-8601 date) | Yes | Calendar day, format `YYYY-MM-DD`. Events included are those with `eventTimestamp` in `[date 00:00:00, next day 00:00:00)` in the JVM default time zone. |

### Response

**Status:** `200 OK`

**Body:** JSON array of objects:

| Field | Type | Description |
|-------|------|-------------|
| `code` | `string` | Staff code from access-control staff. |
| `name` | `string` | Staff display name. |
| `time` | `string` (ISO-8601 date-time) | Event instant (`LocalDateTime` serialized as ISO-8601, e.g. `2026-04-21T14:30:00`). |

### Example

**Request**

```http
GET /api/access-control/events?date=2026-04-21
Authorization: Bearer <jwt>
```

**Response**

```json
[
  {
    "code": "EMP001",
    "name": "Jane Doe",
    "time": "2026-04-21T08:15:00"
  },
  {
    "code": "EMP002",
    "name": "John Smith",
    "time": "2026-04-21T17:42:30"
  }
]
```

### Error behaviour

- If no store can be resolved for the current user, the controller throws at runtime (`Store context not found for current user`). Clients should ensure the JWT maps to a store like other `/api/*` resources that rely on `CurrentStoreContext`.
- Invalid or missing `date` may yield `400 Bad Request` from Spring’s parameter binding, depending on global exception handling.

---

## Implementation notes

- Data source: `access_control_events`, joined to `access_control_staff` for `code` and `name`.
- Staff rows are loaded with the events in a single query (`JOIN FETCH`) to avoid N+1 requests.
