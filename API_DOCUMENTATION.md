# API Documentation

Complete API reference for Kataria Stone World APIs.

## Swagger UI

**Interactive API Documentation:** The easiest way to explore and test the API is through Swagger UI.

### Access Swagger UI

Once the application is running, access Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

**Important:** Swagger UI is **publicly accessible** - no authentication is required to view the API documentation.

### Features

- **Public Access**: No login required to view documentation
- **Interactive Testing**: Test all endpoints directly from the browser
- **Optional Authentication**: Enter your JWT token to test protected endpoints (use the "Authorize" button)
- **Request/Response Examples**: See example requests and responses for each endpoint
- **Schema Documentation**: View detailed data models and DTOs
- **Try It Out**: Execute API calls and see real responses

### Using Swagger UI

1. **Start the application** (see [SETUP.md](SETUP.md) for instructions)
2. **Open Swagger UI** in your browser: `http://localhost:8080/swagger-ui.html`
   - No authentication needed to view the documentation
3. **For Testing Protected Endpoints** (optional):
   - Use the `/api/auth/login` endpoint to get a JWT token
   - Click the "Authorize" button (🔒) at the top right
   - Enter: `Bearer <your_jwt_token>`
   - Click "Authorize" and "Close"
   - Now you can test protected endpoints
4. **Explore endpoints** by expanding the tags
5. **Test endpoints** using the "Try it out" button

### OpenAPI JSON

The OpenAPI specification is available at:
```
http://localhost:8080/api-docs
```

This can be imported into tools like Postman, Insomnia, or other API clients.

---

## Base URL

```
http://localhost:8080/api
```

## Authentication

All protected endpoints require a JWT token in the Authorization header:

```
Authorization: Bearer <your_jwt_token>
```

---

## Authentication Endpoints

### Register User

Register a new user account.

**Endpoint:** `POST /api/auth/register`

**Authentication:** Not required

**Request Body:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "securePassword123",
  "location": "Bhondsi",
  "role": "user"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "role": "user",
  "location": "Bhondsi"
}
```

**Error Responses:**
- `400 Bad Request` - Validation failed
- `500 Internal Server Error` - Server error

---

### Login

Authenticate and receive JWT token.

**Endpoint:** `POST /api/auth/login`

**Authentication:** Not required

**Request Body:**
```json
{
  "username": "john_doe",
  "password": "securePassword123"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "role": "user",
    "location": "Bhondsi"
  }
}
```

**Error Responses:**
- `401 Unauthorized` - Invalid credentials
- `500 Internal Server Error` - Server error

---

### Get Current User

Get details of the currently authenticated user.

**Endpoint:** `GET /api/auth/me`

**Authentication:** Required

**Response:** `200 OK`
```json
{
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "role": "user",
  "location": "Bhondsi"
}
```

**Error Responses:**
- `401 Unauthorized` - Missing or invalid token
  ```json
  {
    "error": "Unauthorized",
    "message": "Token expired",
    "tokenExpired": true,
    "code": "TOKEN_EXPIRED"
  }
  ```

---

## Bill Endpoints

### Create Bill

Create a new bill (GST or Non-GST).

**Endpoint:** `POST /api/bills`

**Authentication:** Required (user, admin)

**Request Body:**
```json
{
  "customerMobileNumber": "9876543210",
  "items": [
    {
      "itemName": "Absolute Black Granite",
      "category": "Granite",
      "pricePerUnit": 180.0,
      "quantity": 1
    },
    {
      "itemName": "White Marble",
      "category": "Marble",
      "pricePerUnit": 250.0,
      "quantity": 2
    }
  ],
  "taxPercentage": 10.0,
  "discountAmount": 40.0,
  "totalAmount": 158.0,
  "paymentStatus": "PAID",
  "simpleBill": false
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "billNumber": "BILL-2024-001",
  "billType": "gst",
  "customer": {
    "id": 1,
    "name": "John Doe",
    "mobileNumber": "9876543210",
    "email": "john@example.com"
  },
  "items": [
    {
      "itemName": "Absolute Black Granite",
      "category": "Granite",
      "pricePerUnit": 180.0,
      "quantity": 1,
      "totalPrice": 180.0
    }
  ],
  "subtotal": 180.0,
  "taxPercentage": 10.0,
  "taxAmount": 18.0,
  "discountAmount": 40.0,
  "totalAmount": 158.0,
  "paymentStatus": "PAID",
  "createdAt": "2024-01-15T10:30:00",
  "location": "Bhondsi"
}
```

**Note:** Location is automatically extracted from JWT token.

---

### Get All Bills

Get all bills for the authenticated user's location.

**Endpoint:** `GET /api/bills`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "billNumber": "BILL-2024-001",
    "billType": "gst",
    "customer": {
      "id": 1,
      "name": "John Doe",
      "mobileNumber": "9876543210"
    },
    "totalAmount": 158.0,
    "paymentStatus": "PAID",
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

---

### Get Bill by ID

Get a specific bill by ID.

**Endpoint:** `GET /api/bills/{id}`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
{
  "id": 1,
  "billNumber": "BILL-2024-001",
  "billType": "gst",
  "customer": {
    "id": 1,
    "name": "John Doe",
    "mobileNumber": "9876543210"
  },
  "items": [...],
  "totalAmount": 158.0,
  "paymentStatus": "PAID"
}
```

