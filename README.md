# Handoff
An application that allows you to transfer files locally between devices, as long as you're on the same network.

## Running

Requires Java 25.

    cd handoff
    ./mvnw spring-boot:run

API docs (Swagger UI) are available at `http://localhost:8080/swagger-ui.html` once the app is running.

## API

| Method | Endpoint        | Description                                      |
|--------|-----------------|--------------------------------------------------|
| GET    | `/api/identity` | This device's identity                           |
| GET    | `/api/devices`  | Other Handoff devices currently on the network   |

`GET /api/identity`

    { "alias": "Caffeinated Walrus", "number": 1234, "id": "a8k2xq" }

`GET /api/devices`

    {
      "data": [
        { "alias": "Soggy Narwhal", "number": 42, "id": "k3m9zp" }
      ]
    }

Devices are sorted alphabetically by `alias` (case-insensitive), with `number` as a tiebreaker.
This device is never included in its own list.

## Device identity

Each device has three identifying fields:

| Field    | Example              | Notes                                                        |
|----------|----------------------|--------------------------------------------------------------|
| `alias`  | `Caffeinated Walrus` | Random adjective + noun, meant for display                    |
| `number` | `1234`               | Random number from 1 to 4999, no leading zeros                |
| `id`     | `a8k2xq`             | 6 lowercase alphanumeric characters, used to tell devices apart |

On first run, Handoff generates the identity and writes it to `~/.handoff/config.properties`.
Later runs read from that file, so a device keeps the same identity across restarts and peers
see a stable name rather than a new one each session.

A new identity is generated if the config file is deleted, can't be read, is missing a field,
or was written by an older config version. To get a new alias, delete the file and restart.

## Device discovery

Each running instance announces itself to the local network every ~1.5 seconds over UDP
multicast (`239.255.1.1:4445`) and listens for announcements from other instances.
A device that hasn't been heard from for 6 seconds is dropped from `/api/devices`.

Both devices must be on the same network, and that network must allow multicast traffic.
Some guest or corporate Wi-Fi networks block it.

## Configuration

| Environment variable  | Default        | Description                           |
|-----------------------|----------------|---------------------------------------|
| `HANDOFF_PORT`        | `8080`         | HTTP port for the API                 |
| `HANDOFF_CONFIG_DIR`  | `~/.handoff`   | Where `config.properties` is stored   |

Each device runs its own instance, so the port only needs to be unique per machine.
Two devices on the same network can both use 8080.

### Running two instances on one machine

To test discovery locally, give each instance its own port and config directory:

    cd handoff
    HANDOFF_PORT=8080 ./mvnw spring-boot:run

    cd handoff
    HANDOFF_PORT=8081 HANDOFF_CONFIG_DIR=/tmp/handoff-b ./mvnw spring-boot:run

Then `curl localhost:8080/api/devices` should list the second instance.

## Frontend

CORS is open to `http://localhost:5173` (the Vite dev server) by default. Change
`app.cors.allowed-origin` in `handoff/src/main/resources/application.properties` to use a different origin.
