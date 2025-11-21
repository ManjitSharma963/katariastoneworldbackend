# Setup Guide

Detailed setup instructions for Kataria Stone World APIs development environment.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Application Configuration](#application-configuration)
- [Running the Application](#running-the-application)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software

1. **Java Development Kit (JDK)**
   - Version: 17 or higher
   - Download: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
   - Verify installation:
     ```bash
     java -version
     javac -version
     ```

2. **Maven**
   - Version: 3.6 or higher
   - Download: [Apache Maven](https://maven.apache.org/download.cgi)
   - Verify installation:
     ```bash
     mvn -version
     ```

3. **MySQL**
   - Version: 8.0 or higher
   - Download: [MySQL Community Server](https://dev.mysql.com/downloads/mysql/)
   - Verify installation:
     ```bash
     mysql --version
     ```

4. **Git** (Optional, for version control)
   - Download: [Git](https://git-scm.com/downloads)

### IDE Setup (Optional but Recommended)

- **IntelliJ IDEA**: [Download](https://www.jetbrains.com/idea/)
- **Eclipse**: [Download](https://www.eclipse.org/downloads/)
- **VS Code**: [Download](https://code.visualstudio.com/) with Java extensions

## Database Setup

### Step 1: Install MySQL

1. Download and install MySQL 8.0+ from the official website
2. During installation, set a root password (remember this for configuration)
3. Ensure MySQL service is running

### Step 2: Create Database

1. Open MySQL command line or MySQL Workbench
2. Connect as root user
3. Create the database:

```sql
CREATE DATABASE katariastoneworld CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

4. Verify database creation:

```sql
SHOW DATABASES;
```

### Step 3: Create Database User (Optional but Recommended)

For production or better security, create a dedicated database user:

```sql
CREATE USER 'ksw_user'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON katariastoneworld.* TO 'ksw_user'@'localhost';
FLUSH PRIVILEGES;
```

## Application Configuration

### Step 1: Clone/Download Project

```bash
git clone <repository-url>
cd katariastoneworld_apis
```

Or download and extract the project files.

### Step 2: Configure Database Connection

Edit `src/main/resources/application.properties`:

```properties
# Update these values with your MySQL credentials
spring.datasource.url=jdbc:mysql://localhost:3306/katariastoneworld?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_mysql_password
```

**For dedicated user:**
```properties
spring.datasource.username=ksw_user
spring.datasource.password=your_secure_password
```

### Step 3: Configure JWT Secret

Generate a secure JWT secret key (at least 32 characters):

```properties
jwt.secret=your-256-bit-secret-key-for-jwt-token-generation-must-be-at-least-32-characters-long-for-security
```

**Generate a secure secret:**
```bash
# On Linux/Mac
openssl rand -base64 32

# Or use an online generator
```

### Step 4: Configure Email (Optional)

For email functionality (bill delivery), configure SMTP settings:

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_app_password
```

**For Gmail:**
1. Enable 2-Factor Authentication
2. Generate an [App Password](https://support.google.com/accounts/answer/185833)
3. Use the app password (not your regular password)

**For other email providers:**
- Update `spring.mail.host` and `spring.mail.port` accordingly
- Adjust authentication settings as needed

### Step 5: Configure Server Port (Optional)

Default port is 8080. To change:

```properties
server.port=8080
```

## Running the Application

### Method 1: Using Maven

1. **Build the project:**
   ```bash
   mvn clean install
   ```

2. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

### Method 2: Using IDE

1. **IntelliJ IDEA:**
   - Open the project
   - Right-click on `KatariaStoneWorldApisApplication.java`
   - Select "Run 'KatariaStoneWorldApisApplication'"

2. **Eclipse:**
   - Import as Maven project
   - Right-click on `KatariaStoneWorldApisApplication.java`
   - Run As → Java Application

3. **VS Code:**
   - Open the project folder
   - Use the Java extension to run the application

### Method 3: Using JAR File

1. **Build JAR:**
   ```bash
   mvn clean package
   ```

2. **Run JAR:**
   ```bash
   java -jar target/katariastoneworld-apis-1.0.0.jar
   ```

### Method 4: Using Docker (If Dockerfile exists)

```bash
docker build -t kataria-apis .
docker run -p 8080:8080 kataria-apis
```

## Verification

### Step 1: Check Application Startup

Look for these messages in the console:

```
Started KatariaStoneWorldApisApplication in X.XXX seconds
```

### Step 2: Verify Database Connection

Check logs for:
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

### Step 3: Test API Endpoints

1. **Access Swagger UI:**
   - Open your browser and navigate to: `http://localhost:8080/swagger-ui.html`
   - You should see the interactive API documentation
   - This confirms the application is running correctly

2. **Health Check (if available):**
   ```bash
   curl http://localhost:8080/api/health
   ```

3. **Register a test user:**
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

4. **Login:**
   ```bash
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "username": "testuser",
       "password": "password123"
     }'
   ```

5. **Get Products (public endpoint):**
   ```bash
   curl http://localhost:8080/api/inventory
   ```

### Step 4: Verify Database Tables

Connect to MySQL and check tables:

```sql
USE katariastoneworld;
SHOW TABLES;
```

You should see tables like:
- `users`
- `products`
- `categories`
- `customers`
- `employees`
- `expenses`
- `bill_gst`
- `bill_non_gst`
- etc.

## Troubleshooting

### Common Issues

#### 1. Database Connection Error

**Error:** `Communications link failure` or `Access denied`

**Solutions:**
- Verify MySQL is running: `mysql -u root -p`
- Check database credentials in `application.properties`
- Ensure database `katariastoneworld` exists
- Check MySQL port (default: 3306)
- Verify user has proper permissions

#### 2. Port Already in Use

**Error:** `Port 8080 is already in use`

**Solutions:**
- Change port in `application.properties`: `server.port=8081`
- Or stop the process using port 8080:
  ```bash
  # Windows
  netstat -ano | findstr :8080
  taskkill /PID <PID> /F
  
  # Linux/Mac
  lsof -ti:8080 | xargs kill
  ```

#### 3. JWT Token Issues

**Error:** `Invalid token` or `Token expired`

**Solutions:**
- Verify JWT secret is set in `application.properties`
- Check token expiration time: `jwt.expiration=600000` (10 minutes)
- Ensure token is included in Authorization header: `Bearer <token>`

#### 4. Hibernate Schema Issues

**Error:** `Table doesn't exist` or schema errors

**Solutions:**
- Check `spring.jpa.hibernate.ddl-auto=update` is set
- Verify database connection is working
- Check Hibernate logs: `spring.jpa.show-sql=true` (temporarily)
- Manually run migration scripts if needed (see `README_DATABASE_MIGRATION.md`)

#### 5. Email Configuration Issues

**Error:** `Mail server connection failed`

**Solutions:**
- Verify SMTP settings are correct
- For Gmail, use App Password (not regular password)
- Check firewall/network settings
- Test with a simple email first using `/api/bills/test-email` endpoint

#### 6. Maven Build Issues

**Error:** `Could not resolve dependencies`

**Solutions:**
- Check internet connection
- Clear Maven cache: `mvn clean`
- Update Maven: `mvn -U clean install`
- Check proxy settings if behind corporate firewall

#### 7. Java Version Issues

**Error:** `Unsupported class file major version`

**Solutions:**
- Verify Java version: `java -version` (should be 17+)
- Set JAVA_HOME environment variable
- Update IDE Java version settings

### Getting Help

1. Check application logs in console output
2. Review `application.properties` configuration
3. Check database connection and permissions
4. Review error messages carefully
5. Consult project documentation
6. Check existing GitHub issues
7. Contact the development team

## Next Steps

After successful setup:

1. **Access Swagger UI** at `http://localhost:8080/swagger-ui.html` to explore the API interactively
2. **Read the README.md** for project overview
3. **Review API_DOCUMENTATION.md** for detailed API reference
4. **Create your first user** via registration endpoint (use Swagger UI or curl)
5. **Explore the API** using Swagger UI, Postman, or curl
6. **Review CONTRIBUTING.md** if you want to contribute

### Using Swagger UI

Swagger UI is the recommended way to interact with the API:

1. Open `http://localhost:8080/swagger-ui.html` in your browser
   - **Note:** Swagger UI is publicly accessible - no authentication needed to view documentation
2. **To test protected endpoints** (optional):
   - Use the `/api/auth/register` or `/api/auth/login` endpoint to get a JWT token
   - Copy the JWT token from the response
   - Click the "Authorize" button (🔒) at the top right
   - Enter: `Bearer <your_jwt_token>`
   - Click "Authorize" and "Close"
   - Now you can test protected endpoints directly from Swagger UI
3. Explore and test endpoints using the interactive interface

## Development Tips

1. **Enable SQL Logging** (for debugging):
   ```properties
   spring.jpa.show-sql=true
   spring.jpa.properties.hibernate.format_sql=true
   ```

2. **Use Spring Boot DevTools** for hot reloading (already included)

3. **Configure Logging Levels:**
   ```properties
   logging.level.com.katariastoneworld.apis=DEBUG
   logging.level.org.springframework.web=INFO
   ```

4. **Database Management:**
   - Use MySQL Workbench for visual database management
   - Use phpMyAdmin as an alternative
   - Keep backups of your database

5. **API Testing:**
   - Use Postman for API testing
   - Import API collection if available
   - Set up environment variables for base URL and tokens

---

**Happy Coding!** 🚀

