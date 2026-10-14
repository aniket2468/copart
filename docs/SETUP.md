# PeopleFlow setup guide

This guide explains everything required to clone and run PeopleFlow on macOS,
Windows, or Linux. The Docker setup is recommended because it runs PostgreSQL,
the Spring Boot API, and the React application in containers with matching
versions on every operating system.

## Choose a setup

### Recommended: Docker

Install only:

- [Git](https://git-scm.com/downloads)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) on macOS or
  Windows, or Docker Engine with the Compose plugin on Linux
- [Visual Studio Code](https://code.visualstudio.com/download) (optional, but
  recommended)

Java, Maven, Node.js, npm, and PostgreSQL do **not** need to be installed on the
computer when using Docker.

### Native development

Install all of the following if you do not want to use Docker:

- Git
- Java Development Kit (JDK) 21
- Maven 3.9+
- Node.js 20+ with npm (Node.js 22 LTS is recommended)
- PostgreSQL 16
- Visual Studio Code (optional)

## macOS

### Install the Docker prerequisites

Install [Homebrew](https://brew.sh/) if it is not already installed, then run:

```bash
brew install git
brew install --cask docker visual-studio-code
```

Open Docker Desktop once from Applications and wait until it reports that the
Docker engine is running. Verify the installation:

```bash
git --version
docker --version
docker compose version
code --version
```

### Clone and start

```bash
git clone <repository-url>
cd copart
cp .env.example .env
code .
docker compose up -d --build
docker compose ps
```

Replace `<repository-url>` with this repository's HTTPS or SSH clone URL. Open
[http://localhost:5173](http://localhost:5173) after all three services are up.

## Windows 10/11

Docker Desktop uses WSL 2. Open **PowerShell as Administrator** and run:

```powershell
wsl --install
winget install --id Git.Git -e
winget install --id Docker.DockerDesktop -e
winget install --id Microsoft.VisualStudioCode -e
```

Restart Windows if `wsl --install` requests it. Open Docker Desktop and wait
until the Docker engine is running. Open a new PowerShell window and verify:

```powershell
git --version
docker --version
docker compose version
code --version
```

Clone and start the project from PowerShell:

```powershell
git clone <repository-url>
Set-Location copart
Copy-Item .env.example .env
code .
docker compose up -d --build
docker compose ps
```

Replace `<repository-url>` with this repository's HTTPS or SSH clone URL. Open
[http://localhost:5173](http://localhost:5173) after all three services are up.

## Linux

The commands below target Ubuntu/Debian. For another distribution, follow the
[official Docker Engine installation guide](https://docs.docker.com/engine/install/)
and install Git and VS Code with that distribution's package manager.

### Install Git and Docker on Ubuntu/Debian

```bash
sudo apt update
sudo apt install -y git ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
. /etc/os-release
sudo curl -fsSL "https://download.docker.com/linux/$ID/gpg" -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$ID $VERSION_CODENAME stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Log out and back in after the `usermod` command. Start Docker and verify it:

```bash
sudo systemctl enable --now docker
git --version
docker --version
docker compose version
```

Install VS Code using the instructions on the
[official VS Code Linux page](https://code.visualstudio.com/docs/setup/linux),
or skip `code .` below and open the cloned folder from your editor.

### Clone and start

```bash
git clone <repository-url>
cd copart
cp .env.example .env
code .
docker compose up -d --build
docker compose ps
```

Replace `<repository-url>` with this repository's HTTPS or SSH clone URL. Open
[http://localhost:5173](http://localhost:5173) after all three services are up.

## Everyday Docker commands

Run these commands from the repository root (the folder containing
`docker-compose.yml`). They are the same on macOS, Windows PowerShell, and Linux.

```bash
# Start the application in the background
docker compose up -d

# Rebuild after dependency or Dockerfile changes
docker compose up -d --build

# Check service status
docker compose ps

# Follow logs from all services
docker compose logs -f

# Follow one service only
docker compose logs -f backend

# Stop and remove containers while keeping database data
docker compose down

# Stop and delete the local database volume (destructive)
docker compose down -v
```

Application addresses:

- Web application: [http://localhost:5173](http://localhost:5173)
- Backend API: [http://localhost:8080](http://localhost:8080)
- PostgreSQL: internal Docker network only; it is not exposed to the host

The first build can take several minutes while Docker downloads images and
dependencies. The database schema is created automatically by Flyway, and demo
users are inserted when the employee table is empty.

## Open and run from VS Code

Open the repository folder, not the `frontend` or `backend` folder by itself:

```bash
code .
```

Open **Terminal > New Terminal** in VS Code and run:

```bash
docker compose up -d --build
docker compose logs -f
```

Press `Ctrl+C` to stop following logs; the containers continue running. Use
`docker compose down` when you want to stop the application.

Helpful optional VS Code extensions:

- Extension Pack for Java (`vscjava.vscode-java-pack`)
- Spring Boot Extension Pack (`vmware.vscode-boot-dev-pack`)
- Docker (`ms-azuretools.vscode-docker`)
- ESLint (`dbaeumer.vscode-eslint`)

Install them from a terminal if desired:

```bash
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-boot-dev-pack
code --install-extension ms-azuretools.vscode-docker
code --install-extension dbaeumer.vscode-eslint
```

## Native development without Docker

Use this only when you want the frontend, backend, and database to run directly
on your computer. Verify every required tool first:

```bash
git --version
java -version
mvn -version
node --version
npm --version
psql --version
```

Expected major versions are Java 21, Maven 3.9+, Node.js 20+ (22 recommended),
and PostgreSQL 16.

### Native dependency installation

macOS with Homebrew:

```bash
brew install openjdk@21 maven node@22 postgresql@16
brew services start postgresql@16
```

Windows PowerShell with winget:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Apache.Maven -e
winget install --id OpenJS.NodeJS.LTS -e
winget install --id PostgreSQL.PostgreSQL.16 -e
```

Ubuntu/Debian (Node.js from the distribution must be version 20 or newer):

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven postgresql postgresql-contrib nodejs npm
```

If `node --version` is below 20, install Node.js 22 LTS using
[NodeSource](https://github.com/nodesource/distributions) or
[nvm](https://github.com/nvm-sh/nvm).

### Create the local database

macOS:

```bash
createuser --createdb employees
createdb --owner=employees employees
psql -d postgres -c "ALTER USER employees WITH PASSWORD 'employees';"
```

Linux:

```bash
sudo systemctl enable --now postgresql
sudo -u postgres psql -c "CREATE USER employees WITH PASSWORD 'employees';"
sudo -u postgres psql -c "CREATE DATABASE employees OWNER employees;"
```

Windows PowerShell (enter the PostgreSQL administrator password when prompted):

```powershell
psql -U postgres -c "CREATE USER employees WITH PASSWORD 'employees';"
psql -U postgres -c "CREATE DATABASE employees OWNER employees;"
```

If the user or database already exists, PostgreSQL will report that fact; do
not recreate it.

### Start the backend and frontend

From the repository root, open the first terminal:

```bash
cd backend
JWT_SECRET='replace-with-a-random-secret-of-at-least-32-characters' \
SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run
```

Leave it running. Open a second terminal at the repository root:

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` calls
to the backend at `http://localhost:8080`.

## Environment configuration

Copy `.env.example` to `.env` before starting Docker. The available variables
are:

- `POSTGRES_DB`: database name
- `POSTGRES_USER`: database username
- `POSTGRES_PASSWORD`: database password
- `JWT_SECRET`: required signing secret; use a long random value and inject it from a secret store outside local development
- `SPRING_PROFILES_ACTIVE=demo`: explicitly enables predictable local demo users; never enable it in production
- `BUSINESS_ZONE`: timezone used for leave-policy dates (defaults to `America/Indiana/Indianapolis`)

For native backend development, equivalent environment variables are
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS`. Optional hardening controls include `DB_CONNECTION_TIMEOUT_MS`, `DB_LOCK_TIMEOUT_MS`, `LOGIN_MAX_FAILURES`, `LOGIN_FAILURE_WINDOW_SECONDS`, and `MAX_REQUEST_BODY_BYTES`.

## Verify the installation

With Docker running:

```bash
docker compose ps
docker compose logs --tail=100 backend
```

All three containers should be running, and the backend log should show that
the application started successfully. Then sign in at
[http://localhost:5173](http://localhost:5173) using:

```text
Email: admin@example.com
Password: Welcome123!
```

## Tests and production builds

Native tools:

```bash
cd backend
mvn test

cd ../frontend
npm install
npm run lint
npm run build
```

Docker users can validate the complete image build with:

```bash
docker compose build
```

## Troubleshooting

### `docker: command not found`

Install Docker Desktop/Docker Engine, start it, and open a new terminal.

### `Cannot connect to the Docker daemon`

Start Docker Desktop on macOS/Windows. On Linux run
`sudo systemctl start docker`; if permission is denied, log out and back in
after adding your user to the `docker` group.

### Port 5173 or 8080 is already in use

Stop the application already using that port, or identify the process with:

```bash
# macOS/Linux
lsof -i :5173
lsof -i :8080
```

```powershell
# Windows PowerShell
Get-NetTCPConnection -LocalPort 5173,8080 -ErrorAction SilentlyContinue
```

### The page does not open immediately

Check container state and backend logs:

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 frontend
```

Wait for the backend to finish migrations, then refresh the browser.

### Reset local demo data

This permanently removes the local Docker database and recreates seed data:

```bash
docker compose down -v
docker compose up -d --build
```

### Windows cannot find `psql`, `mvn`, or `code`

Close and reopen PowerShell after installation. If the command is still absent,
add the program's `bin` directory to the Windows `PATH`, or use the recommended
Docker workflow, which does not require `psql` or Maven.
