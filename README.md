#ServerBackup
ServerBackup is a simple plugin that create backups for your server. There is no permission, only one command you have to execute in the console. You can set which files or folders you want to save in your backup with the [java patterns](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). There is a default config created when you load the plugin for the first time with the patterns you should use to fully backup your server. So if you don't know the java patterns, you won't have any configuration to do.

How to use this plugin
---
Write this command in the console `/sb backup` or `/serverBackup backup` and that will create a backup of your server. If you want to set this command on a schedule, you could use my plugin [CommandScheduler](https://github.com/djxy/CommandScheduler).

To set the files you don't want in your backup, you have to set the [java patterns](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html) inside the config file (`config/serverbackup/serverbackup.conf`).

Example: the line `.jar$` will exclude all the file with extension `.jar`. 

Your config file should look like this:
```
config\\serverbackup\\backups
.jar$
```

To delete your older backups, you have to do `/sb delete <minute>` or `/serverBackup delete <minute>`. All the backups older than now minus the minutes will be deleted.

Backup
---
All the backups created are in the file `config/serverbackup/backups`. They are sorted by date.

Links
---
- Download the plugin click [here](https://github.com/djxy/ServerBackup/releases).
- [Github](https://github.com/djxy/ServerBackup)
