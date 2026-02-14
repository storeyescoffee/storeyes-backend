# Documents API Documentation

## Overview

The Documents API provides endpoints for managing documents associated with stores. Documents are stored in AWS S3 and metadata is stored in the database. All endpoints require authentication via Keycloak JWT tokens.

**Base URL:** `/api/documents`

**Authentication:** All endpoints require a valid JWT token in the `Authorization` header:
```
Authorization: Bearer <jwt_token>
```

**Store Resolution:** The store is automatically resolved from the authenticated user's ID (userId → userData → store). Users can only access documents belonging to their own store.

---

## Endpoints

### 1. Get All Documents

Retrieves all documents for the current user's store.

**Endpoint:** `GET /api/documents`

**Authentication:** Required

**Request Parameters:** None

**Response:** `200 OK`

**Response Body:**
```json
[
  {
    "id": 1,
    "storeId": 5,
    "storeCode": "STORE001",
    "name": "Invoice January 2024",
    "description": "Monthly invoice for January",
    "url": "https://storeyes-documents.s3.eu-south-2.amazonaws.com/STORE001/abc123-def456-ghi789.pdf",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  {
    "id": 2,
    "storeId": 5,
    "storeCode": "STORE001",
    "name": "Contract Agreement",
    "description": "Vendor contract",
    "url": "https://storeyes-documents.s3.eu-south-2.amazonaws.com/STORE001/xyz789-abc123-def456.pdf",
    "createdAt": "2024-01-20T14:20:00",
    "updatedAt": "2024-01-20T14:20:00"
  }
]
```

**Error Responses:**
- `401 Unauthorized` - User is not authenticated
- `404 Not Found` - Store not found for current user

**Example Request:**
```bash
curl -X GET "https://api.example.com/api/documents" \
  -H "Authorization: Bearer <jwt_token>"
```

---

### 2. Create Document

Creates a new document and uploads the file to S3.

**Endpoint:** `POST /api/documents`

**Authentication:** Required

**Content-Type:** `multipart/form-data`

**Request Body (Form Data):**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Document name |
| `description` | String | No | Document description |
| `file` | File | Yes | The file to upload |

**Response:** `201 Created`

**Response Body:**
```json
{
  "id": 3,
  "storeId": 5,
  "storeCode": "STORE001",
  "name": "New Invoice",
  "description": "Invoice for February 2024",
  "url": "https://storeyes-documents.s3.eu-south-2.amazonaws.com/STORE001/uuid-generated-filename.pdf",
  "createdAt": "2024-02-01T09:15:00",
  "updatedAt": "2024-02-01T09:15:00"
}
```

**Error Responses:**
- `400 Bad Request` - Validation error (missing required fields, invalid file)
- `401 Unauthorized` - User is not authenticated
- `404 Not Found` - Store not found for current user
- `500 Internal Server Error` - S3 upload failed

**File Upload Details:**
- Files are uploaded to S3 bucket: `storeyes-documents`
- S3 path structure: `<store_code>/<uuid-generated-filename>.<extension>`
- File names are automatically generated using UUID to avoid conflicts
- Original file extension is preserved
- URL format: `https://storeyes-documents.s3.eu-south-2.amazonaws.com/<store-code>/<file-name>`

**Example Request:**
```bash
curl -X POST "https://api.example.com/api/documents" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "name=Invoice February 2024" \
  -F "description=Monthly invoice for February" \
  -F "file=@/path/to/invoice.pdf"
```

**Example Request (JavaScript/Fetch):**
```javascript
const formData = new FormData();
formData.append('name', 'Invoice February 2024');
formData.append('description', 'Monthly invoice for February');
formData.append('file', fileInput.files[0]);

fetch('https://api.example.com/api/documents', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <jwt_token>'
  },
  body: formData
})
.then(response => response.json())
.then(data => console.log(data));
```

---

### 3. Update Document

Updates an existing document. All fields are optional - only provided fields will be updated.

**Endpoint:** `PUT /api/documents/{id}`

**Authentication:** Required

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | Yes | Document ID |

**Content-Type:** `multipart/form-data`

**Request Body (Form Data):**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | No | New document name |
| `description` | String | No | New document description |
| `file` | File | No | New file to replace existing file |

**Response:** `200 OK`

**Response Body:**
```json
{
  "id": 3,
  "storeId": 5,
  "storeCode": "STORE001",
  "name": "Updated Invoice Name",
  "description": "Updated description",
  "url": "https://storeyes-documents.s3.eu-south-2.amazonaws.com/STORE001/new-uuid-generated-filename.pdf",
  "createdAt": "2024-02-01T09:15:00",
  "updatedAt": "2024-02-01T11:30:00"
}
```

**Error Responses:**
- `400 Bad Request` - Validation error
- `401 Unauthorized` - User is not authenticated
- `404 Not Found` - Document not found or does not belong to user's store
- `500 Internal Server Error` - S3 operation failed

**Notes:**
- If a new file is provided, the old file will be deleted from S3
- Only fields that are provided will be updated
- Empty strings for `name` will be ignored
- `null` values for `description` will clear the description

**Example Request (Update name only):**
```bash
curl -X PUT "https://api.example.com/api/documents/3" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "name=Updated Invoice Name"
```

