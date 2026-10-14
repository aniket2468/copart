# Repeatable verification

Run from the repository root. Requires Java 21, Maven, Node.js, and Docker.
The `copartverify` project has a dedicated PostgreSQL volume and loopback ports
18080 (API) and 15173 (web). None of these commands use the normal Compose stack.

```powershell
Push-Location backend
mvn clean test
Pop-Location
Push-Location frontend
npm ci
npm run lint
npm run build
npm audit --omit=dev
npx playwright install chromium
Pop-Location

docker compose -p copartverify -f scripts/compose.verify.yml up -d --build
node scripts/verify-api.mjs
node scripts/verify-deployment.mjs
Push-Location frontend
$env:E2E_BASE_URL = 'http://localhost:15173'
npm run test:e2e
Remove-Item Env:E2E_BASE_URL
Pop-Location
./scripts/scan-images.ps1
docker compose -p copartverify -f scripts/compose.verify.yml down -v
```

The API harness expects a fresh verification database. Before repeating the full
run, remove **only this project's** disposable stack with the final command and
start it again. API failure injection stops its database, so run the API,
deployment, and browser suites **sequentially**. Upgrade checks create separate
V3/V4/V5 databases inside that same disposable PostgreSQL container.

The browser suite includes deterministic failure and race tests with intercepted
API responses, plus a real employee/manager approval workflow against the Docker
stack. `npm run test:e2e` without `E2E_BASE_URL` starts Vite and runs the intercepted
tests; it explicitly skips the real database workflow.

Image scanning uses local Docker image archives and Trivy with telemetry disabled.
It downloads public vulnerability databases, writes JSON reports under `reports/`,
and fails on high/critical findings. Image archives are removed after each scan.
The scanner cache remains in `copartverify_trivy_cache` for subsequent runs.
