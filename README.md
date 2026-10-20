# Seat Booking System

A backend RESTful service for managing seat bookings and events, built with Spring Boot, PostgreSQL, and Flyway.

---

## 🛠 Tech Stack

- **Java 21**
- **Spring Boot 4.x** (Web, Data JPA, Validation, Actuator)
- **Spring Security & OAuth2 Resource Server** (Stateless JWT authentication and role-based access control)
- **PostgreSQL 17**
- **Flyway** (Database migrations)
- **Docker & Docker Compose** (Local database orchestration)
- **Maven** (Build tool)

---

## 📋 Prerequisites

Ensure you have the following installed locally:

- [Java 21 SDK](https://www.oracle.com/java/technologies/downloads/#java21)
- [Docker](https://www.docker.com/) & Docker Compose
- Git

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Keval2609/seat-booking-system.git
cd seat-booking-system
```

### 2. Configure Environment

Create a `.env` file in the project root to configure required variables (such as database credentials and JWT secret):

```env
DB_PASSWORD=your_secure_password
JWT_SECRET=your_32_byte_base64_encoded_secret
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=admin_secure_password
```

> **Note:** Generate a secure 256-bit secret for `JWT_SECRET`:
> ```bash
> openssl rand -base64 32
> ```

### 3. Start PostgreSQL Database

Start the PostgreSQL container via Docker Compose:

```bash
docker compose up -d
```

> The database runs on `localhost:15432` with database `seat_booking` and user `seat_app`.

### 4. Build and Run the Application

Using the Maven wrapper:

**On Linux/macOS:**
```bash
./mvnw spring-boot:run
```

**On Windows (PowerShell / Command Prompt):**
```powershell
.\mvnw.cmd spring-boot:run
```

Flyway will automatically apply database migrations upon startup.

---

## 🧪 Running Tests

To execute unit and integration tests:

```bash
./mvnw test
```

---

## 🔌 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Application health check endpoint |
| `GET` | `/actuator/health` | Spring Boot Actuator health endpoint |
| `POST` | `/api/auth/register` | Register a new user account |
| `POST` | `/api/auth/login` | Authenticate and obtain a JWT access token |
| `POST` | `/api/events` | Create a new event (ADMIN) |
| `GET` | `/api/events` | List events, paginated |
| `POST` | `/api/events/{id}/seats/bulk` | Bulk-create seats for an event (ADMIN) |
| `GET` | `/api/events/{id}/seats` | List seats with status for an event |
| `POST` | `/api/events/{id}/holds` | Hold 1 to 6 seats for an event (USER) |
| `DELETE` | `/api/holds/{holdId}` | Release own hold (USER) |

> **Note (NFR-7):** Authentication uses short-lived (15-minute) JWT access tokens with no token revocation in v1; compromised tokens remain valid until expiration (accepted risk).

### Health Check Example

```bash
curl http://localhost:8080/api/health
```

**Response:**
```json
{
  "status": "UP"
}
```

---

## 📂 Project Structure

```
seat-booking-system/
├── compose.yaml              # Docker Compose configuration for PostgreSQL
├── pom.xml                   # Maven project dependencies and build configuration
└── src/
    ├── main/
    │   ├── java/com/dev/seatbooking/
    │   │   ├── auth/         # Authentication endpoints, services, and DTOs
    │   │   ├── config/       # Security, JWT, and Admin bootstrap configuration
    │   │   ├── controller/   # REST Controllers (e.g., EventController, HealthController)
    │   │   ├── dto/          # Data Transfer Objects
    │   │   ├── entity/       # JPA Entities
    │   │   ├── error/        # ErrorResponse, ApiException, and GlobalExceptionHandler
    │   │   ├── repository/   # Spring Data Repositories
    │   │   ├── security/     # JWT service and authentication failure handlers
    │   │   └── service/      # Business Logic Services
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/ # Flyway SQL migration scripts
    └── test/                 # Unit and integration tests
```
