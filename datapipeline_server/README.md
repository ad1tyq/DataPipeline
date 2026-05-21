# Identity Correlation Data Pipeline (Spring Boot)

This is a Spring Boot REST API that runs a 5-stage identity correlation engine. It ingests disconnected identity records from various systems (Core Banking, Active Directory, Email), clusters them using a Disjoint-Set (Union-Find) algorithm, applies survivorship rules to create a Golden Record, and emits a flattened graph structure ready for frontend rendering.

## Tech Stack
* **Java 21+** (Utilizes modern Records and Virtual Threads for parallel extraction)
* **Spring Boot 3.x** (Embedded Tomcat Web Server)
* **Maven** (Dependency Management)

## How to Run the Server

You do not need to install Tomcat or complex Java web servers. As long as you have Java 21+ and Maven installed, you can boot the server directly from the terminal.

1. Clone the repository.
2. Navigate to the root directory (where the `pom.xml` file is located).
3. Download dependencies and compile the project:
   ```bash
   mvn clean install
