# HynseBackup

HynseBackup is an easy-to-use auto backup plugin for Minecraft servers.

## Features

- Automatic backups: Enable automatic backups at a specified interval. Backups can be scheduled to occur periodically after the server starts.
- Whitelisted worlds: Specify a list of worlds to include in the backups.
- Maximum backup count: Limit the number of backups to keep. Older backups will be automatically deleted when the maximum count is reached.
- Compression options: Choose between different compression modes - zip, zstd, or zstd_experimental. Adjust compression levels and enable progress display using a boss bar.
- Backup management: Use commands to manually start backups for specific worlds or list existing backups.
- Permissions support: Configure permissions to control who can start backups and list backups.

## Installation

1. Download the HynseBackup.jar file from the [modrinth](https://modrinth.com/plugin/hynsebackup).
2. Place the HynseBackup.jar file in your server's `plugins` folder.
3. Start your server. The plugin will automatically generate the default configuration file.

## Configuration

The HynseBackup plugin can be configured through the `config.yml` file located in the plugin's folder. Below is an example configuration:

```yaml
whitelist-worlds:
  - world
  - world_nether
  - world_the_end

compression:
  level: 3
print-progress: true

auto-intervention:
  enabled: true
  interval: 86400

max-backup:
  enabled: true
  count: 3
```
## Commands
- `/backup start <world>`: Starts a backup for the specified world.
- `/backup reload`: Reload config.
## Permissions
- `hynsebackup.start`: Allows the player to start a backup.
- `hynsebackup.reload`: Reload config.
- `hynsebackup.use`: Allows access to all HynseBackup commands

## Contributing
Contributions to the HynseBackup plugin are welcome! If you would like to contribute

## License
This plugin is licensed under the MIT license. See [LICENSE.md](https://github.com/MidnightTale/HynseBackup/blob/master/LICENSE.md) for more information.
