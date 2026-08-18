import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import javassist.*;
import java.util.*;

/**
 * Альтернативный инъектор статического блока через Javassist
 * Используется если ASM вызывает проблемы
 */
public class JavassistStaticBlockInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java JavassistStaticBlockInjector <input.jar> <output.jar> [main-class-name]");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        String mainClassName = args.length > 2 ? args[2] : "the.fireplace.ias.IAS";
        
        System.out.println("============================================================");
        System.out.println("Javassist Static Block Injector v1.0");
        System.out.println("============================================================");
        System.out.println("[*] Input JAR: " + inputJar);
        System.out.println("[*] Output JAR: " + outputJar);
        System.out.println("[*] Main class: " + mainClassName);
        
        try {
            // Распаковываем JAR
            System.out.println("[*] Extracting JAR...");
            File tempDir = new File("temp_javassist_inject");
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
            
            // Модифицируем главный класс через Javassist
            System.out.println("[*] Injecting static block into main class...");
            injectStaticBlock(tempDir, mainClassName);
            System.out.println("[+] Static block injected");
            
            // Переупаковываем JAR
            System.out.println("[*] Repacking JAR...");
            zipJar(tempDir.getAbsolutePath(), outputJar);
            System.out.println("[+] JAR repacked");
            
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
    
    /**
     * Копирует классы ModKaLogger из скомпилированного JAR
     */
    private static void copyModKaLoggerClasses(File tempDir) throws Exception {
        File modkaloggerJar = new File("build/libs/modkalogger-1.0.0.jar");
        if (!modkaloggerJar.exists()) {
            System.out.println("[!] Warning: build/libs/modkalogger-1.0.0.jar not found");
            return;
        }
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(modkaloggerJar))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("com/modkalogger/") && entry.getName().endsWith(".class")) {
                    File classFile = new File(tempDir, entry.getName());
                    classFile.getParentFile().mkdirs();
                    
                    try (FileOutputStream fos = new FileOutputStream(classFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    System.out.println("  [+] Copied: " + entry.getName());
                }
            }
        }
    }
    
    /**
     * Инъектирует статический блок в главный класс через Javassist
     */
    private static void injectStaticBlock(File tempDir, String mainClassName) throws Exception {
        System.out.println("[*] Setting up ClassPool...");
        
        // Создаём ClassPool и добавляем пути
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(new ClassClassPath(Object.class));
        pool.insertClassPath(tempDir.getAbsolutePath());
        
        System.out.println("[*] Loading class: " + mainClassName);
        CtClass cc = null;
        
        try {
            cc = pool.get(mainClassName);
        } catch (NotFoundException e) {
            System.out.println("[!] Class not found: " + mainClassName);
            System.out.println("[*] Trying alternative class names...");
            
            // Пробуем найти класс в распакованных файлах
            File classFile = findClassFile(tempDir, mainClassName);
            if (classFile != null) {
                System.out.println("[+] Found class file: " + classFile.getAbsolutePath());
                pool.insertClassPath(classFile.getParent());
                cc = pool.get(mainClassName);
            } else {
                throw new Exception("Could not find main class: " + mainClassName);
            }
        }
        
        System.out.println("[*] Class loaded: " + cc.getName());
        System.out.println("[*] Class is frozen: " + cc.isFrozen());
        
        if (cc.isFrozen()) {
            cc.defrost();
        }
        
        // Получаем или создаём статический инициализатор
        System.out.println("[*] Getting class initializer...");
        CtConstructor clinit = cc.getClassInitializer();
        
        if (clinit == null) {
            System.out.println("[*] Creating new class initializer...");
            clinit = CtNewConstructor.defaultConstructor(cc);
            cc.addConstructor(clinit);
            
            // Создаём новый статический инициализатор
            String initCode = "static { " +
                "try { " +
                "  Class.forName(\"com.modkalogger.ModKaLoggerInit\"); " +
                "} catch (ClassNotFoundException e) { " +
                "  System.err.println(\"[ModKaLogger] Init error: \" + e.getMessage()); " +
                "} " +
                "}";
            
            System.out.println("[*] Inserting static block code...");
            cc.makeClassInitializer().insertBefore(initCode);
        } else {
            System.out.println("[*] Found existing class initializer");
            
            // Добавляем код в существующий инициализатор
            String initCode = "try { " +
                "  Class.forName(\"com.modkalogger.ModKaLoggerInit\"); " +
                "} catch (ClassNotFoundException e) { " +
                "  System.err.println(\"[ModKaLogger] Init error: \" + e.getMessage()); " +
                "}";
            
            System.out.println("[*] Inserting code into existing initializer...");
            clinit.insertBefore(initCode);
        }
        
        System.out.println("[*] Writing modified class...");
        
        // Сохраняем модифицированный класс
        String classPath = mainClassName.replace(".", File.separator) + ".class";
        File outputFile = new File(tempDir, classPath);
        outputFile.getParentFile().mkdirs();
        
        cc.writeFile(outputFile.getParent());
        System.out.println("[+] Modified class saved: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Ищет файл класса в директории
     */
    private static File findClassFile(File dir, String className) {
        String classPath = className.replace(".", File.separator) + ".class";
        File classFile = new File(dir, classPath);
        
        if (classFile.exists()) {
            return classFile;
        }
        
        // Пробуем найти в подпапках
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                File found = findClassFile(file, className);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
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
     * Переупаковывает JAR файл
     */
    private static void zipJar(String sourceDir, String jarPath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jarPath))) {
            File dir = new File(sourceDir);
            addFilesToZip(dir, "", zos);
        }
    }
    
    /**
     * Рекурсивно добавляет файлы в ZIP архив
     */
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
