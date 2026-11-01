# Jalebi Commands

Whitelist the commands normal players can use and tab complete.

- Only commands in `allowed-commands` (config.yml) work for non-OP players.
- Everything else is hidden from tab complete.
- Typing anything else shows `Invalid command.` (message is configurable).
- OPs (and players with `jalebicommands.bypass`) are never restricted.

## Build
Requires Java 17+ and Maven:

    mvn clean package

The jar is created at `target/JalebiCommands.jar`. Put it in your server's `plugins/` folder.
Works on Spigot / Paper 1.13+ (built against 1.20.4 API).

## Admin command (`/jalebicommands` or `/jc`)
- `/jc reload` - reload config
- `/jc list` - show allowed commands
- `/jc add <command>` / `/jc remove <command>`