**Example Request (Update file only):**
```bash
curl -X PUT "https://api.example.com/api/documents/3" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "file=@/path/to/new-invoice.pdf"
```

**Example Request (Update all fields):**
```bash
curl -X PUT "https://api.example.com/api/documents/3" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "name=Updated Invoice Name" \
  -F "description=Updated description" \
  -F "file=@/path/to/new-invoice.pdf"
```

---

### 4. Delete Document

Deletes a document and removes the associated file from S3.

**Endpoint:** `DELETE /api/documents/{id}`

**Authentication:** Required

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | Yes | Document ID |

**Response:** `204 No Content`

**Response Body:** None

**Error Responses:**
- `401 Unauthorized` - User is not authenticated
- `404 Not Found` - Document not found or does not belong to user's store
- `500 Internal Server Error` - S3 deletion failed

**Notes:**
- This operation permanently deletes both the database record and the file from S3
- The operation cannot be undone

**Example Request:**
```bash
curl -X DELETE "https://api.example.com/api/documents/3" \
  -H "Authorization: Bearer <jwt_token>"
```

---

## Data Models

### DocumentDTO

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Document ID (auto-generated) |
| `storeId` | Long | ID of the store that owns the document |
| `storeCode` | String | Code of the store that owns the document |
| `name` | String | Document name |
| `description` | String | Document description (optional) |
| `url` | String | S3 URL of the document file |
| `createdAt` | LocalDateTime | Timestamp when document was created |
| `updatedAt` | LocalDateTime | Timestamp when document was last updated |

### CreateDocumentRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Document name (not blank) |
| `description` | String | No | Document description |
| `file` | MultipartFile | Yes | File to upload (not null) |

### UpdateDocumentRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | No | New document name |
| `description` | String | No | New document description |
| `file` | MultipartFile | No | New file to replace existing file |

---

## S3 Storage Details

### Bucket Configuration
- **Bucket Name:** `storeyes-documents`
- **Region:** `eu-south-2`
- **Path Structure:** `<store_code>/<uuid-generated-filename>.<extension>`

### URL Format
```
https://storeyes-documents.s3.eu-south-2.amazonaws.com/<store-code>/<file-name>
```

### File Naming
- Files are automatically renamed using UUID to prevent conflicts
- Original file extension is preserved
- Example: `invoice.pdf` → `a1b2c3d4-e5f6-7890-abcd-ef1234567890.pdf`

---

## Security & Authorization

### Authentication
All endpoints require a valid JWT token obtained from Keycloak. The token must be included in the `Authorization` header:
```
Authorization: Bearer <jwt_token>
```

### Authorization
- Users can only access documents belonging to their own store
- Store is automatically resolved from the authenticated user's ID
- Attempting to access or modify documents from other stores will result in a `404 Not Found` or `403 Forbidden` error

### Store Resolution Flow
1. Extract `userId` from JWT token
2. Find `UserInfo` entity by `userId`
3. Find `Store` entity where `owner.id = userId`
4. Filter documents by `store.id`

---

## Error Handling

### Common Error Responses

#### 400 Bad Request
```json
{
  "timestamp": "2024-02-01T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "name",
      "message": "Document name is required"
    }
  ]
}
```

#### 401 Unauthorized
```json
{
  "timestamp": "2024-02-01T10:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "User is not authenticated"
}
```

#### 404 Not Found
```json
{
  "timestamp": "2024-02-01T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Document not found with id: 999"
}
```

#### 500 Internal Server Error
```json
{
  "timestamp": "2024-02-01T10:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to upload file to S3"
}
```

---

## Examples

### Complete Workflow Example

1. **Get all documents:**
```bash
curl -X GET "https://api.example.com/api/documents" \
  -H "Authorization: Bearer <jwt_token>"
```

2. **Create a new document:**
```bash
curl -X POST "https://api.example.com/api/documents" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "name=Monthly Report" \
  -F "description=January 2024 monthly report" \
  -F "file=@report.pdf"
```

3. **Update the document:**
```bash
curl -X PUT "https://api.example.com/api/documents/1" \
  -H "Authorization: Bearer <jwt_token>" \
  -F "name=Updated Monthly Report" \
  -F "description=Updated January 2024 monthly report"
```

4. **Delete the document:**
```bash
curl -X DELETE "https://api.example.com/api/documents/1" \
  -H "Authorization: Bearer <jwt_token>"
```

---

## Rate Limiting

Currently, there are no rate limits imposed on the Documents API. However, consider implementing rate limiting for production use.

---

## File Size Limits

- **Recommended Maximum File Size:** 10 MB
- **Absolute Maximum:** Configured at the application server level
- **Supported File Types:** All file types are supported

---

## Notes

1. **File Upload:** Files are uploaded directly to S3, not stored temporarily on the server
2. **File Deletion:** When a document is deleted, the associated S3 file is also deleted
3. **File Updates:** When updating a document with a new file, the old file is automatically deleted from S3
4. **Store Isolation:** Documents are automatically scoped to the user's store - no need to specify store ID in requests
5. **URL Encoding:** File names in S3 URLs are URL-encoded to handle special characters

---

## Support

For issues or questions regarding the Documents API, please contact the development team.