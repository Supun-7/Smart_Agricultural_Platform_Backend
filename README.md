# 🌾 Ceylon Harvest Capital — Backend

> Smart Agricultural Investment Platform · REST API · Spring Boot 4 · Java 17

Ceylon Harvest Capital (CHC) is a smart agricultural finance platform that connects **farmers** seeking investment for their land projects with **investors** looking for agri-finance opportunities. This repository contains the complete backend REST API that powers the platform.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Domain Roles](#domain-roles)
- [Authentication & Security](#authentication--security)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Key Integrations](#key-integrations)
- [Configuration Guide](#configuration-guide)
- [Running Locally](#running-locally)
- [Environment Variables](#environment-variables)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.3 |
| Security | Spring Security + JWT (JJWT 0.11.5) |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL (hosted on Supabase) |
| Migrations | Flyway |
| File Storage | Supabase Storage |
| Blockchain | Polygon Amoy Testnet (Web3j 4.10.3) |
| Email / OTP | Spring Mail (SMTP) |
| Authentication | JWT + Google OAuth 2.0 |
| AI Chatbot | DeepSeek / Anthropic Claude / Google Gemini |
| Build Tool | Maven |

---

## Architecture Overview

The backend follows a standard layered architecture:

```
HTTP Request
     │
     ▼
┌─────────────────────┐
│   Controller Layer   │  ← Receives requests, validates auth headers
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│    Service Layer     │  ← Business logic, OTP, blockchain, email
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Repository Layer    │  ← Spring Data JPA — talks to PostgreSQL
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  PostgreSQL (Supabase)│
└─────────────────────┘
```

**Role-based access** is enforced using a custom `@RequiredRole` annotation backed by a `RoleInterceptor`. Every protected endpoint declares which role is allowed to call it. The JWT token is validated on every request and the user's role is extracted from its claims.

---

## Project Structure

```
src/main/java/CHC/Team/Ceylon/Harvest/Capital/
│
├── config/
│   ├── JacksonConfig.java          # JSON serialization settings
│   ├── RestTemplateConfig.java     # HTTP client for external API calls
│   ├── SecurityConfig.java         # Spring Security — CSRF disabled, JWT-managed
│   └── WebConfig.java              # CORS settings, interceptor registration
│
├── controller/
│   ├── AdminController.java         # User management, KYC/application review
│   ├── AdminDashboardController.java# Admin statistics dashboard
│   ├── AuditorController.java       # Pending KYC & farmer application queues
│   ├── AuditorHistoryController.java# Auditor decision history
│   ├── ChatbotController.java       # AI chatbot messaging
│   ├── FarmerController.java        # Land registration, milestones, profile
│   ├── GateController.java          # Post-login routing gate
│   ├── GoogleLoginController.java   # Google OAuth 2.0 login
│   ├── InvestorController.java      # KYC, opportunities, portfolio, investing
│   ├── MilestoneAuditorController.java # Milestone approval/rejection
│   ├── MilestoneEvidenceController.java# Evidence file upload
│   ├── UserController.java          # Register, login, OTP verify, resend OTP
│   └── WalletController.java        # Deposit, withdraw, balance
│
├── dto/                             # Request/response data transfer objects
│   ├── farmer/
│   ├── investment/
│   └── wallet/
│
├── entity/                          # JPA entities mapped to DB tables
│   ├── AdminAuditLog.java
│   ├── AuditLog.java
│   ├── Farmer.java
│   ├── FarmerApplication.java
│   ├── Investment.java
│   ├── KycSubmission.java
│   ├── Land.java
│   ├── Ledger.java
│   ├── Milestone.java
│   ├── Project.java
│   ├── User.java
│   └── Wallet.java
│
├── enums/
│   ├── AccountStatus.java           # ACTIVE | SUSPENDED
│   ├── AuditActionType.java
│   ├── InvestmentStatus.java        # PENDING | ACTIVE | COMPLETED | CANCELLED
│   ├── MilestoneStatus.java         # PENDING | APPROVED | REJECTED
│   ├── Role.java                    # FARMER | INVESTOR | ADMIN | AUDITOR | SYSTEM_ADMIN
│   └── VerificationStatus.java      # PENDING | VERIFIED | REJECTED | NOT_SUBMITTED
│
├── exception/
│   ├── BadRequestException.java
│   ├── ConflictException.java
│   ├── GlobalExceptionHandler.java  # Centralised @ControllerAdvice handler
│   └── ResourceNotFoundException.java
│
├── repository/                      # Spring Data JPA interfaces
│
├── security/
│   ├── JwtUtil.java                 # Token generation and validation
│   ├── RequiredRole.java            # Custom annotation for role enforcement
│   └── RoleInterceptor.java         # Intercepts requests and checks @RequiredRole
│
└── service/
    ├── blockchain/
    │   ├── BlockchainService.java           # Interface
    │   ├── MockBlockchainService.java       # Used in dev/test
    │   └── PolygonAmoyBlockchainService.java# Real Polygon Amoy transactions
    ├── EmailService.java            # SMTP email dispatch (OTP emails)
    ├── GateService.java             # Post-login gate routing logic
    ├── GoogleService.java           # Google token verification
    ├── InvestmentService.java       # Investment processing + blockchain
    ├── MilestoneService.java        # Milestone lifecycle management
    ├── OtpService.java              # OTP generation, verification, rate limiting
    ├── SupabaseStorageService.java  # Evidence file uploads to Supabase Storage
    ├── UserServiceImpl.java         # Registration, login, account management
    └── WalletService.java           # Deposit, withdraw, balance ledger
```

---

## Domain Roles

| Role | Description |
|---|---|
| `FARMER` | Registers land, submits milestones with evidence, manages their farm profile |
| `INVESTOR` | Completes KYC, browses land opportunities, invests funds via wallet |
| `AUDITOR` | Reviews pending KYC submissions, farmer applications, and milestones |
| `ADMIN` | Manages user accounts, suspends/activates users, reviews audit logs |
| `SYSTEM_ADMIN` | Superuser — seeded automatically on first startup |

---

## Authentication & Security

### Login Flow (Two-Step with OTP)

```
Step 1 — POST /api/users/login
  └─ Validates email + password
  └─ On success: generates OTP, sends to registered email
  └─ Returns: { message: "OTP sent...", email }

Step 2 — POST /api/users/verify-otp
  └─ Validates the 6-digit OTP (hashed in DB, expires in 5 minutes)
  └─ On success: returns JWT token + user object
```

### OTP Security Details

- OTPs are **6-digit**, generated using `SecureRandom`
- Stored as a **BCrypt hash** — never as plain text
- Expire after **5 minutes**
- Rate limited: maximum **3 resend requests** per 10-minute window
- Consumed (marked `verified = true`) immediately after successful use — cannot be reused

### JWT

All protected endpoints require the header:
```
Authorization: Bearer <token>
```

The JWT payload contains `userId`, `role`, and `verificationStatus`. It is validated and decoded by `JwtUtil` and the role is enforced by `RoleInterceptor` via the `@RequiredRole` annotation.

### Google OAuth 2.0

Investors and farmers can also authenticate via Google:
```
POST /api/auth/google          ← ID token flow
POST /api/auth/google/callback ← OAuth callback flow
```

---

## API Reference

### Authentication — `/api/users`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/users/register` | Public | Register a new user |
| `POST` | `/api/users/login` | Public | Step 1: validate credentials, send OTP |
| `POST` | `/api/users/verify-otp` | Public | Step 2: verify OTP, receive JWT |
| `POST` | `/api/users/resend-otp` | Public | Resend OTP (rate limited) |

### Google OAuth — `/api/auth`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/google` | Public | Login with Google ID token |
| `POST` | `/api/auth/google/callback` | Public | Google OAuth callback |

### Post-Login Gate — `/api/gate`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/gate/check` | JWT | Returns routing decision after login (PROCEED / PENDING / FAILED / NOT_SUBMITTED) |

### Farmer — `/api/farmer`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/farmer/dashboard` | FARMER | Dashboard summary |
| `GET` | `/api/farmer/profile` | FARMER | Farmer profile |
| `GET` | `/api/farmer/lands` | FARMER | List of registered lands |
| `GET` | `/api/farmer/lands/{landId}` | FARMER | Land detail |
| `POST` | `/api/farmer/lands` | FARMER | Register a new land project |
| `POST` | `/api/farmer/application` | FARMER | Submit farmer verification application |
| `PATCH` | `/api/farmer/lands/{landId}/active` | FARMER | Toggle land active status |
| `POST` | `/api/farmer/milestones` | FARMER | Submit a milestone with evidence |

### Investor — `/api/investor`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/investor/dashboard` | INVESTOR | Dashboard summary |
| `GET` | `/api/investor/profile` | INVESTOR | Investor profile |
| `GET` | `/api/investor/opportunities` | INVESTOR | Browse active land opportunities |
| `GET` | `/api/investor/portfolio` | INVESTOR | View funded lands |
| `POST` | `/api/investor/kyc` | INVESTOR | Submit KYC documents |
| `POST` | `/api/investor/lands/{landId}/invest` | INVESTOR | Invest in a land project |

### Wallet — `/api/investor/wallet`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/investor/wallet` | INVESTOR | Get balance and ledger history |
| `POST` | `/api/investor/wallet/deposit` | INVESTOR | Deposit funds |
| `POST` | `/api/investor/wallet/withdraw` | INVESTOR | Withdraw funds |

### Auditor — `/api/auditor`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/auditor/dashboard` | AUDITOR | Pending KYC and farmer applications |
| `GET` | `/api/auditor/queue` | AUDITOR | Full review queue |
| `PUT` | `/api/auditor/kyc/{id}/approve` | AUDITOR | Approve KYC submission |
| `PUT` | `/api/auditor/kyc/{id}/reject` | AUDITOR | Reject KYC submission |
| `PUT` | `/api/auditor/farmer/{id}/approve` | AUDITOR | Approve farmer application |
| `PUT` | `/api/auditor/farmer/{id}/reject` | AUDITOR | Reject farmer application |
| `GET` | `/api/auditor/history` | AUDITOR | Decision history |

### Milestone Auditing — `/api/auditor/milestones`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/auditor/milestones/pending` | AUDITOR | All pending milestones |
| `GET` | `/api/auditor/milestones/{milestoneId}` | AUDITOR | Milestone detail with evidence |
| `PUT` | `/api/auditor/milestones/{milestoneId}/approve` | AUDITOR | Approve milestone |
| `PUT` | `/api/auditor/milestones/{milestoneId}/reject` | AUDITOR | Reject with reason |

### Admin — `/api/admin`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/admin/users` | ADMIN | List all users |
| `GET` | `/api/admin/dashboard` | ADMIN | Platform statistics |
| `GET` | `/api/admin/audit-logs` | ADMIN | Full admin audit trail |
| `PUT` | `/api/admin/users/{userId}/suspend` | ADMIN | Suspend a user account |
| `PUT` | `/api/admin/users/{userId}/activate` | ADMIN | Activate a user account |
| `PUT` | `/api/admin/users/bulk-suspend` | ADMIN | Bulk suspend accounts |
| `PUT` | `/api/admin/users/bulk-activate` | ADMIN | Bulk activate accounts |
| `PUT` | `/api/admin/update-request/{userId}/approve` | ADMIN | Approve profile update request |
| `PUT` | `/api/admin/update-request/{userId}/reject` | ADMIN | Reject profile update request |

### AI Chatbot — `/api/chatbot`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/chatbot/message` | Public | Send a message, receive AI reply |
| `DELETE` | `/api/chatbot/history` | Public | Clear conversation history |

---

## Database Schema

Key tables in the `chc_db` PostgreSQL database:

| Table | Purpose |
|---|---|
| `users` | All platform users with role, verification status, account status |
| `wallets` | One wallet per investor — balance and currency |
| `lands` | Farmer land projects open for investment |
| `investments` | Records of investor-to-land funding transactions |
| `milestones` | Progress milestones submitted by farmers with evidence |
| `milestone_evidence_files` | File URLs attached to milestones |
| `farmer_applications` | Farmer identity/land verification submissions |
| `kyc_submissions` | Investor KYC document submissions |
| `otp_verifications` | Hashed OTPs with expiry and resend tracking |
| `audit_log` | Auditor decisions on milestones |
| `admin_audit_logs` | Admin actions on user accounts |
| `notifications` | In-app notifications per user |
| `contracts` | Investment contracts with JSONB ledger entries |
| `transaction` | Financial transaction records |

---

## Key Integrations

### Blockchain — Polygon Amoy Testnet
Investments are recorded as smart contract transactions on the Polygon Amoy testnet. The CHC system wallet pays all gas fees — neither farmers nor investors need a crypto wallet. In local/dev mode, `MockBlockchainService` is used instead of hitting the real network.

### Supabase Storage
Milestone evidence files (images, documents) are uploaded directly to a Supabase Storage bucket. The returned public URLs are stored in `milestone_evidence_files` and are accessible to auditors during milestone review.

### Google OAuth 2.0
Users can register and log in using their Google account. The backend verifies the Google ID token server-side using the Google API Client library before issuing a JWT.

### AI Chatbot
The chatbot supports three interchangeable AI backends configured via `application.properties`:
- **DeepSeek** (`deepseek-chat`)
- **Anthropic Claude** (`claude-sonnet-4-20250514`)
- **Google Gemini** (`gemini-2.5-flash`)

Conversation history is maintained per HTTP session.

---

## Configuration Guide

All configuration is in `src/main/resources/application.properties`. Sensitive values should never be committed to version control — use environment variables or a secrets manager in production.

```properties
# Database
spring.datasource.url=jdbc:postgresql://<host>:<port>/postgres
spring.datasource.username=<username>
spring.datasource.password=<password>

# JWT
jwt.secret=<base64-encoded-256-bit-secret>
jwt.expiration=3600000

# Default system admin (seeded on first startup)
admin.default.email=admin@chc.com
admin.default.password=<strong-password>
admin.default.fullName=System Admin

# Google OAuth
google.client.id=<google-client-id>
google.client.secret=<google-client-secret>

# Blockchain
blockchain.rpc.url=https://rpc-amoy.polygon.technology
blockchain.wallet.privateKey=<wallet-private-key>
blockchain.wallet.address=<wallet-address>

# Supabase Storage
supabase.url=https://<project-ref>.supabase.co
supabase.service-role-key=<service-role-key>
supabase.storage.bucket=milestone-evidence

# Email / OTP (Gmail example)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=<app-email>@gmail.com
spring.mail.password=<gmail-app-password-no-spaces>
otp.expiry.minutes=5
otp.max.resend.count=3
otp.resend.window.minutes=10

# AI Chatbot
deepseek.api.key=<key>
anthropic.api.key=<key>
gemini.api.key=<key>
```

### Generating the JWT Secret

Run this once in Java to generate a secure key, then paste the output as `jwt.secret`:

```java
byte[] key = Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded();
System.out.println(Base64.getEncoder().encodeToString(key));
```

---

## Running Locally

### Prerequisites

- Java 17
- Maven 3.8+
- A running PostgreSQL database (or Supabase project)

### Steps

```bash
# 1. Clone the repository
git clone <repo-url>
cd Smart_Agricultural_Platform_Backend-dev

# 2. Fill in application.properties with your credentials

# 3. Build the project
./mvnw clean install

# 4. Run
./mvnw spring-boot:run
```

The server starts at:
```
http://localhost:8080
```

The default system admin account is created automatically on first startup using the `admin.default.*` properties.

### Running Tests

```bash
./mvnw test
```

---

## Environment Variables

For production deployments (Render, Railway, AWS, etc.), set these as environment variables and reference them in `application.properties` using `${VAR_NAME}` syntax:

| Variable | Description |
|---|---|
| `DB_URL` | Full JDBC PostgreSQL connection string |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Base64-encoded HS256 key |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `BLOCKCHAIN_PRIVATE_KEY` | Polygon Amoy wallet private key |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase service role key |
| `MAIL_USERNAME` | SMTP sender email address |
| `MAIL_PASSWORD` | SMTP password or app password |
| `DEEPSEEK_API_KEY` | DeepSeek AI API key |
| `ANTHROPIC_API_KEY` | Anthropic Claude API key |
| `GEMINI_API_KEY` | Google Gemini API key |

---

## Authors

**Ceylon Harvest Capital Development Team**  
Smart Agricultural Investment Platform