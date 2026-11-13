# Handoff
An application that allows you to transfer files locally between devices, as long as you're on the same network.

## Restart behavior

On first run, Handoff generates a random `alias` and `device ID` and writes them to
`~/.handoff/config.properties`. Subsequent runs read from that file, so a device
keeps the same identity across restarts — peers see a stable name rather than a
new one each session.

Delete the config file to get a new alias.

### Note
`device ID` is a range from `(1, 9999)`, this number will <b>not</b> have leading zeros (e.g can be `24`, `123`, or `1123`)

## Port

Defaults to 8080. Override with the `HANDOFF_PORT` environment variable:

    HANDOFF_PORT=8081 cd handoff ./mvnw spring-boot:run

Each device runs its own instance, so the port only needs to be unique per
machine — two devices on the same network can both use 8080.
