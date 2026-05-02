# AI Agent Rules

This document establishes the guidelines and standards that all AI agents must follow when working on the **mvnBuilder** project.

## 1. Code Documentation

- **Mandatory JavaDoc**: All methods (public, private, or protected) must include descriptive JavaDoc.
  - It must explain the purpose of the method.
  - It must document all parameters (`@param`).
  - It must document the return value (`@return`).
  - It must document any thrown exceptions (`@throws`).
- **Internal Comments**: The code must include explanatory comments in complex logic blocks to facilitate maintenance and understanding.

## 2. Architecture and Patterns

- **Spring Boot MVC**: The standard Spring Boot Model-View-Controller pattern must be strictly respected.
  - **Controller**: Only for handling HTTP requests, simple input validation, and returning views or responses.
  - **Service**: Layer where all business logic resides. Controllers must call services.
  - **Repository**: Data access layer. Services must call repositories.
  - **Model/Entity**: Definition of data structures and persistence entities.
- **Dependency Injection**: Prefer constructor injection over field injection with `@Autowired`.

## 3. Coding Standards

- Follow standard Java naming conventions (CamelCase).
- Keep methods short and with a single responsibility.
- Ensure the code is clean and readable.

## 4. Core Technologies

- **Backend**: Java / Spring Boot.
- **Frontend**: Thymeleaf / HTML / CSS / JS.
- **Project Management**: Maven.

## 5. Code Formatting

- Indentation: 4 spaces.
- Blank Lines: Use to separate logical blocks of code.
- Line Length: Maximum 120 characters.
- Use IntelliJ IDEA default code style for Java.

## 6. Java Style

- Use UTF-8 encoding.
- Use descriptive names for classes, methods, and variables.
- Avoid `var` keyword, prefer explicit types.
- All method parameters should be `final`.
- All variables should be declared as `final` where possible.
- Preference for immutability:
  - Avoid mutations of objects, specially when using for-each loops or Stream API using `forEach()`.
- Avoid magic numbers and strings; use constants instead.
- Check emptiness and nullness before operations on collections and strings.
- Avoid methods using `throws` clause; prefer unchecked exceptions.
- Use `@Override` annotation when overriding methods.
- Avoid `Objects.isNull()` and `Objects.nonNull()` for one or two variables; prefer direct null checks for better performance.
- Wrap multiple conditions in a boolean variable for better readibility.
- Prefer early returns.
- Avoid else statements when not necessary and try early returns.

## 7. Lombok Annotations

- Use `@RequiredArgsConstructor` from Lombok for dependency injection via constructor.
- Use `@Slf4j` from Lombok for logging.
- Use `@Builder(setterPrefix = "with")` for complex object creation.
- Avoid `@Data` annotation; prefer `@Getter` and `@Setter` for granular control.

## 8. Annotations

- **`@Service`**: For business logic classes.
- **`@Repository`**: For data access classes that extend JPA repositories or interact with the database.
- **`@RestController`**: For web controllers.
- **`@Component`**: For generic Spring components.
- **`@Configuration`**: For Spring configuration classes.
- **`@Autowired`**: Prefer constructor injection for production code and field injection only for tests.
- **`@ConfigurationProperties`**: For binding related properties avoid multiple `@Value` annotations. From more than 2 properties, consider using this annotation.
- **`@Transactional`**: Only Service classes should be annotated with `@Transactional` at class level to avoid transaction management in each method.
- **`@Validated`**: To enable Bean Validation in method parameters or classes.
- **`@PreAuthorize`**: at the controller layer when using Spring Security to enforce method-level security.
- Circular dependencies should be avoided. Avoid `@Order` annotation for dependency resolution.

## 9. Mappers

**Use MapStruct**

- For mapping between DTOs and entities.
- Define mapper interfaces with `@Mapper` annotation.
- Use `@Mapping` annotation for custom field mappings.
- Use `componentModel = "spring"` to allow Spring to manage mapper instances.
- Mapper should have as suffix `Mapper` (e.g., `UserMapper`).
- Name mapper methods clearly (e.g., `toDto`, `toEntity`).
- Example Mapper Interface:

  ```java
  @Mapper(componentModel = "spring")
  public interface UserMapper {
      @Mapping(source = "email", target = "emailAddress")
      UserDTO toDto(User user);
      @Mapping(source = "emailAddress", target = "email")
      User toEntity(UserDTO userDto);
  }
  ```

- For testing mappers, use `Mappers.getMapper(UserMapper.class)` to get an instance of the mapper.

**Use Static Mappers**

- Define a private constructor to prevent instantiation with `UnsupportedOperationException("This class should never be instantiated")`.
- Use static methods for mapping between DTOs and entities.
- Name mapper methods clearly (e.g., `toDto`, `toEntity`).
- Example Static Mapper Class:

  ```java
  public class UserMapper {
      private UserMapper() {
          throw new UnsupportedOperationException("This class should never be instantiated");
      }
      public static UserDTO toDto(final User user) {
          if (user == null) {
              return null;
          }
          return UserDTO.builder()
              .withId(user.getId())
              .withEmailAddress(user.getEmail())
              .build();
      }
      public static User toEntity(final UserDTO userDto) {
          if (userDto == null) {
              return null;
          }
          return User.builder()
              .withId(userDto.getId())
              .withEmail(userDto.getEmailAddress())
              .build();
      }
  }
  ```

## 10. Exception Handling

- Custom Exceptions: Create custom domain exception classes extending `RuntimeException`.
- Global Exception Handler: Use `@ControllerAdvice` and `@ExceptionHandler` to handle exceptions globally.
- HTTP Status Codes: Map exceptions to appropriate HTTP status codes in REST controllers.
- Error Response Structure: Define a consistent error response structure.

## 11. Testing

- Use JUnit 5 for unit and integration testing.
- Use Mockito for mocking dependencies in unit tests.
- Use `@WebMvcTest(ControllerClass.class)` for testing Spring MVC controllers.
- Use `@SpringBootTest` for integration tests that require the Spring context.
- Use `given/when/then` structure in test methods for clarity.
- Method naming could follow snake_case or camelCase convention for test methods (e.g., `get_user_by_id_ok`, `get_user_by_id_not_found_ko`).
- Avoid reflection in tests.
- Avoid business logic in tests; focus on behavior verification.

## 12. Logging

- Use `@Slf4j` annotation from Lombok for logging to avoid boilerplate code with Logger instances.
- Log at appropriate levels: `DEBUG`, `INFO`, `WARN`, `ERROR`.
- Include contextual information in logs (e.g., request IDs, user IDs).
- Avoid logging sensitive information.
- Use structured logging for better log management.
- Format log messages with placeholders (e.g., `{}`) instead of string concatenation.
- Logging info code could follow this template: `log.info("[MicroserviceName/ModuleName] - API-CALL/METHOD/ACTION: response: {}, userId: {}", body, userId);`
- Logging error code could follow this template: `log.error("[MicroserviceName/ModuleName] - API-CALL/METHOD/ACTION: errorMessage: {}, userId: {}", errorMessage, userId);`

## 13. Entities and Models (DTOs)

- **Package Location**:
  - All entities must be located in the `entityes` package.
  - All models (DTOs) must be located in the `model` package.
- **Attribute Documentation**:
  - All attributes in models and entities must have a comment explaining what they are.
