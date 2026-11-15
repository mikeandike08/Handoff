# Handoff
An application that allows you to transfer files locally between devices, as long as you're on the same network.

## Running

Requires Java 25.

    cd handoff
    ./mvnw spring-boot:run

API docs (Swagger UI) are available at `http://localhost:8080/swagger-ui.html` once the app is running.

## API

### Devices

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

### File transfer

Called by the frontend:

| Method | Endpoint                             | Body                                      | Description                                        |
|--------|--------------------------------------|-------------------------------------------|----------------------------------------------------|
| POST   | `/api/transfer/start`                | JSON `{ deviceId, fileName, fileSize }`   | Offer a file to a peer. Returns right away in `PENDING` |
| GET    | `/api/transfer`                      | –                                         | All transfers this device knows about, newest first |
| GET    | `/api/transfer/incoming`             | –                                         | Offers waiting for this user's decision (poll this) |
| POST   | `/api/transfer/{transferId}/respond` | JSON `{ accepted }`                       | Accept or decline an incoming offer                |
| POST   | `/api/transfer/{transferId}/send`    | multipart `file`                          | Upload the file for an accepted outgoing transfer  |
| GET    | `/api/transfer/{transferId}/status`  | –                                         | Current state of a transfer                        |

Called by another device's backend (not for the browser):

| Method | Endpoint                                  | Description                                          |
|--------|-------------------------------------------|------------------------------------------------------|
| POST   | `/api/peer/transfer/request`              | Receive an offer and wait for the local user's decision |
| POST   | `/api/peer/transfer/{transferId}/upload`  | Receive the file bytes                               |

Transfer endpoints return a `TransferView`:

    {
      "transferId": "3f2b…",
      "peerDeviceId": "a8k2xq",
      "peerAlias": "Soggy Waffle",
      "peerNumber": 4070,
      "fileName": "photo.jpg",
      "fileSize": 204800,
      "state": "FAILED",
      "bytesTransferred": 0,
      "outgoing": true,
      "error": "Soggy Waffle went offline",
      "createdAt": "2026-11-14T15:02:11Z"
    }

`peerAlias` and `peerNumber` are the other device's name, so an incoming offer can show who it's
from without looking it up in `/api/devices`. `error` explains a `FAILED`, `EXPIRED` or
automatically `DECLINED` transfer, and is `null` otherwise.

### Errors

All errors use the same shape:

    { "timestamp": "...", "status": 409, "error": "Conflict", "message": "..." }

| Status | When                                                                                      |
|--------|-------------------------------------------------------------------------------------------|
| 400    | Invalid input: missing or invalid JSON, a missing field, a missing `file` part, or a file whose size doesn't match the offer. `message` says what's wrong |
| 404    | Unknown endpoint or transfer ID, or the peer is no longer on the network                  |
| 405    | Wrong HTTP method for the endpoint                                                        |
| 409    | Transfer isn't in the right state (e.g. `/send` before it was accepted, or `/respond` after the offer expired or was answered) |
| 413    | `fileSize` is over the 10 GB limit (checked at `/start`, before the receiver sees the offer) |
| 415    | Wrong or missing `Content-Type` (JSON endpoints need `application/json`, `/send` needs `multipart/form-data`) |
| 502    | The peer couldn't be reached or rejected the upload. The transfer is marked `FAILED`     |
| 500    | Unexpected error on this device                                                           |

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

## File transfer

A transfer takes two steps: the sender asks permission, then uploads the file once the receiver accepts.

1. **Sender UI** calls `POST /api/transfer/start` with the peer's `id`, the file name and size.
   It returns right away with the transfer in `PENDING`, and the offer is sent to the peer in the background.
2. **Receiver UI** polls `GET /api/transfer/incoming`, shows the offer to the user, and calls
   `POST /api/transfer/{transferId}/respond` with `{ "accepted": true }` or `false`.
   The receiver has 60 seconds to answer.
3. **Sender UI** polls `GET /api/transfer/{transferId}/status` until the state becomes `ACCEPTED`,
   `DECLINED`, `EXPIRED` (no answer within 60 seconds) or `FAILED`.
4. If accepted, **sender UI** calls `POST /api/transfer/{transferId}/send` with the file as multipart
   field `file`. Its size must match the `fileSize` sent to `/start`. The sender's backend streams the
   file to the peer, which saves it to its download folder.
5. Either side can check progress with `GET /api/transfer/{transferId}/status`.

After a page refresh, `GET /api/transfer` lists every transfer this device still knows about, so the UI
can pick up where it left off.

### Transfer states

| State          | Meaning                                         |
|----------------|-------------------------------------------------|
| `PENDING`      | Offered, waiting for the receiver to decide     |
| `ACCEPTED`     | Accepted, file not sent yet                     |
| `TRANSFERRING` | File is being sent                              |
| `COMPLETE`     | File was saved on the receiver                  |
| `DECLINED`     | Receiver said no, or the offer was rejected automatically (`error` says why) |
| `EXPIRED`      | Receiver didn't answer within 60 seconds        |
| `FAILED`       | Something went wrong, such as the peer going offline (`error` says what) |

Transfers are kept in memory only and are cleared on restart. Finished transfers
(`COMPLETE`, `DECLINED`, `EXPIRED`, `FAILED`) are removed 5 minutes after they finish.
An `ACCEPTED` transfer whose file is never sent is marked `FAILED` after 10 minutes.

### Received files

Files are saved to `~/Downloads/Handoff` by default (see `HANDOFF_DOWNLOAD_DIR` below).
The sender's file name is cleaned before saving:
- Directory parts are removed, so `../../notes.txt` becomes `notes.txt`.
- Names like `.`, `..` or a blank name become `untitled`.

Existing files are never overwritten. If `report.zip` already exists, the new file is saved as
`report (1).zip`, then `report (2).zip`, and so on.

The maximum file size is 10 GB. Larger offers are rejected at `/start` with a `413`.

## Configuration

| Environment variable   | Default                 | Description                           |
|------------------------|-------------------------|---------------------------------------|
| `HANDOFF_PORT`         | `8080`                  | HTTP port for the API                 |
| `HANDOFF_CONFIG_DIR`   | `~/.handoff`            | Where `config.properties` is stored   |
| `HANDOFF_DOWNLOAD_DIR` | `~/Downloads/Handoff`   | Where received files are saved        |

Each device runs its own instance, so the port only needs to be unique per machine.
Two devices on the same network can both use 8080.

### Running two instances on one machine

To test discovery and transfers locally, give each instance its own port, config directory and download directory:

    cd handoff
    HANDOFF_PORT=8080 ./mvnw spring-boot:run

    cd handoff
    HANDOFF_PORT=8081 HANDOFF_CONFIG_DIR=/tmp/handoff-b HANDOFF_DOWNLOAD_DIR=/tmp/handoff-b-dl ./mvnw spring-boot:run

Then `curl localhost:8080/api/devices` should list the second instance, and files sent to it
will appear in `/tmp/handoff-b-dl`.

## Frontend

CORS is open to `http://localhost:5173` (the Vite dev server) by default. Change
`app.cors.allowed-origin` in `handoff/src/main/resources/application.properties` to use a different origin.

## Known limitations

- One file per transfer.
- `bytesTransferred` only updates when a transfer finishes, so there's no live progress bar yet.
- Transfer history is lost when the app restarts.
- Both devices need the same version of Handoff. The device-to-device messages changed in this
  version, so an older build can't send to or receive from a newer one.
