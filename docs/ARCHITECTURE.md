# MediManage System Architecture

## Overview

MediManage is built using a **layered MVC (Model-View-Controller) architecture** with a dedicated **DAO (Data Access Object) layer** for clean separation of concerns and maintainability.

## Architecture Pattern

### Layered Architecture

The application is organized into distinct layers, each with specific responsibilities:

```
┌───────────────────────────────────────────────────┐
│                 UI Layer (View)                   │
│           JavaFX Controllers & FXML               │
└────────────────────┬──────────────────────────────┘
                     │ User Interactions
                     │
┌────────────────────▼──────────────────────────────┐
│              Service Layer                        │
│         Business Logic & Orchestration            │
│    (AuthService, ReportService, etc.)             │
└────────────────────┬──────────────────────────────┘
                     │ Business Operations
                     │
┌────────────────────▼──────────────────────────────┐
│                DAO Layer                          │
│           Data Access Objects                     │
│  (MedicineDAO, BillDAO, CustomerDAO, etc.)        │
└────────────────────┬──────────────────────────────┘
                     │ JDBC/SQL
                     │
┌────────────────────▼──────────────────────────────┐
│              Model Layer                          │
│           Domain Entities (POJOs)                 │
│    (Medicine, Customer, Bill, User, etc.)         │
└────────────────────┬──────────────────────────────┘
                     │
┌────────────────────▼──────────────────────────────┐
│         Configuration Layer                       │
│          DatabaseConfig, Utils                    │
└────────────────────┬──────────────────────────────┘
                     │
┌────────────────────▼──────────────────────────────┐
│            SQLite Database                        │
│          medimanage.db                            │
└───────────────────────────────────────────────────┘
```

## Layer Responsibilities

### 1. UI Layer (View + Controller)

**Location:** `src/main/java/org/example/MediManage/*Controller.java` + `src/main/resources/org/example/MediManage/*-view.fxml`

**Responsibilities:**
- Handle user input and interactions
- Display data to users
- Validate form inputs (client-side)
- Navigate between views
- Bind UI components to model data

**Key Controllers:**
- `LoginController` - Authentication UI
- `DashboardController` - Main hub with KPIs and navigation
- `BillingController` - POS interface and checkout
- `InventoryController` - Medicine management
- `CustomersController` - Customer profiles
- `UsersController` - User account management
- `ReportsController` - Report generation interface

**Design Principles:**
- Controllers should be **thin** - delegate business logic to services
- Use **JavaFX Properties** for reactive UI binding
- Handle **asynchronous operations** with Task/Platform.runLater
- Never perform direct database operations in controllers

### 2. Service Layer

**Location:** `src/main/java/org/example/MediManage/service/`

**Responsibilities:**
- Implement complex business logic
- Coordinate between multiple DAOs
- Handle cross-cutting concerns (logging, transactions)
- Provide facade for controllers

**Services:**
- `AuthService` - Login/logout, session management
- `ReportService` - PDF generation, thermal printing
- `DatabaseService` - Database initialization, backups

**Benefits:**
- Reusable business logic
- Transaction coordination
- Easier testing (mock services)
- Clear business rules location

### 3. DAO Layer (Data Access)

**Location:** `src/main/java/org/example/MediManage/dao/`

**Responsibilities:**
- Execute SQL queries
- Map database records to Java objects
- Handle database transactions
- Manage connections
- Implement CRUD operations

**DAOs:**
- `MedicineDAO` - Medicine inventory operations
- `CustomerDAO` - Customer management
- `BillDAO` - Invoice generation and sales queries
- `UserDAO` - User authentication and management
- `ExpenseDAO` - Expense tracking

**Design Patterns:**
- **DAO Pattern** - Separates persistence logic
- **Connection Pooling** - Reuse connections via DatabaseConfig
- **Prepared Statements** - Prevent SQL injection
- **Transaction Management** - ACID compliance

**Example Transaction (BillDAO.generateInvoice):**
```java
Connection conn = DatabaseConfig.getConnection();
conn.setAutoCommit(false);
try {
    // Insert bill
    // Insert bill items
    // Update stock
    // Update customer balance (if credit)
    conn.commit();
} catch (Exception e) {
    conn.rollback();
    throw e;
}
```

### 4. Model Layer (Domain Entities)

**Location:** `src/main/java/org/example/MediManage/model/`

**Responsibilities:**
- Represent business entities
- Encapsulate data and validation rules
- Provide JavaFX properties for UI binding

**Entities:**
- `Medicine` - Product information
- `Customer` - Customer profile
- `Bill` - Invoice header
- `BillItem` - Invoice line item
- `User` - User account
- `Expense` - Operating cost
- `UserRole` - Enum for roles

**Design:**
- **JavaFX Properties** - `StringProperty`, `DoubleProperty` for reactive UI
- **Immutability** - Where appropriate (e.g., enums)
- **Validation** - Basic validation in setters
- **No business logic** - Keep POJOs simple

### 5. Configuration Layer

**Location:** `src/main/java/org/example/MediManage/config/`, `util/`

**Responsibilities:**
- Database connection management
- Application configuration
- Utility functions

**Key Classes:**
- `DatabaseConfig` - Connection factory, dev/prod mode detection
- `DatabaseUtil` - Schema initialization, migrations
- `UserSession` - Session state management
- `PDFUtil` - PDF generation helpers

## Design Patterns Used

