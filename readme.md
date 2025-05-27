# RandomEvents Plugin

A Minecraft plugin for generating random events on your server.

## Requirements

- Paper/Spigot server 1.19+
- Java 17 or higher

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/njm25/RandomEvents.git
cd RandomEvents
```

### 2. Configuration Setup

Create a new file named `config.txt` and configure the following settings:
```
PROJECT_DIR=C:\Users\Name\GitHub\RandomEvents
SERVER_DIR=C:\Users\Name\Server
PLUGIN_NAME=RandomEvents-1.0.jar
SERVER_JAR=paper-1.21.5-101.jar
```

### 3. Deployment

#### Windows 

   ```powershell
   cd RandomEvents
   .\deploy.bat
   ```

#### Linux/Mac 
   ```bash
   cd RandomEvents
   chmod +x deploy.sh
   ./deploy.sh
   ```