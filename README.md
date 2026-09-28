# FlimsyArmor (Paper 1.21.11)

`/startflimsyarmor [minutes]` – starts the event (default 15, e.g. `30` or any custom number).
`/stopflimsyarmor` – ends it early.
Permission: `flimsyarmor.admin` (op by default).

While active, Unbreaking has no effect on armor. Edit `plugins/FlimsyArmor/config.yml` to change messages, default/max time, reminders and the boss bar.

Build: push to GitHub → Actions → "Build plugin" → download the `FlimsyArmor` artifact (the jar).
Local build: `mvn package` (Java 21).
