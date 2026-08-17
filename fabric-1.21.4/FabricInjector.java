import javassist.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Инъектор ModKaLogger для Fabric модов
 * Копирует записи напрямую из ZIP в ZIP — ничего не теряется (включая дубликаты)
 */
public class FabricInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("==================================================");
            System.out.println("ModKaLogger Fabric Injector v3.0");
            System.out.println("==================================================");
            System.out.println("Usage: <input.jar> <output.jar> [modJarPath]");
            System.out.println("Example: java FabricInjector mod.jar mod_injected.jar path/to/modd.jar");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        String modJarPath = args.length > 2 ? args[2] : "build/libs/modkalogger-fabric-1.0.0.jar";
        
        File inputFile = new File(inputJar);
        File modFile = new File(modJarPath);
        
        if (!inputFile.exists()) {
            System.err.println("[!] Input JAR not found: " + inputJar);
            System.exit(1);
        }
        if (!modFile.exists()) {
            System.err.println("[!] ModKaLogger Fabric JAR not found: " + modJarPath);
            System.err.println("[!] Run: gradlew build in fabric-1.21.4 folder");
            System.exit(1);
        }
        
        System.out.println("==================================================");
        System.out.println("ModKaLogger Fabric Injector v3.0");
        System.out.println("==================================================");
        System.out.println("[*] Input: " + inputJar);
        System.out.println("[*] ModKaLogger: " + modFile.getName());
        System.out.println("[*] Output: " + outputJar);
        System.out.println();
        
        try {
            // 1. Читаем fabric.mod.json из оригинального JAR
            System.out.println("[1/5] Reading fabric.mod.json...");
            byte[] originalFabricModJson = readEntryFromZip(inputJar, "fabric.mod.json");
            String fabricModJsonStr = originalFabricModJson != null ? 
                new String(originalFabricModJson, "UTF-8") : null;
            System.out.println("      Done");
            
            // 2. Ищем entrypoint
            System.out.println("[2/5] Finding entrypoint...");
            String entrypointClass = null;
            if (fabricModJsonStr != null) {
                entrypointClass = extractEntrypoint(fabricModJsonStr, "client");
                if (entrypointClass == null) {
                    entrypointClass = extractEntrypoint(fabricModJsonStr, "main");
                }
            }
            if (entrypointClass != null) {
                System.out.println("      Found: " + entrypointClass);
            } else {
                System.out.println("      No entrypoint found");
            }
            
            // 3. Обновляем fabric.mod.json
            System.out.println("[3/5] Updating fabric.mod.json...");
            byte[] updatedFabricModJson = null;
            if (fabricModJsonStr != null) {
                updatedFabricModJson = patchFabricModJson(fabricModJsonStr);
            }
            System.out.println("      Done");
            
            // 4. Модифицируем entrypoint класс через Javassist
            System.out.println("[4/5] Injecting initialization...");
            Map<String, byte[]> modifiedClasses = new LinkedHashMap<>();
            if (entrypointClass != null) {
                String classPath = entrypointClass.replace('.', '/') + ".class";
                byte[] classBytes = readEntryFromZip(inputJar, classPath);
                if (classBytes != null) {
                    byte[] patched = injectInitCall(classBytes, entrypointClass);
                    if (patched != null) {
                        modifiedClasses.put(classPath, patched);
                        System.out.println("      Injected into: " + entrypointClass);
                    } else {
                        System.out.println("      Javassist injection failed, skipping");
                    }
                } else {
                    System.out.println("      Class file not found in JAR: " + classPath);
                }
            } else {
                System.out.println("      No entrypoint to inject into");
            }
            System.out.println("      Done");
            
            // 5. Собираем выходной JAR: оригинал + модификации + ModKaLogger классы
            System.out.println("[5/5] Creating output JAR...");
            int[] counts = buildOutputJar(inputJar, modFile.getAbsolutePath(), outputJar, 
                                           updatedFabricModJson, modifiedClasses);
            System.out.println("      Original entries: " + counts[0]);
            System.out.println("      ModKaLogger entries: " + counts[1]);
            System.out.println("      Done: " + outputJar);
            
            System.out.println();
            System.out.println("==================================================");
            System.out.println("[+] SUCCESS!");
            System.out.println("[+] ModKaLogger injected into Fabric mod!");
            System.out.println("==================================================");
            
        } catch (Exception e) {
            System.err.println("[!] ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Читает байты конкретной записи из ZIP
     */
    private static byte[] readEntryFromZip(String zipPath, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toByteArray();
                }
            }
        }
        return null;
    }
    
    /**
     * Обновляет fabric.mod.json — добавляет ClientBootstrap entrypoint, убирает миксины
     */
    private static byte[] patchFabricModJson(String content) {
        // Убираем старую ссылку если есть чтобы добавить чисто
        if (content.contains("clientcore.mixins.json")) {
            content = content.replaceAll(",?\\s*\"modkalogger\\.mixins\\.json\"", "");
        }
        
        // Добавляем clientcore.mixins.json в секцию mixins
        if (!content.contains("clientcore.mixins.json")) {
            if (content.contains("\"mixins\":")) {
                int mixinsIdx = content.indexOf("\"mixins\"");
                int arrStart = content.indexOf("[", mixinsIdx);
                int arrEnd = content.indexOf("]", arrStart);
                String arrContent = content.substring(arrStart + 1, arrEnd).trim();
                String comma = arrContent.isEmpty() ? "" : ", ";
                content = content.substring(0, arrEnd) + 
                    comma + "\"clientcore.mixins.json\"" + 
                    content.substring(arrEnd);
                System.out.println("      Added clientcore.mixins.json to mixins");
            } else {
                // Нет секции mixins — добавляем перед entrypoints или в конец
                int lastBrace = content.lastIndexOf("}");
                if (lastBrace > 0) {
                    content = content.substring(0, lastBrace) + 
                        ",\n  \"mixins\": [\"clientcore.mixins.json\"]\n}";
                }
                System.out.println("      Added mixins section");
            }
        }
        
        // Добавляем ClientBootstrap entrypoint
        if (!content.contains("com.client.core.ClientBootstrap")) {
            if (content.contains("\"entrypoints\":")) {
                // Есть секция entrypoints — ищем "client" внутри
                int epIdx = content.indexOf("\"entrypoints\"");
                int braceIdx = content.indexOf("{", epIdx);
                
                // Ищем закрывающую скобку
                int depth = 1;
                int endIdx = braceIdx + 1;
                while (endIdx < content.length() && depth > 0) {
                    char c = content.charAt(endIdx);
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                    endIdx++;
                }
                
                String before = content.substring(0, braceIdx + 1);
                String inner = content.substring(braceIdx + 1, endIdx - 1).trim();
                String after = content.substring(endIdx - 1);
                
                if (inner.isEmpty()) {
                    // Пустой объект: "entrypoints": {}
                    content = before + "\n    \"client\": [\"com.client.core.ClientBootstrap\"]\n  " + after;
                } else if (content.indexOf("\"client\"", braceIdx) != -1 && 
                           content.indexOf("\"client\"", braceIdx) < endIdx) {
                    // Уже есть client — добавляем в массив
                    int clientIdx = content.indexOf("\"client\"", braceIdx);
                    int arrStart = content.indexOf("[", clientIdx);
                    int arrEnd = content.indexOf("]", arrStart);
                    String arrContent = content.substring(arrStart + 1, arrEnd).trim();
                    String comma = arrContent.isEmpty() ? "" : ", ";
                    content = content.substring(0, arrEnd) + 
                        comma + "\"com.client.core.ClientBootstrap\"" + 
                        content.substring(arrEnd);
                } else {
                    // Нет client — добавляем
                    content = content.substring(0, braceIdx + 1) + 
                        "\n    \"client\": [\"com.client.core.ClientBootstrap\"]," + 
                        content.substring(braceIdx + 1);
                }
                System.out.println("      Added ClientBootstrap entrypoint");
            } else {
                // Нет entrypoints — добавляем секцию
                int lastBrace = content.lastIndexOf("}");
                if (lastBrace > 0) {
                    content = content.substring(0, lastBrace) + 
                        ",\n  \"entrypoints\": {\n    \"client\": [\"com.client.core.ClientBootstrap\"]\n  }\n}";
                }
                System.out.println("      Added entrypoints section");
            }
        } else {
            System.out.println("      ClientBootstrap entrypoint already present");
        }
        
        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * Модифицирует bytecode класса — вставляет вызов ModKaLogger.init() в <clinit>
     */
    private static byte[] injectInitCall(byte[] classBytes, String className) {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));
            
            CtConstructor clinit = ctClass.getClassInitializer();
            if (clinit == null) {
                clinit = ctClass.makeClassInitializer();
            }
            
            clinit.insertBefore(
                "try { " +
                "  com.client.core.ModKaLogger.init(); " +
                "} catch(Throwable t) { " +
                "  System.err.println(\"[ModKaLogger] Init error: \" + t.getMessage()); " +
                "}"
            );
            
            byte[] result = ctClass.toBytecode();
            ctClass.detach();
            return result;
        } catch (Throwable e) {
            System.err.println("      Javassist error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Собирает выходной JAR: копирует ВСЕ записи из оригинала, применяет модификации,
     * добавляет классы ModKaLogger. Сохраняет дубликаты.
     */
    private static int[] buildOutputJar(String inputJarPath, String modJarPath, String outputJarPath,
                                         byte[] updatedFabricModJson, 
                                         Map<String, byte[]> modifiedClasses) throws Exception {
        int originalCount = 0;
        int modCount = 0;
        
        // Запоминаем все имена из оригинала чтобы не добавлять дубли из ModKaLogger
        Set<String> originalEntries = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(inputJarPath))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                originalEntries.add(e.getName());
            }
        }
        
        // Собираем modified class paths
        Set<String> modifiedPaths = modifiedClasses.keySet();
        
        try (ZipInputStream zisOriginal = new ZipInputStream(new FileInputStream(inputJarPath));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputJarPath))) {
            
            byte[] buffer = new byte[8192];
            
            // 1. Копируем ВСЕ записи из оригинала (включая дубликаты!)
            ZipEntry entry;
            while ((entry = zisOriginal.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Заменяем fabric.mod.json
                if (name.equals("fabric.mod.json") && updatedFabricModJson != null) {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(updatedFabricModJson);
                    zos.closeEntry();
                    originalCount++;
                    continue;
                }
                
                // Заменяем модифицированные классы
                if (modifiedPaths.contains(name)) {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(modifiedClasses.get(name));
                    zos.closeEntry();
                    originalCount++;
                    continue;
                }
                
                // Копируем запись как есть
                zos.putNextEntry(new ZipEntry(name));
                int len;
                while ((len = zisOriginal.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
                originalCount++;
            }
            
            // 2. Добавляем классы ModKaLogger (только тех которых нет в оригинале)
            try (ZipInputStream zisMod = new ZipInputStream(new FileInputStream(modJarPath))) {
                ZipEntry modEntry;
                while ((modEntry = zisMod.getNextEntry()) != null) {
                    String name = modEntry.getName();
                    
                    // Пропускаем metadata
                    if (name.equals("fabric.mod.json") || name.startsWith("META-INF/") || 
                        name.equals("pack.mcmeta") || name.endsWith(".SF") || 
                        name.endsWith(".DSA") || name.endsWith(".RSA")) {
                        continue;
                    }
                    
                    // Пропускаем если уже есть в оригинале
                    if (originalEntries.contains(name)) {
                        continue;
                    }
                    
                    zos.putNextEntry(new ZipEntry(name));
                    int len;
                    while ((len = zisMod.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                    modCount++;
                }
            }
        }
        
        return new int[]{originalCount, modCount};
    }
    
    /**
     * Извлекает имя класса entrypoint из fabric.mod.json
     */
    private static String extractEntrypoint(String json, String type) {
        int entrypointsIdx = json.indexOf("\"entrypoints\"");
        if (entrypointsIdx == -1) return null;
        
        int entrypointsBrace = json.indexOf("{", entrypointsIdx);
        if (entrypointsBrace == -1) return null;
        
        int depth = 1;
        int entrypointsEnd = entrypointsBrace + 1;
        while (entrypointsEnd < json.length() && depth > 0) {
            char c = json.charAt(entrypointsEnd);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            entrypointsEnd++;
        }
        
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
}
