# Ceylon Harvest Capital – Backend

## Overview
Backend service for Ceylon Harvest Capital Agri-Finance Platform.

Built using:
- Spring Boot
- PostgreSQL (Supabase)
- JPA / Hibernate
- REST APIs
- JWT Authentication (if implemented)

This service handles:
- User management
- Role-based access control
- Project creation & funding logic
- Admin & auditor controls
- Database interactions

---

## Tech Stack

- Java 17+
- Spring Boot
- Spring Security
- PostgreSQL (Supabase)
- Maven

---

## Environment Variables

Create application.properties or use environment variables:

```
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
JWT_SECRET=
```

---

## Run Locally

```
mvn clean install
mvn spring-boot:run
```

Server runs on:
```
http://localhost:8080
```

---

## API Base URL

```
/api/v1
```

---

## Architecture

- Controller Layer
- Service Layer
- Repository Layer
- DTO Pattern
- Role-based authorization

---

## Roles

- Farmer
- Investor
- Admin
- Auditor

---

## Deployment

Designed for deployment on:
- Render
- AWS
- Railway

---

## Author
Ceylon Harvest Capital Development Team
