# Distributed Elastic File Storage System

![Java](https://img.shields.io/badge/Java-JavaFX_&_Maven-183253?style=flat-square&logo=openjdk&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-9_Container_Cluster-187D83?style=flat-square&logo=docker&logoColor=white)
![MQTT](https://img.shields.io/badge/MQTT-Message_Broker-183253?style=flat-square)
![SQLite & MySQL](https://img.shields.io/badge/Databases-SQLite_&_MySQL-187D83?style=flat-square&logo=mysql&logoColor=white)

A containerised, distributed file storage and load-balancing system built in Java and Docker. The platform combines a JavaFX desktop client, an MQTT-driven custom load balancer, hybrid local/centralised database persistence, file chunking with AES encryption, and concurrency-safe file locking across a multi-container Docker network.

---

## 1. System Architecture & Core Features

* **Multi-Container Docker Infrastructure & MQTT Messaging:** Deploys 9 Docker containers over a shared virtual network (`docker-compose.yml`), routing file requests and chunk transfers between `MainContainer` and `Buffer` via an MQTT message broker (`LoadBalancer.java`, `MQTTHelper.java`).
* **Load Balancing, Traffic Emulation & Starvation Prevention:** Distributes storage requests across containers while emulating low, medium, and high network traffic conditions using dynamic delay multipliers over 30–90 second latency windows (`TrafficSimulation.java`, `Task.java`). Incorporates efficiency-based request scheduling to prevent lower-priority request starvation under peak load (`SimpleSchedulingSimulator.java`).
* **Concurrency Control & Audit Logging:** Implements file-level locking during upload, download, and deletion operations to maintain data integrity under concurrent access, paired with system-wide event and audit logging (`FileLockManager.java`).
* **File Chunking & AES Encryption:** Splits files into smaller distributable chunks (`req25.java`) and applies AES encryption to both user credentials and file chunks prior to container storage, decrypting upon authorised retrieval (`req7.java`, `Buffer.java`, `req15.java`).
* **Hybrid Database Persistence & Synchronisation:** Pairs a low-latency local SQLite database for session state, user profiles, and file metadata (`req15.java`, `req4.java`) with a centralised MySQL database tracking distributed chunk allocations (`Buffer.java`), supported by SQLite-to-MySQL synchronisation (`req16.java`).
* **Role-Based Access Control, JavaFX GUI & Embedded Terminal:** Provides `standard` and `admin` user roles (`UserManagementController.java`), file sharing with read/write Access Control Lists (`FileManagementController.java`), a JavaFX desktop interface (`LoginOrRegisterController.java`, `MainMenuController.java`), and an integrated terminal emulator supporting `ls`, `cp`, `mv`, `mkdir`, `ps`, `whoami`, `tree`, and `nano` (`req23.java`).

---

## 2. Repository Structure

```text
Distributed-Elastic-File-Storage-System/
├── docker-compose.yml              # Multi-container network & service orchestration
├── Requirements/
│   ├── pom.xml                     # Maven dependencies (JavaFX, Eclipse Paho MQTT, SQLite/MySQL JDBC)
│   └── src/                        # Core Java source code, load balancer, encryption & FXML views
└── README.md
```

---

## 3. Tech Stack

* **Language & UI:** Java, JavaFX (FXML), Maven
* **Containerisation & Messaging:** Docker, Docker Compose, MQTT (Eclipse Paho)
* **Databases:** SQLite (local edge persistence), MySQL (centralised chunk metadata)
* **Security & Concurrency:** AES Encryption, Salted Credential Hashing, File-Level Mutex Locking

---

## 4. Setup & Execution

### Prerequisites
* Java JDK 17+ and Apache Maven
* Docker Desktop (with Docker Compose enabled)
* Local or networked MySQL server (for remote chunk metadata synchronisation)

### Running the System
1. **Start the Docker container cluster:**
   ```bash
   docker-compose up -d
   ```
2. **Build and launch the JavaFX client & load balancer:**
   ```bash
   cd Requirements
   mvn clean javafx:run
   ```