### 1. MVC (Model-View-Controller)
- **Model:** Domain entities in `model/`
- **View:** FXML files in `resources/`
- **Controller:** Controllers manage interaction

### 2. DAO (Data Access Object)
- Abstracts database operations
- Provides clean data access interface
- Separates SQL from business logic

### 3. Singleton
- `DatabaseConfig` - Single connection pool
- `UserSession` - Single active session

### 4. Factory
- `DatabaseConfig.getConnection()` - Creates connections

### 5. Observer (JavaFX)
- JavaFX Properties for reactive UI updates
- Event handlers for user interactions

## Data Flow Examples

### Example 1: User Login

```
User Input → LoginController
            ↓
        AuthService.login()
            ↓
        UserDAO.authenticate()
            ↓
        DatabaseConfig.getConnection()
            ↓
        SQLite Database Query
            ↓
        User object ← UserDAO
            ↓
        Store in UserSession ← AuthService
            ↓
        Navigate to Dashboard ← LoginController
```

### Example 2: Generate Invoice

```
Cashier scans items → BillingController
                    ↓
                MedicineDAO.searchByBarcode()
                    ↓
                Add to TableView
                    ↓
Cashier clicks Checkout
                    ↓
            BillDAO.generateInvoice()
                    ↓
        [BEGIN TRANSACTION]
            • Insert into bills
            • Insert into bill_items
            • Update stock quantities
            • Update customer balance (if credit)
        [COMMIT]
                    ↓
            ReportService.generatePDF()
                    ↓
            ReportService.printReceipt()
                    ↓
        Invoice delivered to Cashier
```

### Example 3: Load Dashboard KPIs

```
User navigates → DashboardController.loadKPIs()
                    ↓
            [Parallel Async Tasks]
                    ↓
        ┌───────────┼───────────┐
        ↓           ↓           ↓
    BillDAO     ExpenseDAO   MedicineDAO
getDailySales  getTotalExp  getLowStock
        ↓           ↓           ↓
    [Calculate Net Profit]
        ↓
    Update UI on JavaFX thread
        ↓
    Display KPIs to User
```

## Key Architectural Decisions

### 1. Embedded SQLite Database
**Why:** 
- Zero configuration for users
- Portability (single file)
- No server setup required
- Suitable for single-user desktop app

**Trade-offs:**
- Not suitable for concurrent multi-user access
- Limited to ~1TB database size (plenty for pharmacy)

### 2. JavaFX for UI
**Why:**
- Rich desktop UI capabilities
- Native look and feel
- Built-in data binding
- Active community

**Trade-offs:**
- Desktop-only (not web)
- Larger distribution size

### 3. Layered Architecture
**Why:**
- Clear separation of concerns
- Easier testing and maintenance
- Reusable components
- Team collaboration

**Trade-offs:**
- More boilerplate code
- Slight performance overhead (negligible)

### 4. Bundled JRE
**Why:**
- No Java installation required by users
- Consistent runtime environment
- Easier deployment

**Trade-offs:**
- Larger installer (~120 MB)
- Need to update JRE separately

## Performance Considerations

### 1. Database Connections
- **Connection reuse** via `DatabaseConfig`
- **Prepared statements** for query caching
- **Batch inserts** for bill_items

### 2. UI Responsiveness
- **Async operations** with Task API
- **Background threads** for heavy operations
- **Platform.runLater()** for UI updates

### 3. Memory Management
- **Close resources** properly (Connection, ResultSet)
- **Limit query results** where appropriate
- **Lazy loading** for large datasets

## Security Considerations

### 1. Authentication
- Password hashing (should be implemented)
- Role-based access control
- Session timeout

### 2. SQL Injection Prevention
- **Prepared statements** exclusively
- **Parameterized queries** only
- Never concatenate user input into SQL

### 3. Data Validation
- Input validation in UI
- Server-side (DAO) validation
- Type safety with Java

## Scalability & Future Enhancements

### Current Limitations
- Single-user (SQLite limitation)
- Desktop-only
- Windows-focused installer

### Potential Upgrades
1. **Multi-user support**
   - Switch to PostgreSQL/MySQL
   - Client-server architecture
   - Concurrent access handling

2. **Web version**
   - Spring Boot backend
   - React/Angular frontend
   - RESTful API

3. **Mobile app**
   - React Native or Flutter
   - Barcode scanning on phone
   - Offline-first sync

4. **Cloud deployment**
   - AWS/Azure hosting
   - Database backups
   - Remote access

## Testing Strategy

### Unit Tests
- Test DAOs with in-memory SQLite
- Mock services in controller tests
- Test business logic independently

### Integration Tests
- Test full workflows (login → dashboard)
- Test transactions (billing)
- Test database migrations

### Manual Testing
- UI flows
- Barcode scanning
- Printer integration

## Best Practices

1. **Follow SOLID principles**
   - Single Responsibility
   - Open/Closed
   - Liskov Substitution
   - Interface Segregation
   - Dependency Inversion

2. **Keep layers independent**
   - Controllers don't access DAOs directly
   - DAOs don't know about UI
   - Models are pure POJOs

3. **Handle errors gracefully**
   - Try-catch blocks
   - User-friendly error messages
   - Logging

4. **Document code**
   - Javadoc for public methods
   - Comments for complex logic
   - README for components

---

**Last Updated:** January 2026  
**Maintained by:** MediManage Development Team