**Error Responses:**
- `404 Not Found` - Bill not found

---

### Get Bill by Type and ID

Get a bill by its type (gst/nongst) and ID.

**Endpoint:** `GET /api/bills/{billType}/{id}`

**Parameters:**
- `billType`: `gst` or `nongst`
- `id`: Bill ID

**Authentication:** Required (admin)

**Response:** `200 OK` (Same as Get Bill by ID)

---

### Get Bill by Bill Number

Get a bill by its bill number.

**Endpoint:** `GET /api/bills/number/{billNumber}`

**Authentication:** Required (admin)

**Response:** `200 OK` (Same as Get Bill by ID)

---

### Get All Sales

Get all sales (bills) for the authenticated user's location.

**Endpoint:** `GET /api/bills/sales`

**Authentication:** Required (admin)

**Response:** `200 OK` (Same format as Get All Bills)

---

### Get Bills by Customer Mobile Number

Get all bills for a specific customer by mobile number.

**Endpoint:** `GET /api/bills/customer/{mobileNumber}`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "billNumber": "BILL-2024-001",
    "customer": {
      "mobileNumber": "9876543210"
    },
    "totalAmount": 158.0
  }
]
```

---

### Download Bill PDF

Download bill PDF by bill number.

**Endpoint:** `GET /api/bills/number/{billNumber}/download`

**Authentication:** Required (admin)

**Response:** `200 OK`
- Content-Type: `application/pdf`
- Content-Disposition: `attachment; filename="Bill_BILL-2024-001.pdf"`

---

### Download Bill PDF by Type and ID

Download bill PDF by type and ID.

**Endpoint:** `GET /api/bills/{billType}/{id}/download`

**Parameters:**
- `billType`: `gst` or `nongst`
- `id`: Bill ID

**Authentication:** Required (admin)

**Response:** `200 OK` (PDF file)

---

### Send Test Email

Send a test email (for testing email configuration).

**Endpoint:** `POST /api/bills/test-email?email=recipient@example.com`

**Query Parameters:**
- `email` (optional): Email address (default: configured email)

**Authentication:** Required (admin)

**Response:** `200 OK`
```
Test email sent successfully to: recipient@example.com
```

---

## Product/Inventory Endpoints

### Create Product

Create a new product.

**Endpoint:** `POST /api/inventory`

**Authentication:** Required (admin)

**Request Body:**
```json
{
  "name": "Absolute Black Granite",
  "slug": "absolute-black-granite",
  "description": "Premium black granite with fine texture",
  "price": 180.0,
  "categoryId": 1,
  "metaTitle": "Absolute Black Granite",
  "metaDescription": "Premium black granite",
  "metaKeywords": "granite, black, premium"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "name": "Absolute Black Granite",
  "slug": "absolute-black-granite",
  "description": "Premium black granite with fine texture",
  "price": 180.0,
  "category": {
    "id": 1,
    "name": "Granite"
  },
  "location": "Bhondsi",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### Get All Products

Get all products. If authenticated, returns products for user's location. Otherwise, returns all products.

**Endpoint:** `GET /api/inventory`

**Query Parameters:**
- `location` (optional): Filter by location

**Authentication:** Not required

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "name": "Absolute Black Granite",
    "slug": "absolute-black-granite",
    "price": 180.0,
    "category": {
      "id": 1,
      "name": "Granite"
    },
    "location": "Bhondsi"
  }
]
```

---

### Get Product by ID

Get a specific product by ID.

**Endpoint:** `GET /api/inventory/{id}`

**Query Parameters:**
- `location` (optional): Filter by location (required if not authenticated)

**Authentication:** Not required (but location required if not authenticated)

**Response:** `200 OK`
```json
{
  "id": 1,
  "name": "Absolute Black Granite",
  "slug": "absolute-black-granite",
  "description": "Premium black granite with fine texture",
  "price": 180.0,
  "category": {
    "id": 1,
    "name": "Granite"
  },
  "location": "Bhondsi"
}
```

---

### Get Product by Slug

Get a product by its slug.

**Endpoint:** `GET /api/inventory/slug/{slug}`

**Query Parameters:**
- `location` (optional): Filter by location

**Authentication:** Not required

**Response:** `200 OK` (Same format as Get Product by ID)

---

### Update Product

Update an existing product.

**Endpoint:** `PUT /api/inventory/{id}`

**Authentication:** Required (admin)

**Request Body:** (Same as Create Product)

**Response:** `200 OK` (Same format as Create Product)

**Error Responses:**
- `404 Not Found` - Product not found

---

### Delete Product

Delete a product.

**Endpoint:** `DELETE /api/inventory/{id}`

**Authentication:** Required (admin)

**Response:** `204 No Content`

**Error Responses:**
- `404 Not Found` - Product not found

---

## Customer Endpoints

### Create Customer

Create a new customer.

**Endpoint:** `POST /api/customers`

**Authentication:** Required (user, admin)

**Request Body:**
```json
{
  "name": "John Doe",
  "mobileNumber": "9876543210",
  "email": "john@example.com",
  "address": "123 Main Street"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "name": "John Doe",
  "mobileNumber": "9876543210",
  "email": "john@example.com",
  "address": "123 Main Street",
  "location": "Bhondsi",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### Get All Customers

Get all customers for the authenticated user's location.

**Endpoint:** `GET /api/customers`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "name": "John Doe",
    "mobileNumber": "9876543210",
    "email": "john@example.com",
    "location": "Bhondsi"
  }
]
```

---

### Get Customer by ID

Get a specific customer by ID.

**Endpoint:** `GET /api/customers/{id}`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
{
  "id": 1,
  "name": "John Doe",
  "mobileNumber": "9876543210",
  "email": "john@example.com",
  "address": "123 Main Street",
  "location": "Bhondsi"
}
```

