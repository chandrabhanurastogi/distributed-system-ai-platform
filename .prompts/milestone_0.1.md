Here are my answers to the 5 Starting Architecture interview questions from ROADMAP.md
1- Why does each service get its own database instead of a shared schema, and what do you give up by doing that?
Service specific database helps in achieving true microservice architecture as each service can take individual decision. The schema and tables will be very focused on specific service entities. 
It helps in maintaining and managing by a single team. There is always sync required with other serivce's request response before making schema change decisions but there is more ownership with one team. 
However, it also introduces the need of storing primary keys as nonrelational columns if the table does not exist in the db of a microservice schema. You loose the JPA managed lazy loading benefit as you'd 
have to call other service to find the content of table who's primary key you know but can't fetch other details. 

2- inventory-service currently has spring-kafka on its classpath but nothing produces or consumes a message. Is that a problem? Why or why not?
It adds unnecessary classpath bloat and footprint. More importantly, auto-configuration might attempt broker connections or expose dead endpoints if not explicitly disabled. 
It represents accidental scaffolding debt that should be activated deliberately. 

3- Why is Java 25 (LTS) pinned via a Gradle toolchain block instead of just relying on whatever JDK happens to be on the developer's PATH?
Then the application might work in 1 machine and not in another. toolchain pinning helps gureenting that every machine and CI runner compiles against the same jdk version 

4- The two services currently share zero code — no common library module. When (if ever) would you introduce a common/shared-kernel module, and what's the risk of introducing it too early?
when same tasks are required to be done from all microservice such as maker-checker operations, then a common module will make sense. Introducing it too early will make releases dependent on each other. Common module
will introduce version drift and will make it difficult to keep services up to date with the latest version of common module. 

5- Both services are structurally identical skeletons right now. What is the actual architectural reason order-service and inventory-service must be two separate deployable services rather than two packages in one Spring Boot app?
as we are building real world distribution system, the separate services are created to separate the domain boundaries so that impact on 1 service doesn't impact other immediately. 

Decision for Milestone 0.2:
I choose Option A (One Postgres container per service) to enforce real process failure isolation so that stopping one database container doesn't take down the other service.
I have decided to use managed cloud services (serverless Postgres via Neon.tech) rather than running local database containers.  
Architectural Justification:
Resource Conservation: It offloads compute and memory overhead completely from the local development laptop.
Cloud-Native Parity: It enforces real network hops, latency variance, SSL/TLS connection requirements, and proper 12-factor secret management from Day 1.
Isolation: We maintain separate databases (order_db and inventory_db) owned independently by each service without local daemon footprint.

Please execute the following:

Draft ADR-0003: Record the decision to use managed serverless cloud databases (Neon.tech) with environment-driven credentials over local Docker daemon containers, 
noting the trade-offs (requires active internet connection vs. zero local memory overhead).

Environment & Secrets Template:
Create an .env.example file in the project root documenting the required variables such as db urls, usernames and passwords. Ensure .env is listed in .gitignore.

Spring Configuration:
Configure order-service/src/main/resources/application.yml and inventory-service/src/main/resources/application.yml to bind their respective datasources using these environment variables, enabling SSL mode (sslmode=require) and connection pooling.

To complete Milestone 0.1:
Read `CLAUDE.md` and `ROADMAP.md`.

We are currently completing Milestone 0.1.

The only remaining task is the root `README.md`.

Create the README with:

* project purpose
* current services
* prerequisites
* how to build
* how to run order-service
* how to run inventory-service
* ports
* links to ROADMAP.md, CLAUDE.md, ARCHITECTURE.md and ADR/

Keep it concise.

Do not implement anything else. such as DO NOT following:
add PostgreSQL
add Docker
add APIs
add Kafka
create new services
modify architecture
start Milestone 0.2

After creating it:

1. Verify the README is accurate against the actual repository.
2. Do not invent commands or configuration.
3. Update ROADMAP.md to mark only the README item complete if verification passes.
4. Do not start Milestone 0.2.

Then report exactly:

* files changed
* verification performed
* whether Milestone 0.1 is now complete
* what Milestone 0.2 requires
