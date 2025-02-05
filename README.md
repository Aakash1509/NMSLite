# 🌐 Network Monitoring System

## 🚀 Overview
This project is a **Network Monitoring System** that enables credential management, device discovery, provisioning, scheduling, and data polling using a combination of **Java (Vert.x) 🏗️**, **Golang 🐹**, **ZMQ 🔄**, and **PostgreSQL 🗄️**.

## 🔧 Features & Modules

### 1️⃣ Credential Module (CRUD)
- Provides **5 REST APIs**: `create`, `get`, `getAll`, `update`, `delete`.
- Stores credentials required for **SNMP 📡** and **SSH 🔑** connections.
- Supports both **network devices (SNMP) 🌍** and **Linux devices (SSH) 🖥️**.

### 2️⃣ Discovery Module (CRUD + Device Discovery)
- Provides **6 REST APIs**: `create`, `get`, `getAll`, `update`, `delete`, `/:id/run`.
- Stores discovery details like **IP 📍, port 🚪, and credential profiles 🔐**.
- The `/:id/run` API verifies:
  - Ping status 📶, port availability ✅, and connection establishment 🔗.
  - Uses a **Go plugin 🛠️** for SSH/SNMP validation.

### 3️⃣ Provision Module (Assigning Metrics to Devices)
- **API:** `/:id/provision`
- Assigns **metrics 📊** to discovered devices (Linux/SNMP-based).

### 4️⃣ Scheduling & Data Polling
- Polling process triggers the Go plugin via **ProcessBuilder 🏗️**.
- Data is collected at scheduled intervals and stored as **files 📂**.
- **File Naming Convention:** `ip_timestamp.log` 🕒

### 5️⃣ Data Transfer to Go Database
- Uses **ZMQ 🔄** for communication between **Vert.x (Java) ☕** and **Go 🐹**.
- Transfers collected data to Go, where it is stored in **files 📄**.
- Files are deleted from Vert.x after processing. ❌

## 🛠️ Tech Stack
- **Backend:** Java (Vert.x) ☕, Golang 🐹
- **Database:** PostgreSQL 🗄️
- **Communication:** ZMQ 🔄
- **Networking:** SSH 🔑, SNMP 📡

## 🌟 Key Achievements
- Built **REST APIs 🎯** for credential and discovery management.
- Implemented a **Golang-based polling mechanism 🤖** to fetch device data.
- **Optimized database interactions ⚡** using HashMap, reducing **GET requests by 67% 📉** (from 12 to 4).
- Transferred **polled data via ZMQ 🔄** to Go for structured storage and efficient processing. 📂

## 🛠️ Usage
- Use the **Credential Module 🔐** to create and manage authentication details.
- Use the **Discovery Module 🔍** to identify devices in the network.
- Provision devices and attach **metrics 📊**.
- Enable **scheduling ⏳** to automate polling.
- Transfer and store collected data efficiently using **ZMQ 🔄**.

