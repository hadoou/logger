import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Инъектор статического блока через ASM
 * Добавляет явный статический блок инициализации в главный класс
 * Гарантирует выполнение при загрузке класса
 */
public class StaticBlockInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java StaticBlockInjector <input.jar> <output.jar> [main-class-path]");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        String mainClassPath = args.length > 2 ? args[2] : "the_fireplace/ias/IAS.class";
        
        System.out.println("============================================================");
        System.out.println("Static Block Bytecode Injector v2.0");
        System.out.println("============================================================");
        System.out.println("[*] Input JAR: " + inputJar);
        System.out.println("[*] Output JAR: " + outputJar);
        System.out.println("[*] Main class: " + mainClassPath);
        
        try {
            // Распаковываем JAR
            System.out.println("[*] Extracting JAR...");
            File tempDir = new File("temp_static_inject");
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
            
            // Модифицируем главный класс через ASM
            System.out.println("[*] Injecting static block into main class...");
            injectStaticBlock(tempDir, mainClassPath);
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
        
        File modkaDir = new File(tempDir, "com/modkalogger");
        modkaDir.mkdirs();
        
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
     * Инъектирует статический блок в главный класс через ASM
     */
    private static void injectStaticBlock(File tempDir, String mainClassPath) throws Exception {
        File mainClassFile = new File(tempDir, mainClassPath);
        
        if (!mainClassFile.exists()) {
            throw new Exception("Main class not found: " + mainClassPath);
        }
        
        System.out.println("[*] Reading class: " + mainClassPath);
        byte[] classBytes = Files.readAllBytes(mainClassFile.toPath());
        
        // Используем ASM для модификации класса
        ClassReader cr = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);
        
        System.out.println("[*] Class name: " + classNode.name);
        System.out.println("[*] Current methods: " + classNode.methods.size());
        
        // Ищем или создаём статический инициализатор
        MethodNode clinit = null;
        for (MethodNode method : classNode.methods) {
            if ("<clinit>".equals(method.name)) {
                clinit = method;
                break;
            }
        }
        
        if (clinit == null) {
            System.out.println("[*] Creating new static initializer...");
            clinit = new MethodNode(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
            );
            classNode.methods.add(clinit);
        } else {
            System.out.println("[*] Found existing static initializer");
        }
        
        // Добавляем инструкции в статический блок
        InsnList instructions = new InsnList();
        
        // Добавляем вызов инициализации
        instructions.add(new LdcInsnNode("com.modkalogger.ModKaLoggerInit"));
        instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/lang/Class",
            "forName",
            "(Ljava/lang/String;)Ljava/lang/Class;",
            false
        ));
        instructions.add(new InsnNode(Opcodes.POP)); // Убираем результат со стека
        
        // Добавляем обработку исключений
        instructions.add(new InsnNode(Opcodes.RETURN));
        
        // Если это новый метод, устанавливаем инструкции
        if (clinit.instructions.size() == 0) {
            clinit.instructions = instructions;
        } else {
            // Если метод уже существует, вставляем наши инструкции в начало
            clinit.instructions.insertBefore(clinit.instructions.getFirst(), instructions);
        }
        
        System.out.println("[*] Static block code injected");
        
        // Преобразуем обратно в bytecode
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classNode.accept(cw);
        byte[] modifiedBytes = cw.toByteArray();
        
        // Сохраняем модифицированный класс
        Files.write(mainClassFile.toPath(), modifiedBytes);
        System.out.println("[+] Modified class saved");
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
