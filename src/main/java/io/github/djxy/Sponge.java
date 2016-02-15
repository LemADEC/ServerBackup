package io.github.djxy;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Samuel on 2016-02-07.
 */
@Plugin(id = "ServerBackup", name = "Server backup", version = "1.0")
public class Sponge implements CommandExecutor{

    @Inject
    @DefaultConfig(sharedRoot = false)
    private Path defaultFile;
    private File backupFolder;

    @Inject
    private Game game;

    @Inject
    private Logger logger;

    private final ArrayList<Pattern> patternFileNotAccepted = new ArrayList<>();

    @Listener
    public void onInitializationEvent(GameInitializationEvent event) {
        initFiles();
        CommandSpec commandBackUp = CommandSpec.builder()
                .description(Text.of("Create a backup of the server."))
                .executor(this)
                .build();

        game.getCommandManager().register(this, commandBackUp, "serverBackup", "sb");
    }

    @Override
    public CommandResult execute(CommandSource commandSource, CommandContext commandContext) throws CommandException {
        if(commandSource instanceof ConsoleSource)
            createBackUp();

        return CommandResult.success();
    }

    private void initFiles(){
        File folder = defaultFile.toFile().getParentFile();
        File config = defaultFile.toFile();
        backupFolder = new File(folder.getPath()+File.separator+"backups");

        if(!backupFolder.exists())
            backupFolder.mkdirs();

        if(!config.exists()){
            try {
                config.createNewFile();

                FileOutputStream fos = new FileOutputStream(config);
                fos.write("config\\\\serverbackup\\\\backups".getBytes());

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
                File zip = new File(backupFolder.getPath()+File.separator + createBackupName());

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
            zos.putNextEntry(new ZipEntry(getFileName(file)));

            FileInputStream fis = new FileInputStream(file);
            int i;

            while((i = fis.read()) != -1)
                zos.write(i);

            fis.close();
            zos.closeEntry();
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