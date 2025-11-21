# Contributing to Kataria Stone World APIs

Thank you for your interest in contributing to Kataria Stone World APIs! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Testing](#testing)
- [Documentation](#documentation)

## Code of Conduct

- Be respectful and considerate of others
- Welcome newcomers and help them get started
- Focus on constructive feedback
- Respect different viewpoints and experiences

## Getting Started

1. **Fork the Repository**
   ```bash
   git clone https://github.com/your-username/katariastoneworld_apis.git
   cd katariastoneworld_apis
   ```

2. **Set Up Development Environment**
   - Ensure you have JDK 17+ installed
   - Install Maven 3.6+
   - Set up MySQL 8.0+
   - Configure your IDE (IntelliJ IDEA, Eclipse, or VS Code)

3. **Configure Application**
   - Copy `application.properties` and update with your local database credentials
   - Set up your JWT secret key
   - Configure email settings (optional for local development)

4. **Build the Project**
   ```bash
   mvn clean install
   ```

## Development Workflow

### Branch Naming

- `feature/feature-name` - For new features
- `bugfix/bug-description` - For bug fixes
- `hotfix/urgent-fix` - For urgent production fixes
- `refactor/refactor-description` - For code refactoring
- `docs/documentation-update` - For documentation updates

### Creating a Branch

```bash
git checkout -b feature/your-feature-name
```

### Making Changes

1. Make your changes in the appropriate files
2. Follow the coding standards (see below)
3. Write or update tests if applicable
4. Update documentation if needed

## Coding Standards

### Java Code Style

- Follow Java naming conventions:
  - Classes: `PascalCase` (e.g., `BillController`)
  - Methods: `camelCase` (e.g., `createBill`)
  - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`)
  - Variables: `camelCase` (e.g., `billNumber`)

- Use meaningful variable and method names
- Keep methods focused and single-purpose
- Maximum method length: ~50 lines (exceptions allowed for complex logic)

### Code Organization

- **Controllers**: Handle HTTP requests/responses, minimal business logic
- **Services**: Contain business logic
- **Repositories**: Data access layer
- **DTOs**: Data transfer objects for API requests/responses
- **Entities**: JPA entities representing database tables

### Best Practices

1. **Validation**
   - Use Jakarta Validation annotations (`@Valid`, `@NotNull`, `@NotBlank`, etc.)
   - Validate input at the controller level

2. **Error Handling**
   - Use `GlobalExceptionHandler` for centralized error handling
   - Return appropriate HTTP status codes
   - Provide meaningful error messages

3. **Security**
   - Always use `@RequiresRole` for protected endpoints
   - Never expose sensitive information in error messages
   - Use location-based filtering for data isolation

4. **Database**
   - Use transactions where appropriate (`@Transactional`)
   - Avoid N+1 query problems
   - Use proper indexing for frequently queried fields

5. **Logging**
   - Use appropriate log levels (DEBUG, INFO, WARN, ERROR)
   - Log important operations and errors
   - Don't log sensitive information (passwords, tokens)

### Example Code Structure

```java
@RestController
@RequestMapping("/api/resource")
public class ResourceController {
    
    @Autowired
    private ResourceService resourceService;
    
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<ResourceResponseDTO> createResource(
            @Valid @RequestBody ResourceRequestDTO requestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ResourceResponseDTO response = resourceService.createResource(requestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
```

## Commit Guidelines

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

### Examples

```
feat(bills): Add PDF download functionality

- Implement PDF generation for bills
- Add download endpoint
- Update bill service to support PDF generation

Closes #123
```

```
fix(auth): Fix token expiration validation

The token expiration check was not working correctly
for tokens that were about to expire.

Fixes #456
```

## Pull Request Process

1. **Before Submitting**
   - Ensure your code follows the coding standards
   - Run tests and ensure they pass
   - Update documentation if needed
   - Rebase your branch on the latest main branch

2. **Creating a Pull Request**
   - Provide a clear title and description
   - Reference related issues (e.g., "Closes #123")
   - Include screenshots if UI changes are involved
   - List any breaking changes

3. **Pull Request Template**

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing performed
- [ ] All tests pass

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex code
- [ ] Documentation updated
- [ ] No new warnings generated
```

4. **Review Process**
   - Address review comments promptly
   - Make requested changes
   - Keep discussions constructive

## Testing

### Unit Tests

- Write unit tests for service layer methods
- Aim for high code coverage (target: 80%+)
- Use meaningful test names: `shouldReturnBillWhenValidRequestProvided()`

### Integration Tests

- Test API endpoints with real database
- Test authentication and authorization
- Test location-based filtering

### Manual Testing

- Test all affected endpoints
- Test error scenarios
- Test with different user roles

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=BillServiceTest

# Run with coverage
mvn test jacoco:report
```

## Documentation

### Code Documentation

- Add JavaDoc comments for public methods
- Document complex algorithms or business logic
- Include parameter and return value descriptions

### API Documentation

- Update `API_DOCUMENTATION.md` for new endpoints
- Include request/response examples
- Document any breaking changes

### README Updates

- Update README.md for significant changes
- Add new configuration options
- Update installation instructions if needed

## Project-Specific Guidelines

### Location-Based Data Isolation

- Always extract location from JWT token using `RequestUtil.getLocationFromRequest()`
- Filter queries by location
- Never allow cross-location data access

### JWT Authentication

- Use `@RequiresRole` annotation for role-based access
- Validate tokens in filters
- Handle token expiration gracefully

### Bill Management

- Support both GST and Non-GST bills
- Generate unique bill numbers
- Support PDF generation and email delivery

## Getting Help

- Check existing issues and pull requests
- Review the documentation
- Ask questions in discussions or issues
- Contact the maintainers

## Recognition

Contributors will be recognized in:
- Project README
- Release notes
- Project documentation

Thank you for contributing to Kataria Stone World APIs!