---

### Get Customer by Phone

Get a customer by phone number.

**Endpoint:** `GET /api/customers/phone/{phone}`

**Authentication:** Required (admin)

**Response:** `200 OK` (Same format as Get Customer by ID)

---

### Update Customer

Update an existing customer.

**Endpoint:** `PUT /api/customers/{id}`

**Authentication:** Required (admin)

**Request Body:** (Same as Create Customer)

**Response:** `200 OK` (Same format as Create Customer)

---

### Delete Customer

Delete a customer.

**Endpoint:** `DELETE /api/customers/{id}`

**Authentication:** Required (admin)

**Response:** `204 No Content`

---

## Employee Endpoints

### Create Employee

Create a new employee.

**Endpoint:** `POST /api/employees`

**Authentication:** Required (admin)

**Request Body:**
```json
{
  "name": "Jane Smith",
  "email": "jane@example.com",
  "phone": "9876543211",
  "position": "Sales Manager",
  "joiningDate": "2024-01-01",
  "salary": 50000.0
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "name": "Jane Smith",
  "email": "jane@example.com",
  "phone": "9876543211",
  "position": "Sales Manager",
  "joiningDate": "2024-01-01",
  "salary": 50000.0,
  "location": "Bhondsi",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### Get All Employees

Get all employees for the authenticated user's location.

**Endpoint:** `GET /api/employees`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "name": "Jane Smith",
    "email": "jane@example.com",
    "position": "Sales Manager",
    "location": "Bhondsi"
  }
]
```

