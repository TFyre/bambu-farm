# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Bambu Farm — a web application to monitor and control multiple Bambu Lab 3D printers (A1/A1 Mini/P1P/P1S/X1C) over MQTT, FTPS, and RTSP without custom firmware. Stack: Java 21, Quarkus 3, Vaadin Flow (server-side UI), Apache Camel (paho MQTT), protobuf.

Full user-facing configuration options live in [README.md](README.md) ("Full Config Options") — consult it before answering config questions.

## Modules (Maven multi-module, groupId `com.tfyre.bambu`)

- `common` — shared code: `src/main/proto/bambu.proto` (generates `com.tfyre.bambu.model.*` protobuf classes for the printer's MQTT JSON payload), `mqtt/AbstractMqttController` (Camel RouteBuilder base class that builds `paho:` MQTT and `direct:` endpoints, using a no-op trust socket factory for the printers' self-signed certs), plus JAXB XJC generation from `src/main/schema/slice-info-1.0.xsd` (for parsing `.3mf` slice info, with fluent-api/default-value plugins).
- `bambu` (artifact `bambu-web`) — the actual web application: Vaadin Flow UI, per-printer Camel MQTT routes, FTPS upload, Bambu Lab cloud support. Produces the deployable `bambu/target/bambu-web-*-runner.jar` (uber-jar, no profile needed since Vaadin 25).
- `server` (artifact `bambu-server`) — small headless Quarkus app that simulates printers: it subscribes to the MQTT request topic and, on a `pushall` request, publishes JSON from `server/src/main/resources/json/*.json` to the report topic. Used to test the web app without physical printers.

## Build & run

- Build everything (including Vaadin production frontend build): `./mvnw clean install`
- Run the web app in dev mode (hot reload, Vaadin dev server): `./mvnw -pl bambu quarkus:dev` (or `cd bambu && ./mvnw quarkus:dev` — the module's `defaultGoal` is `package quarkus:dev`)
- Run the server simulator in dev mode: `./mvnw -pl server quarkus:dev`
- Package the deployable jar: `./mvnw -pl bambu package`, then `java -jar bambu/target/bambu-web-*-runner.jar`
- Tests: `./mvnw test` (currently there are no test sources; CI still runs this)
- No linter/formatter is configured. On Windows use `mvnw.cmd` or plain `mvn`.

CI ([github-pull-reqeust.yml](.github/workflows/github-pull-reqeust.yml)) builds and runs tests on PRs/pushes to `main`; [github-release.yml](.github/workflows/github-release.yml) uploads the runner jar on release. Dependabot keeps Quarkus/Vaadin/plugin versions current — version bumps land via PRs from `TFyre/dependabot/*`.

## Git

Commits use [conventional commits](https://www.conventionalcommits.org/) with a type prefix and optional scope, e.g. `feat: BatchPrint - skip upload if file exists and is same size`, `fix: update quarkus deprecated uber jar options`, `build(deps): bump quarkus.version from 3.29.2 to 3.30.5` (Dependabot's format), `refactor: possible this-escape`. Version bumps are plain commits (`Version 1.8.0`). Match this style in commit messages.

## Runtime configuration

All runtime settings come from a `.env` file next to the jar (Quarkus auto-loads it; it is gitignored). Config is mapped by SmallRye `@ConfigMapping` interfaces named `BambuConfig` (one per module), prefix `bambu`:

- `bambu.printers.<id>.*` — device-id, access-code, ip, mqtt report/request topics, ftp, stream, per-printer options
- `bambu.users.<name>.*` — password (bcrypt) and role (`admin` | `normal`)
- `bambu.cloud.*` — connect via Bambu Lab cloud MQTT (`ssl://us.mqtt.bambulab.com:8883`) instead of LAN; `CloudService`/`CloudApi` handle this
- `bambu.preheat`, `bambu.batch-print.*`, `bambu.live-view-url`, `bambu.use-bouncy-castle`, etc. — see README

Adding a new `bambu.*` option means updating the corresponding `@ConfigMapping` interface. Dev default login is `admin`/`admin`; `bambu.auto-login=true` skips the login view.

## Architecture

### Printer communication (MQTT via Camel)

Each printer has two MQTT topics: `device/<device-id>/report` (printer → app) and `device/<device-id>/request` (app → printer). `CamelController` (in both `bambu` and `server` modules) is a `@Startup` Camel RouteBuilder that creates two routes per configured printer, named `producer-<name>` and `consumer-<name>`:

- producer: `direct:bambu-<name>` → `paho:` request topic
- consumer: `paho:` report topic → processor (`BambuPrinterImpl` in the web app, `BambuPrinterProcessor` in the simulator)

Routes are created with `autoStartup(false)`; `BambuPrintersImpl.startPrinter()/stopPrinter()` start and stop a printer's route group at runtime (the UI can stop/start printers). Printer payloads are JSON parsed into the generated protobuf `BambuMessage` (and sub-messages like `Print`, `Tray`) via `JsonFormat` with `preservingProtoFieldNames`/`ignoringUnknownFields` — if the printer sends an unrecognized field, add it to `common/src/main/proto/bambu.proto` rather than hand-writing JSON parsing.

`BambuPrinter` is the per-printer abstraction; `BambuPrinterImpl` (one `@Dependent` instance per printer, managed by `BambuPrintersImpl`) holds the latest status/fullStatus, a bounded queue of recent messages, sends commands (gcode, speed, light, filament, etc.) through a Camel `ProducerTemplate` to the printer's `direct:` endpoint, and polls full status every minute via a Quarkus scheduled job. The UI reads printer state via `BambuPrinters.getPrinters()` and polls it (`bambu.refresh-interval`, default 1s).

### UI (Vaadin Flow)

Server-side Vaadin Flow with a custom theme in `bambu/src/main/frontend/themes/bambu-theme/` (`bambu.css`). `bambu/src/main/frontend/generated/` and `node_modules/` are build output (gitignored). Routes (layout `MainLayout`): Dashboard (`""`), `printer`, `batchprint`, `sdcard`, `filament`, `logs`, `maintenance`, plus a standalone `LoginView`. The dashboard renders each printer from `BambuPrinter` state; most views are `@Route` classes in `com.tfyre.bambu.view`. Security is Quarkus Elytron with a custom `TFyreIdentityProvider`/`TFyreIdentityManager` (users from `bambu.users.*`) and `NavigationAccessCheckerInitializer` enforcing roles.

### FTPS and camera

`com.tfyre.ftp.BambuFtp` extends commons-net `FTPSClient` and adds Bouncy Castle SSL session reuse when `bambu.use-bouncy-castle=true` (required for SD-card features on the X1C). Camera feed: per-printer `stream.url` (RTSP via an external mediaMTX container — see `docker/bambu-liveview/`) or an iframe embed.

## Local development

- `.env` is gitignored — you need a real device-id/access-code/IP to talk to a printer, or run the `server` simulator (its `src/main/resources/json/*.json` fixtures, e.g. `fullstatus.json`, are responses keyed by resource name).
- `docker/bambu-local-dev/` provides a local vsftpd FTPS server (`docker compose up`) for testing uploads without a printer.
- Debug logging: set `quarkus.log.category."com.tfyre".level=DEBUG` (or TRACE) in `.env`; see the README "Debug" section.
- Untracked files at the repo root (`bambu-web-*-runner.jar`, `wimpie.json`) and `server/src/main/resources/json/fullstatus-tfyre*.json` are local artifacts/fixtures, not part of the build.
