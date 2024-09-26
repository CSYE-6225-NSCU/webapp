# Web Application Health Check API

## Project Overview

This web application provides a **Health Check API** that monitors the status of the application and its connection to a PostgreSQL database. It is implemented using **Spring Boot** and connects to a **PostgreSQL 16** database. The `/healthz` endpoint checks the health of the database connection and ensures that the application can handle requests.

## Technologies Used
- **Programming Language**: Java
- **Framework**: Spring Boot
- **Database**: PostgreSQL 16
- **ORM Framework**: Hibernate (JPA)

---

## Prerequisites

Before running this application, make sure you have the following installed:

1. **Java 17** or higher: [Download here](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
2. **PostgreSQL 16**: [Download and install PostgreSQL 16](https://www.postgresql.org/download/)
3. **Apache Maven**: [Install Maven](https://maven.apache.org/install.html)
4. **Git**: [Install Git](https://git-scm.com/)

---

## Project Setup

### Step 1: Clone the Repository

Clone the project from GitHub to your local machine.

```bash
git clone https://github.com/CSYE-6225-NSCU/webapp.git
cd webapp
```

### Step 2: Configure PostgreSQL

1. Ensure that PostgreSQL 16 is installed and running:
   ```bash
   brew services start postgresql@16
   ```

2. Log in to PostgreSQL as the default user (`postgres`) and create the necessary database and user:
   ```bash
   psql postgres
   ```

   In the PostgreSQL shell, create the `webapp_db` database and the `webapp_user` user with the appropriate privileges:

   ```bash
   CREATE DATABASE webapp_db;
   CREATE USER webapp_user WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE webapp_db TO webapp_user;
   ```

3. Update the `pg_hba.conf` file to allow local connections using MD5:
   ```bash
   sudo nano /usr/local/var/postgresql@16/pg_hba.confg
   ```

   Add the following line:
   ```
   local   all             all                                     md5
   ```

4. Restart PostgreSQL to apply the changes:
   ```bash
   brew services restart postgresql@16
   ```

### Step 3: Configure Application Properties

In the `src/main/resources/application.properties` file, update the PostgreSQL connection details:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/webapp_db
spring.datasource.username=webapp_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false

# Disable caching
spring.cache.type=none
```

### Step 4: Build and Run the Application

1. **Build the project** using Maven:

   ```bash
   ./mvnw clean install
   ```

2. **Run the application**:

   ```bash
   ./mvnw spring-boot:run
   ```

The application will run on `http://localhost:8080/`.

---

## API Endpoints

### Health Check API

#### **`GET /healthz`**

This endpoint checks the health of the application and its connection to the database.

- **Success Response (HTTP 200)**:
    - Returned when the application is able to connect to the database successfully.
    - No response body is included.

- **Failure Response (HTTP 503)**:
    - Returned when the application cannot connect to the database.
    - No response body is included.

- **Invalid Request (HTTP 400)**:
    - Returned if the request contains a payload (only GET requests without a body are allowed).

- **Unsupported Method (HTTP 405)**:
    - Returned if the request uses a method other than `GET`.

---

## Testing the API

### 1. **Successful Health Check**

Run this command to check if the database connection is healthy:

```bash
curl -vvvv http://localhost:8080/healthz
```

Expected Response:

```
< HTTP/1.1 200 OK
< Cache-Control: no-cache, must-revalidate
< Pragma: no-cache
< X-Content-Type-Options: nosniff
< Content-Length: 0
```

### 2. **Simulate Database Connection Failure**

To simulate a database connection failure, stop PostgreSQL:

```bash
brew services stop postgresql@16
```

Then, run the health check again:

```bash
curl -vvvv http://localhost:8080/healthz
```

Expected Response:

```
< HTTP/1.1 503 Service Unavailable
< Cache-Control: no-cache, must-revalidate
< Pragma: no-cache
< X-Content-Type-Options: nosniff
< Content-Length: 0
```

Restart PostgreSQL after testing:

```bash
brew services start postgresql@16
```

### 3. **Invalid Request with Payload**

To test that the API rejects requests with a payload, run:

```bash
curl -vvvv -X GET -d '{"test": "data"}' http://localhost:8080/healthz
```

Expected Response:

```
< HTTP/1.1 400 Bad Request
< Cache-Control: no-cache, must-revalidate
< Pragma: no-cache
< X-Content-Type-Options: nosniff
< Content-Length: 0
```

### 4. **Unsupported HTTP Method**

To test that the API only supports the GET method, run:

```bash
curl -vvvv -X PUT http://localhost:8080/healthz
```

Expected Response:

```
< HTTP/1.1 405 Method Not Allowed
< Allow: GET
```

---

## Deployment Instructions

You can deploy this application on any cloud provider that supports Java Spring Boot applications, such as Heroku, AWS, or GCP.

1. **Package the Application**:

   ```bash
   ./mvnw clean package
   ```

2. **Deploy the Jar** on your preferred cloud service.

---

## Notes

- **Security**: Ensure that no sensitive information (such as passwords) is committed to the repository.
- **Performance**: This is a simple API with minimal configuration, designed to be lightweight and fast.

---

## Conclusion

This web application provides a robust and simple health check mechanism for monitoring the status of a Spring Boot application and its PostgreSQL database connection.

---