---

### Get Employee by ID

Get a specific employee by ID.

**Endpoint:** `GET /api/employees/{id}`

**Authentication:** Required (admin)

**Response:** `200 OK` (Same format as Create Employee)

**Error Responses:**
- `404 Not Found` - Employee not found

---

### Update Employee

Update an existing employee.

**Endpoint:** `PUT /api/employees/{id}`

**Authentication:** Required (admin)

**Request Body:** (Same as Create Employee)

**Response:** `200 OK` (Same format as Create Employee)

**Error Responses:**
- `404 Not Found` - Employee not found

---

### Delete Employee

Delete an employee.

**Endpoint:** `DELETE /api/employees/{id}`

**Authentication:** Required (admin)

**Response:** `204 No Content`

**Error Responses:**
- `404 Not Found` - Employee not found

---

## Expense Endpoints

### Create Expense

Create a new expense record.

**Endpoint:** `POST /api/expenses`

**Authentication:** Required (admin)

**Request Body:**
```json
{
  "description": "Office Supplies",
  "amount": 5000.0,
  "expenseDate": "2024-01-15",
  "category": "Office",
  "settled": false
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "description": "Office Supplies",
  "amount": 5000.0,
  "expenseDate": "2024-01-15",
  "category": "Office",
  "settled": false,
  "location": "Bhondsi",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### Get All Expenses

Get all expenses for the authenticated user's location.

**Endpoint:** `GET /api/expenses`

**Authentication:** Required (admin)

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "description": "Office Supplies",
    "amount": 5000.0,
    "expenseDate": "2024-01-15",
    "category": "Office",
    "settled": false,
    "location": "Bhondsi"
  }
]
```

---

### Get Expense by ID

Get a specific expense by ID.

**Endpoint:** `GET /api/expenses/{id}`

**Authentication:** Required (admin)

**Response:** `200 OK` (Same format as Create Expense)

**Error Responses:**
- `404 Not Found` - Expense not found

---

### Update Expense

Update an existing expense.

**Endpoint:** `PUT /api/expenses/{id}`

**Authentication:** Required (admin)

**Request Body:** (Same as Create Expense)

**Response:** `200 OK` (Same format as Create Expense)

**Error Responses:**
- `404 Not Found` - Expense not found

---

### Delete Expense

Delete an expense.

**Endpoint:** `DELETE /api/expenses/{id}`

**Authentication:** Required (admin)

**Response:** `204 No Content`

**Error Responses:**
- `404 Not Found` - Expense not found

---

## Category Endpoints

### Create Category

Create a new category.

**Endpoint:** `POST /api/categories`

**Authentication:** Required (admin)

