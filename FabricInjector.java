import javassist.*;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Инъектор ModKaLogger для Fabric модов
 * Вшивает Fabric версию логгера в любой Fabric мод через Javassist
 */
public class FabricInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("==================================================");
            System.out.println("ModKaLogger Fabric Injector v2.1");
            System.out.println("==================================================");
            System.out.println("Usage: java FabricInjector <input.jar> <output.jar>");
            System.out.println("Example: java FabricInjector mod.jar mod_injected.jar");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        
        File inputFile = new File(inputJar);
        File modFile = new File("build/libs/modkalogger-fabric-1.0.0.jar");
        
        // Если запускаем из fabric-1.21.4, ищем JAR в текущей папке
        if (!modFile.exists()) {
            modFile = new File("fabric-1.21.4/build/libs/modkalogger-fabric-1.0.0.jar");
        }
        
        if (!inputFile.exists()) {
            System.err.println("[!] Input JAR not found: " + inputJar);
            System.exit(1);
        }
        if (!modFile.exists()) {
            System.err.println("[!] ModKaLogger Fabric JAR not found!");
            System.err.println("[!] Expected: " + modFile.getAbsolutePath());
            System.exit(1);
        }
        
        System.out.println("==================================================");
        System.out.println("ModKaLogger Fabric Injector v2.1");
        System.out.println("==================================================");
        System.out.println("[*] Input: " + inputJar);
        System.out.println("[*] ModKaLogger: " + modFile.getName());
        System.out.println("[*] Output: " + outputJar);
        System.out.println();
        
        File tempDir = new File("temp_inject_" + System.currentTimeMillis());
        tempDir.mkdir();
        
        try {
            // 1. Распаковываем целевой мод
            System.out.println("[1/5] Extracting target mod...");
            unzipJar(inputJar, tempDir.getAbsolutePath());
            System.out.println("      Done");
            
            // 2. Находим entrypoint класс
            System.out.println("[2/5] Finding entrypoint class...");
            String entrypointClass = findEntrypointClass(tempDir);
            if (entrypointClass == null) {
                System.out.println("      No entrypoint found, will create new");
            } else {
                System.out.println("      Found: " + entrypointClass);
            }
            
            // 3. Добавляем классы ModKaLogger (БЕЗ перезаписи fabric.mod.json!)
            System.out.println("[3/5] Adding ModKaLogger classes...");
            addModKaLoggerClasses(modFile.getAbsolutePath(), tempDir.getAbsolutePath());
            System.out.println("      Done");
            
            // 4. Инжектим вызов через Javassist (только если есть entrypoint)
            System.out.println("[4/5] Injecting initialization...");
            if (entrypointClass != null) {
                injectWithJavassist(tempDir, entrypointClass);
            } else {
                System.out.println("      No entrypoint found - will add via fabric.mod.json");
            }
            System.out.println("      Done");
            
            // 5. Обновляем fabric.mod.json (всегда добавляем ModKaLoggerClient entrypoint)
            System.out.println("[5/5] Updating fabric.mod.json...");
            updateFabricModJson(tempDir);
            System.out.println("      Done");
            
            // 6. Собираем JAR
            System.out.println("[6/6] Creating output JAR...");
            createJar(tempDir, outputJar);
            System.out.println("      Done: " + outputJar);
            
            // Очистка
            deleteDirectory(tempDir);
            
            System.out.println();
            System.out.println("==================================================");
            System.out.println("[+] SUCCESS!");
            System.out.println("[+] ModKaLogger injected into Fabric mod!");
            System.out.println("==================================================");
            
        } catch (Exception e) {
            System.err.println("[!] ERROR: " + e.getMessage());
            e.printStackTrace();
            deleteDirectory(tempDir);
            System.exit(1);
        }
    }
    
    /**
     * Добавляет классы ModKaLogger, НЕ перезаписывая файлы целевого мода
     */
    private static void addModKaLoggerClasses(String jarPath, String destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Пропускаем META-INF из ModKaLogger
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                
                // Пропускаем fabric.mod.json из ModKaLogger (целевой мод важнее)
                if (name.equals("fabric.mod.json")) {
                    System.out.println("      Skipping fabric.mod.json (keeping target mod's version)");
                    continue;
                }
                
                // Пропускаем миксины - они не используются и вызывают краш
                if (name.endsWith(".mixins.json") || name.contains("mixin")) {
                    System.out.println("      Skipping mixin: " + name);
                    continue;
                }
                
                File file = new File(destDir, name);
                
                // НЕ перезаписываем существующие файлы!
                if (file.exists()) {
                    System.out.println("      Skipping existing: " + name);
                    continue;
                }
                
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
                    System.out.println("      Added: " + name);
                }
            }
        }
    }
    
    /**
     * Инжектит вызов ModKaLogger.init() в статический инициализатор класса
     */
    private static void injectWithJavassist(File tempDir, String className) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(tempDir.getAbsolutePath());
        
        CtClass targetClass = pool.get(className);
        
        // Получаем или создаём статический инициализатор (<clinit>)
        CtConstructor clinit = targetClass.getClassInitializer();
        
        if (clinit == null) {
            System.out.println("      Creating new <clinit>...");
            clinit = targetClass.makeClassInitializer();
        }
        
        // Добавляем вызов в НАЧАЛО <clinit> с обработкой ошибок
        clinit.insertBefore(
            "try { " +
            "  com.modkalogger.ModKaLogger.init(); " +
            "} catch(Throwable t) { " +
            "  System.err.println(\"[ModKaLogger] Init error: \" + t.getMessage()); " +
            "}"
        );
        
        System.out.println("      Injected: com.modkalogger.ModKaLogger.init()");
        
        targetClass.writeFile(tempDir.getAbsolutePath());
        targetClass.detach();
    }
    
    /**
     * Находит класс entrypoint
     */
    private static String findEntrypointClass(File dir) {
        File fabricModJson = new File(dir, "fabric.mod.json");
        if (!fabricModJson.exists()) return null;
        
        try {
            String content = new String(Files.readAllBytes(fabricModJson.toPath()), "UTF-8");
            
            // Ищем entrypoints -> client
            String entrypoint = extractEntrypoint(content, "client");
            if (entrypoint != null) {
                return entrypoint;
            }
            
            // Ищем main entrypoint
            entrypoint = extractEntrypoint(content, "main");
            if (entrypoint != null) {
                return entrypoint;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    private static String extractEntrypoint(String json, String type) {
        // Сначала находим секцию "entrypoints"
        int entrypointsIdx = json.indexOf("\"entrypoints\"");
        if (entrypointsIdx == -1) return null;
        
        // Находим открывающую скобку секции entrypoints
        int entrypointsBrace = json.indexOf("{", entrypointsIdx);
        if (entrypointsBrace == -1) return null;
        
        // Ищем закрывающую скобку секции entrypoints (с учётом вложенности)
        int depth = 1;
        int entrypointsEnd = entrypointsBrace + 1;
        while (entrypointsEnd < json.length() && depth > 0) {
            char c = json.charAt(entrypointsEnd);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            entrypointsEnd++;
        }
        
        // Ищем тип entrypoint ТОЛЬКО внутри секции entrypoints
        String entrypointsSection = json.substring(entrypointsBrace, entrypointsEnd);
        String search = "\"" + type + "\"";
        int idx = entrypointsSection.indexOf(search);
        if (idx == -1) return null;
        
        int start = entrypointsSection.indexOf("[", idx);
        int end = entrypointsSection.indexOf("]", start);
        if (start == -1 || end == -1) return null;
        
        String arrayContent = entrypointsSection.substring(start + 1, end);
        
        int quoteStart = arrayContent.indexOf("\"");
        int quoteEnd = arrayContent.indexOf("\"", quoteStart + 1);
        if (quoteStart != -1 && quoteEnd != -1) {
            return arrayContent.substring(quoteStart + 1, quoteEnd);
        }
        
        return null;
    }
    
    /**
     * Обновляет fabric.mod.json
     * ТОЛЬКО добавляет entrypoint - НЕ добавляет миксины (вызывают краш!)
     */
    private static void updateFabricModJson(File tempDir) throws Exception {
        File fabricModJson = new File(tempDir, "fabric.mod.json");
        
        if (!fabricModJson.exists()) {
            System.err.println("[!] fabric.mod.json not found!");
            throw new Exception("fabric.mod.json not found");
        }
        
        String content = new String(Files.readAllBytes(fabricModJson.toPath()), "UTF-8");
        
        // НИКОГДА не добавляем миксины - они крашат игру!
        // Удаляем ссылку на modkalogger.mixins если есть
        if (content.contains("modkalogger.mixins.json")) {
            content = content.replaceAll(",?\\s*\"modkalogger\\.mixins\\.json\"", "");
            System.out.println("      Removed modkalogger.mixins reference");
        }
        
        // Добавляем ModKaLoggerClient как client entrypoint
        if (!content.contains("com.modkalogger.ModKaLoggerClient")) {
            if (content.contains("\"client\":")) {
                // Уже есть client entrypoints - добавляем в конец массива
                int clientIdx = content.indexOf("\"client\":");
                int arrayStart = content.indexOf("[", clientIdx);
                int arrayEnd = content.indexOf("]", arrayStart);
                if (arrayStart != -1 && arrayEnd != -1) {
                    String before = content.substring(0, arrayEnd);
                    String after = content.substring(arrayEnd);
                    String arrayContent = content.substring(arrayStart + 1, arrayEnd).trim();
                    String comma = arrayContent.isEmpty() ? "" : ", ";
                    content = before + comma + "\"com.modkalogger.ModKaLoggerClient\"" + after;
                }
            } else if (content.contains("\"entrypoints\":")) {
                content = content.replaceFirst(
                    "\"entrypoints\"\\s*:\\s*\\{",
                    "\"entrypoints\": {\n    \"client\": [\"com.modkalogger.ModKaLoggerClient\"],"
                );
            } else {
                int lastBrace = content.lastIndexOf("}");
                if (lastBrace > 0) {
                    content = content.substring(0, lastBrace) + 
                        ",\n  \"entrypoints\": {\n    \"client\": [\"com.modkalogger.ModKaLoggerClient\"]\n  }\n}";
                }
            }
            System.out.println("      Added ModKaLoggerClient entrypoint");
        } else {
            System.out.println("      ModKaLoggerClient entrypoint already present");
        }
        
        Files.write(fabricModJson.toPath(), content.getBytes("UTF-8"));
    }
    
    private static void unzipJar(String jarPath, String destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            
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
    
    private static void createJar(File sourceDir, String jarPath) throws Exception {
        Manifest manifest = new Manifest();
        File mf = new File(sourceDir, "META-INF/MANIFEST.MF");
        if (mf.exists()) {
            try (FileInputStream fis = new FileInputStream(mf)) {
                manifest.read(fis);
            }
        }
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath), manifest)) {
            addFilesToJar(sourceDir, "", jos);
        }
    }
    
    private static void addFilesToJar(File file, String parentPath, JarOutputStream jos) throws Exception {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) return;
            
            for (File child : files) {
                String path = parentPath.isEmpty() ? child.getName() : parentPath + "/" + child.getName();
                addFilesToJar(child, path, jos);
            }
        } else {
            if (parentPath.endsWith("MANIFEST.MF")) return;
            
            jos.putNextEntry(new JarEntry(parentPath));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    jos.write(buffer, 0, len);
                }
            }
            jos.closeEntry();
        }
    }
    
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) deleteDirectory(f);
            }
        }
        dir.delete();
    }
}
