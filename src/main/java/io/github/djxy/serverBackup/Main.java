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
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Samuel on 2016-02-07.
 */
@Plugin(id = "serverbackup", name = "Server Backup", version = "1.3")
public class Main {

    @Inject
    @DefaultConfig(sharedRoot = false)
    private Path defaultFile;
    private Path backupFolder;

    @Inject
    private Logger logger;

    private final ArrayList<Pattern> patternFileNotAccepted = new ArrayList<>();

    @Listener
    public void onInitializationEvent(GameInitializationEvent event) {
        initFiles();

        Sponge.getGame().getCommandManager().register(this, CommandSpec
                .builder()
                .child(CommandSpec
                        .builder()
                        .executor(new CommandExecutor() {
                            @Override
                            public CommandResult execute(CommandSource commandSource, CommandContext commandContext) throws CommandException {
                                if (commandSource instanceof ConsoleSource)
                                    createBackUp();

                                return CommandResult.success();
                            }
                        })
                        .build(), "backup")
                .child(CommandSpec
                        .builder()
                        .executor(new CommandExecutor() {
                            @Override
                            public CommandResult execute(CommandSource commandSource, CommandContext commandContext) throws CommandException {
                                if (commandSource instanceof ConsoleSource && commandContext.getOne("minute").isPresent())
                                    deleteFilesOlder((Integer) commandContext.getOne("minute").get());

                                return CommandResult.success();
                            }
                        })
                        .arguments(GenericArguments.onlyOne(GenericArguments.integer(Text.of("minute"))))
                        .build(), "delete")
                .build(), "serverBackup", "sb");
    }

    private void deleteFilesOlder(int minutes){
        List<File> filesToDelete = new ArrayList<>();

        for (File file : backupFolder.toFile().listFiles()){
            try {
                BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                long difference = System.currentTimeMillis() - attr.creationTime().toMillis();

                if(minutes*60*1000 <= difference)
                    filesToDelete.add(file);
            } catch (Exception e) {}
        }

        for(File file : filesToDelete)
            file.delete();

        logger.info(filesToDelete.size()+" file(s) deleted.");
    }

    private void initFiles(){
        backupFolder = defaultFile.getParent().resolve("backups");
        File config = defaultFile.toFile();

        backupFolder.toFile().mkdirs();

        if(!config.exists()){
            try {
                config.createNewFile();

                FileOutputStream fos = new FileOutputStream(config);
                fos.write("config\\\\server_backup\\\\backups".getBytes());

                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            BufferedReader bf = new BufferedReader(new FileReader(config));
            String line;

            while((line = bf.readLine()) != null) {
                line = line.trim();

                if (line.length() != 0)
                    patternFileNotAccepted.add(Pattern.compile(line));
            }

            bf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createBackUp(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                File zip = backupFolder.resolve(createBackupName()).toFile();

                try {
                    logger.info("Server backup started.");
                    zip.createNewFile();
                    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));

                    putFileInZip(new File("."), zos);

                    zos.close();
                    logger.info("Server backup finished.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void putFileInZip(File file, ZipOutputStream zos) throws Exception {
        if(file.isDirectory()) {
            for (File f : file.listFiles()) {
                putFileInZip(f, zos);
            }
        }
        else if(isFileValid(file)) {
            try{

                FileInputStream fis = new FileInputStream(file);
                int i = fis.read();

                zos.putNextEntry(new ZipEntry(getFileName(file)));

                zos.write(i);

                while((i = fis.read()) != -1)
                    zos.write(i);

                fis.close();
                zos.closeEntry();
            }catch (Exception e){
                logger.error("Can't put "+file.getAbsolutePath()+" in the backup.");
            }
        }
    }

    private boolean isFileValid(File file){
        for(Pattern pattern : patternFileNotAccepted)
            if(pattern.matcher(file.getPath()).find())
                return false;

        return true;
    }

    private String createBackupName(){
        LocalDateTime now = LocalDateTime.now();
        String month = now.getMonth().getValue() < 10?"0"+now.getMonth().getValue():now.getMonth().getValue()+"";
        String day = now.getDayOfMonth() < 10?"0"+now.getDayOfMonth():now.getDayOfMonth()+"";
        String hour = now.getHour() < 10?"0"+now.getHour():now.getHour()+"";
        String minute = now.getMinute() < 10?"0"+now.getMinute():now.getMinute()+"";
        String second = now.getSecond() < 10?"0"+now.getSecond():now.getSecond()+"";

        return now.getYear()+"-"+month+"-"+day+"_"+hour+"-"+minute+"-"+second+".zip";
    }

    private String getFileName(File file){
        return file.getPath().substring(file.getPath().indexOf(File.separator) + 1);
    }
}