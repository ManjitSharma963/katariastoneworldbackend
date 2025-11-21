# Kataria Stone World APIs

A comprehensive Spring Boot REST API application for managing bills, inventory, customers, employees, and expenses for Kataria Stone World. The application supports location-based data isolation, JWT authentication, PDF bill generation, and email notifications.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Authentication](#authentication)
- [Database Schema](#database-schema)
- [Location-Based Data Isolation](#location-based-data-isolation)
- [Testing](#testing)
- [Deployment](#deployment)
- [Contributing](#contributing)
- [Additional Documentation](#additional-documentation)

## Features

- **Authentication & Authorization**
  - JWT-based authentication
  - Role-based access control (Admin, User)
  - User registration and login
  - Token validation and refresh

- **Bill Management**
  - Create GST and Non-GST bills
  - Automatic bill number generation
  - PDF generation for bills
  - Email bill delivery
  - Bill search by customer mobile number
  - Payment status tracking

- **Inventory Management**
  - Product CRUD operations
  - Category management
  - Location-based product filtering
  - Product search and filtering

- **Customer Management**
  - Customer registration and management
  - Customer search by phone number
  - Location-based customer data

- **Employee Management**
  - Employee CRUD operations
  - Location-based employee management
  - Employee details tracking

- **Expense Management**
  - Expense tracking and management
  - Location-based expense filtering
  - Expense settlement tracking

- **Hero Section Management**
  - Dynamic hero content management
  - Active/inactive hero toggling

- **Location-Based Data Isolation**
  - Multi-location support
  - Automatic location extraction from JWT tokens
  - Location-scoped data access

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Java Version**: 17
- **Build Tool**: Maven
- **Database**: MySQL 8.0+
- **ORM**: Spring Data JPA / Hibernate
- **Security**: JWT (JSON Web Tokens), BCrypt
- **PDF Generation**: OpenHTMLToPDF
- **Email**: Spring Mail (SMTP)
- **Template Engine**: Thymeleaf
- **Validation**: Jakarta Validation
- **Utilities**: Lombok

## Prerequisites

Before running this application, ensure you have the following installed:

- **Java Development Kit (JDK)**: Version 17 or higher
- **Maven**: Version 3.6+ (for building the project)
- **MySQL**: Version 8.0 or higher
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code (optional)

## Installation

> **For detailed setup instructions, troubleshooting, and step-by-step guide, see [SETUP.md](SETUP.md)**

### 1. Clone the Repository

```bash
git clone <repository-url>
cd katariastoneworld_apis
```

### 2. Database Setup

Create a MySQL database:

```sql
CREATE DATABASE katariastoneworld;
```

### 3. Configure Application Properties

Update `src/main/resources/application.properties` with your database credentials and other configurations:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/katariastoneworld?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=your_username
spring.datasource.password=your_password

# JWT Configuration
jwt.secret=your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-characters-long-for-security
jwt.expiration=600000

# Email Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_app_password
```

**Important**: 
- For Gmail, use an [App Password](https://support.google.com/accounts/answer/185833) instead of your regular password
- Change the JWT secret to a secure random string (at least 32 characters)

### 4. Build the Project

```bash
mvn clean install
```

### 5. Run Database Migrations

If you have existing data, refer to `README_DATABASE_MIGRATION.md` for migration instructions.

## Configuration

### Application Properties

Key configuration options in `application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `server.port` | Server port | 8080 |
| `spring.jpa.hibernate.ddl-auto` | Database schema update mode | update |
| `spring.jpa.show-sql` | Show SQL queries in logs | false |
| `jwt.secret` | JWT secret key | (must be configured) |
| `jwt.expiration` | JWT token expiration (ms) | 600000 (10 minutes) |
| `spring.datasource.hikari.maximum-pool-size` | Connection pool size | 20 |

### Environment Variables

For production, consider using environment variables or Spring profiles:

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/katariastoneworld
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=secret
export JWT_SECRET=your-secret-key
```

## Running the Application

### Using Maven

```bash
mvn spring-boot:run
```

### Using Java

```bash
java -jar target/katariastoneworld-apis-1.0.0.jar
```

### Using IDE

Run the `KatariaStoneWorldApisApplication` class directly from your IDE.

The application will start on `http://localhost:8080`

## API Documentation

### Swagger UI

The API documentation is available through Swagger UI, which provides an interactive interface to explore and test all endpoints.

**Access Swagger UI:**
```
http://localhost:8080/swagger-ui.html
```

**OpenAPI JSON:**
```
http://localhost:8080/api-docs
```

**Features:**
- **Public Access**: Swagger UI is publicly accessible - no authentication required to view the documentation
- Interactive API testing
- Request/response examples
- Optional JWT authentication for testing protected endpoints (use the "Authorize" button)
- Schema documentation
- Try it out functionality

**Note:** While Swagger UI itself is public, individual API endpoints may still require authentication. Use the "Authorize" button in Swagger UI to add your JWT token for testing protected endpoints.

### Base URL

```
http://localhost:8080/api
```

### Authentication Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/register` | Register a new user | No |
| POST | `/api/auth/login` | Login and get JWT token | No |
| GET | `/api/auth/me` | Get current user details | Yes |

### Bill Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/bills` | Create a new bill | Yes | user, admin |
| GET | `/api/bills` | Get all bills | Yes | admin |
| GET | `/api/bills/{id}` | Get bill by ID | Yes | admin |
| GET | `/api/bills/{billType}/{id}` | Get bill by type and ID | Yes | admin |
| GET | `/api/bills/number/{billNumber}` | Get bill by bill number | Yes | admin |
| GET | `/api/bills/sales` | Get all sales | Yes | admin |
| GET | `/api/bills/customer/{mobileNumber}` | Get bills by customer mobile | Yes | admin |
| GET | `/api/bills/{billType}/{id}/download` | Download bill PDF | Yes | admin |
| GET | `/api/bills/number/{billNumber}/download` | Download bill PDF by number | Yes | admin |

### Product/Inventory Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/inventory` | Create a product | Yes | admin |
| GET | `/api/inventory` | Get all products | No | - |
| GET | `/api/inventory/{id}` | Get product by ID | No | - |
| PUT | `/api/inventory/{id}` | Update product | Yes | admin |
| DELETE | `/api/inventory/{id}` | Delete product | Yes | admin |

### Customer Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/customers` | Create a customer | Yes | user, admin |
| GET | `/api/customers` | Get all customers | Yes | admin |
| GET | `/api/customers/{id}` | Get customer by ID | Yes | admin |
| GET | `/api/customers/phone/{phone}` | Get customer by phone | Yes | admin |

### Employee Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/employees` | Create an employee | Yes | admin |
| GET | `/api/employees` | Get all employees | Yes | admin |
| GET | `/api/employees/{id}` | Get employee by ID | Yes | admin |
| PUT | `/api/employees/{id}` | Update employee | Yes | admin |
| DELETE | `/api/employees/{id}` | Delete employee | Yes | admin |

### Expense Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/expenses` | Create an expense | Yes | admin |
| GET | `/api/expenses` | Get all expenses | Yes | admin |
| GET | `/api/expenses/{id}` | Get expense by ID | Yes | admin |
| PUT | `/api/expenses/{id}` | Update expense | Yes | admin |
| DELETE | `/api/expenses/{id}` | Delete expense | Yes | admin |

### Category Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/categories` | Create a category | Yes | admin |
| GET | `/api/categories` | Get all categories | No | - |
| GET | `/api/categories?category_type={type}` | Get categories by type | No | - |
| GET | `/api/categories/{id}` | Get category by ID | No | - |

### Hero Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| POST | `/api/heroes` | Create a hero | Yes | admin |
| GET | `/api/heroes` | Get all heroes | No | - |
| GET | `/api/heroes/active` | Get active heroes | No | - |
| GET | `/api/heroes/{id}` | Get hero by ID | No | - |
| PUT | `/api/heroes/{id}` | Update hero | Yes | admin |

For detailed API documentation with request/response examples, see [API_DOCUMENTATION.md](API_DOCUMENTATION.md).

## Additional Documentation

This project includes comprehensive documentation:

- **[API_DOCUMENTATION.md](API_DOCUMENTATION.md)** - Complete API reference with request/response examples for all endpoints
- **[SETUP.md](SETUP.md)** - Detailed setup instructions and troubleshooting guide
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Guidelines for contributing to the project
- **[README_DATABASE_MIGRATION.md](README_DATABASE_MIGRATION.md)** - Database migration guide for adding location columns

### Quick Links

- **New to the project?** Start with [SETUP.md](SETUP.md) for installation and configuration
- **Want to use the API?** Check [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for endpoint details
- **Want to contribute?** Read [CONTRIBUTING.md](CONTRIBUTING.md) for coding standards and workflow
- **Need to migrate database?** Follow [README_DATABASE_MIGRATION.md](README_DATABASE_MIGRATION.md)

## Project Structure

```
katariastoneworld_apis/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── katariastoneworld/
│   │   │           └── apis/
│   │   │               ├── config/          # Configuration classes
│   │   │               │   ├── CorsConfig.java
│   │   │               │   ├── JwtAuthenticationFilter.java
│   │   │               │   ├── RoleAuthorizationFilter.java
│   │   │               │   └── WebConfig.java
│   │   │               ├── controller/       # REST controllers
│   │   │               │   ├── AuthController.java
│   │   │               │   ├── BillController.java
│   │   │               │   ├── CategoryController.java
│   │   │               │   ├── CustomerController.java
│   │   │               │   ├── EmployeeController.java
│   │   │               │   ├── ExpenseController.java
│   │   │               │   ├── HeroController.java
│   │   │               │   └── ProductController.java
│   │   │               ├── dto/              # Data Transfer Objects
│   │   │               ├── entity/           # JPA entities
│   │   │               ├── exception/         # Exception handlers
│   │   │               ├── repository/        # JPA repositories
│   │   │               ├── service/          # Business logic
│   │   │               └── util/              # Utility classes
│   │   └── resources/
│   │       ├── application.properties
│   │       └── templates/                    # Thymeleaf templates
│   │           ├── bill-template.html
│   │           └── simple-bill-template.html
│   └── test/                                 # Test files
├── pom.xml                                   # Maven configuration
├── README.md                                 # This file
├── API_DOCUMENTATION.md                      # Detailed API docs
└── README_DATABASE_MIGRATION.md              # Database migration guide
```

## Authentication

### Registration

```bash
POST /api/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "securePassword123",
  "location": "Bhondsi",
  "role": "user"
}
```

### Login

```bash
POST /api/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "securePassword123"
}
```

**Response:**
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

### Using JWT Token

Include the JWT token in the Authorization header for protected endpoints:

```bash
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Database Schema

The application uses the following main entities:

- **User**: User accounts with authentication
- **BillGST / BillNonGST**: Bills with and without GST
- **BillItemGST / BillItemNonGST**: Bill line items
- **Product**: Inventory items
- **Category**: Product categories
- **Customer**: Customer information
- **Employee**: Employee records
- **Expense**: Expense tracking
- **Hero**: Hero section content
- **Seller**: Seller information

All entities support location-based data isolation (except User and Category).

## Location-Based Data Isolation

The application implements location-based data isolation to support multiple business locations:

- **Automatic Location Extraction**: Location is extracted from JWT token claims
- **Data Filtering**: All queries automatically filter by user's location
- **Location Scoping**: Users can only access data from their assigned location
- **Supported Entities**: Bills, Products, Customers, Employees, Expenses

### Location in JWT Token

The JWT token includes the user's location in the claims. The application automatically extracts this location and uses it for all data operations.

## Testing

### Using cURL

**Register a user:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "location": "Bhondsi",
    "role": "user"
  }'
```

**Login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

**Get products (with token):**
```bash
curl -X GET http://localhost:8080/api/inventory \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Using Postman

1. Import the API collection (if available)
2. Set up environment variables for `base_url` and `token`
3. Use the "Login" request to get a token
4. Set the token in the Authorization header for subsequent requests

## Deployment

### Production Considerations

1. **Security**
   - Change default JWT secret to a strong random string
   - Use environment variables for sensitive configuration
   - Enable HTTPS
   - Configure proper CORS settings

2. **Database**
   - Use connection pooling (already configured with HikariCP)
   - Set up database backups
   - Consider read replicas for scaling

3. **Application**
   - Set `spring.jpa.hibernate.ddl-auto=validate` in production
   - Configure proper logging levels
   - Set up monitoring and health checks

4. **Email**
   - Use a production SMTP server
   - Configure email templates properly
   - Set up email delivery monitoring

### Docker Deployment (Optional)

Create a `Dockerfile`:

```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/katariastoneworld-apis-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t kataria-apis .
docker run -p 8080:8080 kataria-apis
```

## Contributing

We welcome contributions! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) guide for:

- Code of conduct
- Development workflow
- Coding standards
- Commit guidelines
- Pull request process
- Testing requirements

Quick start:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes following our commit guidelines
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

For detailed information, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

This project is proprietary software for Kataria Stone World.

## Support

For issues, questions, or contributions, please contact the development team.

---

**Version**: 1.0.0  
**Last Updated**: 2024