**Request Body:**
```json
{
  "name": "Granite",
  "slug": "granite",
  "description": "Granite stone category",
  "categoryType": "product"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "name": "Granite",
  "slug": "granite",
  "description": "Granite stone category",
  "categoryType": "product",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### Get All Categories

Get all categories, optionally filtered by type.

**Endpoint:** `GET /api/categories`

**Query Parameters:**
- `category_type` (optional): Filter by category type (e.g., "product")

**Authentication:** Not required

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "name": "Granite",
    "slug": "granite",
    "description": "Granite stone category",
    "categoryType": "product"
  }
]
```

---

### Get Category by ID

Get a specific category by ID.

**Endpoint:** `GET /api/categories/{id}`

**Authentication:** Not required

**Response:** `200 OK`
```json
{
  "id": 1,
  "name": "Granite",
  "slug": "granite",
  "description": "Granite stone category",
  "categoryType": "product"
}
```

---

## Hero Endpoints

### Create Hero

Create a new hero section content.

**Endpoint:** `POST /api/heroes`

**Authentication:** Required (admin)

**Request Body:**
```json
{
  "title": "Welcome to Kataria Stone World",
  "subtitle": "Premium Natural Stones",
  "description": "Discover our wide range of premium natural stones",
  "imageUrl": "https://example.com/hero-image.jpg",
  "buttonText": "Shop Now",
  "buttonLink": "/products",
  "active": true
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "title": "Welcome to Kataria Stone World",
  "subtitle": "Premium Natural Stones",
  "description": "Discover our wide range of premium natural stones",
  "imageUrl": "https://example.com/hero-image.jpg",
  "buttonText": "Shop Now",
  "buttonLink": "/products",
  "active": true,
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### Get All Heroes

Get all hero sections.

**Endpoint:** `GET /api/heroes`

**Authentication:** Not required

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Welcome to Kataria Stone World",
    "subtitle": "Premium Natural Stones",
    "active": true
  }
]
```

---

### Get Active Heroes

Get only active hero sections.

**Endpoint:** `GET /api/heroes/active`

**Authentication:** Not required

**Response:** `200 OK` (Same format as Get All Heroes, but only active ones)

---

### Get Hero by ID

Get a specific hero by ID.

**Endpoint:** `GET /api/heroes/{id}`

**Authentication:** Not required

**Response:** `200 OK` (Same format as Create Hero)

---

### Update Hero

Update an existing hero.

**Endpoint:** `PUT /api/heroes/{id}`

**Authentication:** Required (admin)

**Request Body:** (Same as Create Hero)

**Response:** `200 OK` (Same format as Create Hero)

**Error Responses:**
- `404 Not Found` - Hero not found

---

### Delete Hero

Delete a hero.

**Endpoint:** `DELETE /api/heroes/{id}`

**Authentication:** Required (admin)

**Response:** `204 No Content`

**Error Responses:**
- `404 Not Found` - Hero not found

---

## Error Responses

All endpoints may return the following error responses:

### 400 Bad Request
```json
{
  "error": "Validation failed",
  "message": "Field validation error details"
}
```

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token",
  "tokenExpired": false,
  "code": "INVALID_TOKEN"
}
```

### 403 Forbidden
```json
{
  "error": "Forbidden",
  "message": "Insufficient permissions"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Resource not found"
}
```

### 500 Internal Server Error
```json
{
  "error": "Error",
  "message": "Internal server error details"
}
```

---

## Common Request/Response Patterns

### Pagination

Currently, endpoints return all results. Pagination may be added in future versions.

### Filtering

Many endpoints support location-based filtering automatically through JWT tokens. Some endpoints also support query parameters for filtering.

### Date Formats

All dates are in ISO 8601 format: `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm:ss`

### Currency

All monetary values are in decimal format (e.g., `180.0` for ₹180.00).

---

## Rate Limiting

Currently, there are no rate limits. Consider implementing rate limiting for production use.

---

## Versioning

Current API version: **1.0.0**

API versioning may be implemented in future releases.

---

## Support

For API support, please contact the development team.

**Last Updated:** 2024

