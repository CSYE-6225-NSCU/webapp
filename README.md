# Web Application Health Check API

## Project Overview

This web application provides a **Health Check API** that monitors the status of the application and its connection to a PostgreSQL database. It is implemented using **Spring Boot** and connects to a **PostgreSQL 16** database. The `/healthz` endpoint checks the health of the database connection and ensures that the application can handle requests.

## Technologies Used

- **Programming Language**: Java
- **Framework**: Spring Boot
- **Database**: PostgreSQL 16
- **ORM Framework**: Hibernate (JPA)

## Prerequisites

Before running this application, make sure you have the following installed:

1. **Java 17** or higher: [Download here](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
2. **PostgreSQL 16**: [Download and install PostgreSQL 16](https://www.postgresql.org/download/)
3. **Apache Maven**: [Install Maven](https://maven.apache.org/install.html)
4. **Git**: [Install Git](https://git-scm.com/)
5. **An SSH client** (e.g., OpenSSH)
6. **Postman for API testing**

## Project Setup

### Clone the Repository

Clone the project from GitHub to your local machine:

```bash
git clone https://github.com/CSYE-6225-NSCU/webapp.git
cd webapp
```

## Setting Up the DigitalOcean VM

### 1. Launch a DigitalOcean Ubuntu 24.04 LTS Droplet

- Create a new droplet in your DigitalOcean account using Ubuntu 24.04 LTS.

### 2. Connect to the Droplet

Create an SSH shortcut to connect to your droplet for easier access:

1. Open the SSH config file:
   ```bash
   nano ~/.ssh/config
   ```

2. Add the following configuration:
   ```
   Host digitalocean
   HostName <YOUR_DROPLET_IP_ADDRESS>
   User <YOUR_SSH_USERNAME>
   IdentityFile ~/.ssh/<YOUR_PRIVATE_KEY_FILE>
   Port 22
   ```
   Replace the placeholders accordingly.

3. Save and exit the file (Ctrl + O, then Enter to save; Ctrl + X to exit).

4. Change file permissions:
   ```bash
   chmod 600 ~/.ssh/config
   ```

5. Connect using:
   ```bash
   ssh digitalocean
   ```

### 3. Set Up the Environment on the Droplet

1. Install Java:
   ```bash
   sudo apt update
   sudo apt install openjdk-17-jdk -y
   ```

2. Install PostgreSQL:
   ```bash
   sudo apt install postgresql postgresql-contrib -y
   ```

3. Install Maven:
   ```bash
   sudo apt install maven -y
   ```

4. Configure PostgreSQL:
   ```bash
   sudo -i -u postgres
   psql
   ```
   In the PostgreSQL shell:
   ```sql
   CREATE USER webapp_user WITH PASSWORD 'your_password';
   CREATE DATABASE webapp_db;
   GRANT ALL PRIVILEGES ON DATABASE webapp_db TO webapp_user;
   ALTER USER webapp_user WITH SUPERUSER;
   \q
   ```

### 4. Transfer Project Files to the Droplet

On your local machine:
```bash
scp -r /path/to/your/webapp digitalocean:/home/
```

### 5. Configure the Application

Update the `application.properties` file in `src/main/resources`:

```properties
spring.application.name=webapp
spring.datasource.url=jdbc:postgresql://localhost:5432/webapp_db
spring.datasource.username=${DB_USERNAME:webapp_user}
spring.datasource.password=${DB_PASSWORD:your_password}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jackson.property-naming-strategy=SNAKE_CASE
spring.jackson.deserialization.fail-on-unknown-properties=true
spring.jackson.time-zone=UTC
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### 6. Build and Run the Application

```bash
cd /home/webapp
mvn clean install
mvn spring-boot:run
```

### 7. Set Up Firewall Rules

```bash
sudo ufw allow 8080
sudo ufw allow ssh
sudo ufw enable
```

### 8. Verify the Setup

```bash
curl http://<YOUR_DROPLET_IP_ADDRESS>:8080/healthz
```

## Testing the API with Postman

Base URL: `http://<YOUR_DROPLET_IP_ADDRESS>:8080`

1. Create a User (POST /v1/user)
2. Get User Information (GET /v1/user/self)
3. Update User Information (PUT /v1/user/self)
4. Health Check (GET /healthz)

For detailed request and response examples, refer to the original README.

## Notes

- Ensure PostgreSQL is running: `sudo service postgresql start`
- Database tables should be automatically created on application start.
- Verify that the firewall (UFW) is enabled and allowing port 8080.
- Do not commit sensitive information (like passwords) to the repository.
- This API is designed to be lightweight and fast.

## Conclusion

This web application provides a robust and simple health check mechanism for monitoring the status of a Spring Boot application and its PostgreSQL database connection.