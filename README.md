# FAQ Uploader Service

The **FAQ Uploader Service** is a Spring Boot application designed to manage and upload structured FAQ data into a Couchbase 8.0 database.
It provides a reliable backend component for enterprise chatbot and knowledge management systems, ensuring all FAQ content is indexed and stored in a searchable format.

---

## 1. Overview

The application reads FAQ entries from a JSON file and uploads them into Couchbase.
Each FAQ document contains text, optional images, and links.
This service forms the data ingestion layer of a larger conversational or search platform.

---

## 2. Key Features

* Built with **Spring Boot 3.5.x** and **Java 21**
* Uses **Couchbase Java SDK 3.9.x**
* Supports local and containerized deployment via **Docker Compose**
* Automatically reads FAQ data from JSON input files
* Gracefully handles duplicates and connection failures
* Configurable via environment variables and `.env` file
* Includes full integration tests using **Testcontainers** and **JUnit 5**

---

## 3. Architecture

The service follows clean code and layered architecture principles.

```
┌────────────────────────────────────┐
│           FAQ JSON File            │
└────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────┐
│        FaqFileReader (I/O)         │
│  Parses JSON → Domain Objects      │
└────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────┐
│     UploaderService (Business)     │
│  Generates IDs → Inserts into DB   │
└────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────┐
│   CouchbaseRepository (Persistence)│
│  Interacts with Couchbase Cluster  │
└────────────────────────────────────┘
```

---

## 4. Prerequisites

* Java 21
* Maven 3.9+
* Docker and Docker Compose (for Couchbase container)
* Internet access to pull base images and dependencies

---

## 5. Local Setup

### Step 1: Start Couchbase

Run Couchbase Server 8.0 using Docker Compose:

```bash
docker compose up -d couchbase
```

Access the Couchbase console at [http://localhost:8091](http://localhost:8091)
Use the following credentials for local development:

* Username: `admin`
* Password: `password`

Verify that the following resources exist:

* Bucket: `faq_bucket`
* Scope: `faq_scope`
* Collection: `faqs`

### Step 2: Configure Environment Variables

Create a `.env` file in the project root:

```bash
CB_CONN=couchbase://localhost
CB_USERNAME=admin
CB_PASSWORD=password
CB_BUCKET=faq_bucket
CB_SCOPE=faq_scope
CB_COLLECTION=faqs
CB_TLS_ENABLED=false
FAQ_JSON=./faq.json
```

### Step 3: Build the Application

```bash
mvn clean package -DskipTests
```

### Step 4: Run the Application

```bash
java -jar target/faq-uploader-1.0.0.jar
```

The application will start, connect to Couchbase, read from `faq.json`, and upload the FAQ entries.

---

## 6. Running in Docker

You can run both Couchbase and the FAQ Uploader together using Docker Compose.

### Example command

```bash
docker compose up --build
```

### Example service configuration

```yaml
services:
  couchbase:
    image: couchbase/server:8.0.0
    ports:
      - "8091:8091"
      - "8093:8093"
      - "11210:11210"
    environment:
      COUCHBASE_ADMINISTRATOR_USERNAME: admin
      COUCHBASE_ADMINISTRATOR_PASSWORD: password
    volumes:
      - ./data:/opt/couchbase/var
      - ./init:/docker-entrypoint-initdb.d

  faq-uploader:
    build: .
    depends_on:
      couchbase:
        condition: service_healthy
    environment:
      CB_CONN: couchbase://couchbase
      CB_USERNAME: admin
      CB_PASSWORD: password
      CB_BUCKET: faq_bucket
      CB_SCOPE: faq_scope
      CB_COLLECTION: faqs
      CB_TLS_ENABLED: false
      FAQ_JSON: /app/faq.json
    volumes:
      - ./faq.json:/app/faq.json
```

---

## 7. Verifying Data in Couchbase

After running the application, log in to the Couchbase Web Console and execute this N1QL query:

```sql
SELECT META().id, question, answer
FROM `faq_bucket`.`faq_scope`.`faqs`
WHERE type = "faq";
```

You should see your uploaded FAQ entries.

---

## 8. Running Tests

The project includes a complete integration test suite using **Testcontainers**.

To execute tests:

```bash
mvn test
```

Testcontainers automatically starts Couchbase 8.0 in a temporary container and validates:

* Connection establishment
* JSON parsing
* Data insertion and retrieval

---

## 9. Logging and Monitoring

Logging is implemented using **Log4j2**.
Log configuration can be customized via `log4j2.xml` or environment variables.
For production, logs can be redirected to ELK, CloudWatch, or similar systems.

---

## 10. Security and Hardening

* TLS and certificate verification are optional for local testing.
* In production, enable TLS and use a secure connection string (`couchbases://`).
* Never commit `.env`, keys, or credentials to version control.
* Use role-based access in Couchbase to limit privileges.

---

## 11. Directory Structure

```
faq-uploader/
├── src/
│   ├── main/
│   │   ├── java/io/github/jdeeplearn/rag/...
│   │   └── resources/
│   └── test/
│       ├── java/io/github/jdeeplearn/rag/...
│       └── resources/sample-faq.json
├── data/                     # Local Couchbase data (ignored by git)
├── init/                     # Optional Couchbase init scripts
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── .gitignore
├── .env
├── faq.json
└── README.md
```

---

## 12. Production Deployment

In production:

* Use **multi-stage Docker builds** for optimized images
* Configure **TLS (couchbases://)** for secure Couchbase communication
* Externalize configuration via environment variables or Kubernetes Secrets
* Enable health endpoints (`/actuator/health`) for monitoring
* Use CI/CD pipelines to build, test, and deploy automatically

---

## 13. License

This project is released under the MIT License.
Refer to the `LICENSE` file for details.

---

## 14. Support and Maintenance

* Issues and feature requests should be logged in the project’s issue tracker.
* Follow semantic versioning for releases.
* Regular dependency upgrades are recommended to maintain security compliance.

---

**End of Document**