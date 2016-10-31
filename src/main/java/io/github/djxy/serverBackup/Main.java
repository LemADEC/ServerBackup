/*
 * Copyright (c) 2016 Samuel Marchildon-Lavoie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.djxy.serverBackup;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Plugin(id = "serverbackup", name = "Server Backup", version = "1.4", description = "Backup your server with ease")
public class Main {
	
	@Inject
	@DefaultConfig(sharedRoot = false)
	private Path defaultFile;
	private Path backupFolder;
	
	@Inject
	private Logger logger;
	
	private static final AtomicBoolean isOngoing = new AtomicBoolean(false);
	
	private final ArrayList<Pattern> patternFileNotAccepted = new ArrayList<>();
	
	@Listener
	public void onInitializationEvent(GameInitializationEvent event) {
		initFiles();
		
		Sponge.getGame().getCommandManager().register(this, CommandSpec
			.builder()
			.child(CommandSpec
				.builder()
				.executor((commandSource, commandContext) -> {
					createBackup();
					
					return CommandResult.success();
				})
				.description(Text.of("Triggers an automatic backup"))
				.permission("serverbackup.backup")
				.build(), "backup")
			.child(CommandSpec
				.builder()
				.executor((commandSource, commandContext) -> {
					if (commandSource instanceof ConsoleSource) {
						Optional<Object> optional = commandContext.getOne("hours");
						if (optional.isPresent()) {
							deleteFilesOlder((Integer) optional.get());
						}
					} else {
						commandSource.sendMessage(Text.builder("This is a console command").color(TextColors.RED).build());
					}
					
					return CommandResult.success();
				})
				.arguments(GenericArguments.onlyOne(GenericArguments.integer(Text.of("hours"))))
				.description(Text.of("Triggers an automatic purge of backup files older than specified hours"))
				.permission("serverbackup.delete")
				.build(), "delete")
			.build(), "serverBackup", "sb");
		
		isOngoing.set(false);
	}
	
	private void deleteFilesOlder(int hours) {
		List<File> filesToDelete = new ArrayList<>();
		File[] files = backupFolder.toFile().listFiles();
		
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				continue;
			}
			try {
				BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
				long difference = System.currentTimeMillis() - attr.creationTime().toMillis();
				
				if (hours * 60 * 60 * 1000 <= difference) {
					filesToDelete.add(file);
				}
			} catch (Exception exception) {
				exception.printStackTrace();
				logger.error("Failed to check file for deletion: " + file.getPath());
			}
		}
		
		filesToDelete.forEach(File::delete);
		
		Sponge.getServer().getBroadcastChannel().send(Text.builder("Automatic purge completed...").color(TextColors.GRAY).build());
		logger.info(filesToDelete.size() + " file(s) deleted as they were older than " + hours + " hours.");
	}
	
	private void initFiles() {
		backupFolder = defaultFile.getParent().resolve("backups");
		File fileConfig = defaultFile.toFile();
		
		//noinspection ResultOfMethodCallIgnored
		backupFolder.toFile().mkdirs();
		
		if (!fileConfig.exists()) {
			try {
				if (fileConfig.createNewFile()) {
					FileOutputStream fileOutputStream = new FileOutputStream(fileConfig);
					fileOutputStream.write("\\./backups/".getBytes());
					fileOutputStream.write("\\./config/serverbackup/backups".getBytes());
					fileOutputStream.write("\\./crash-reports/".getBytes());
					fileOutputStream.write("\\./dumps/".getBytes());
					fileOutputStream.write("\\./logs/".getBytes());
					fileOutputStream.write("\\./libraries/".getBytes());
					fileOutputStream.write("\\./mods/".getBytes());
					fileOutputStream.write("\\./plugins/".getBytes());
					fileOutputStream.write("\\./stall-reports/".getBytes());
					fileOutputStream.write("\\./startup.nps".getBytes());
					fileOutputStream.write("\\./dynmap/".getBytes());
					fileOutputStream.write("\\./.*\\.jar".getBytes());
					fileOutputStream.write("\\./.*\\.zip".getBytes());
					fileOutputStream.write("\\./_.*/".getBytes());
					fileOutputStream.write("\\./z/".getBytes());
					fileOutputStream.close();
				}
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		}
		
		try {
			BufferedReader bufferedReader = new BufferedReader(new FileReader(fileConfig));
			String line;
			
			while ((line = bufferedReader.readLine()) != null) {
				line = line.trim();
				
				if (line.length() != 0) {
					patternFileNotAccepted.add(Pattern.compile(line));
				}
			}
			
			bufferedReader.close();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}
	
	private void createBackup() {
		new Thread(() -> {
			if (!isOngoing.compareAndSet(false, true)) {
				Sponge.getServer().getBroadcastChannel().send(Text.builder("Automatic backup is already in progress").color(TextColors.RED).build());
				logger.warn("A server backup is already in progress, ignoring new backup request");
				return;
			}
			
			File fileZIP = backupFolder.resolve(createBackupName()).toFile();
			
			try {
				Sponge.getServer().getBroadcastChannel().send(Text.builder("Automatic backup starting...").color(TextColors.GRAY).build());
				logger.info("Server backup started.");
				
				//noinspection ResultOfMethodCallIgnored
				fileZIP.createNewFile();
				ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(fileZIP));
				
				putFileInZip(new File("."), zipOutputStream);
				
				zipOutputStream.close();
				
				Sponge.getServer().getBroadcastChannel().send(Text.builder("Automatic backup completed").color(TextColors.GRAY).build());
				logger.info("Server backup finished.");
			} catch (Exception exception) {
				exception.printStackTrace();
				logger.info("Server backup failed.");
			}
			
			isOngoing.set(false);
		}).start();
	}
	
	private static final byte[] bytes = new byte[4 * 1024 * 1024];
	private void putFileInZip(File fileToAdd, ZipOutputStream zipOutputStream) {
		if (fileToAdd.isDirectory()) {
			File[] files = fileToAdd.listFiles();
			if (files != null) {
				for (File file : files) {
					if (isFileValid(file)) {
						putFileInZip(file, zipOutputStream);
					}
				}
			}
		} else {
			try {
				FileInputStream fileInputStream = new FileInputStream(fileToAdd);
				
				int length = fileInputStream.read(bytes);
				
				if (length > 0) {
					zipOutputStream.putNextEntry(new ZipEntry(getFileName(fileToAdd)));
					
					while (length > 0) {
						zipOutputStream.write(bytes, 0, length);
						length = fileInputStream.read(bytes);
					}
					
					fileInputStream.close();
					zipOutputStream.closeEntry();
				}
			} catch (Exception exception) {
				logger.error("Can't put " + fileToAdd.getPath() + " in the backup.");
			}
		}
	}
	
	private boolean isFileValid(File file) {
		String path = file.getPath().replace('\\', '/');
		if (file.isDirectory()) {
			path += "/";
		}
		for (Pattern pattern : patternFileNotAccepted) {
			if (pattern.matcher(path).find()) {
				// logger.info("Rejecting '" + path + "' due to '" + pattern.pattern() + "'");
				return false;
			}
		}
		// logger.info("Adding '" + path + "'");
		return true;
	}
	
	private String createBackupName() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd_HH'h'mm'm'ss'.zip'");
		return sdfDate.format(new Date());
	}
	
	private String getFileName(File file) {
		return file.getPath().substring(file.getPath().indexOf(File.separator) + 1);
	}
}