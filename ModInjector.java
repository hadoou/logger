import javassist.*;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Вшиватор для инъекции ModKaLogger в Minecraft моды
 * Использует JavaAssist для модификации bytecode
 * 
 * Правильный подход:
 * 1. Находим главный класс целевого мода
 * 2. Создаём отдельный класс-инициализатор (не анонимный!)
 * 3. Добавляем вызов этого класса в статический инициализатор
 * 4. Обновляем JAR с модифицированным классом
 */
public class ModInjector {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Использование: java ModInjector <путь_к_моду> <путь_вывода>");
            System.out.println("Пример: java ModInjector ias.jar ias_injected.jar");
            System.exit(1);
        }
        
        String inputJar = args[0];
        String outputJar = args[1];
        
        System.out.println("============================================================");
        System.out.println("ModKaLogger Injector v2.0 (JavaAssist - Fixed)");
        System.out.println("============================================================");
        
        try {
            injectMod(inputJar, outputJar);
            System.out.println("[+] Вшивание завершено успешно!");
            System.out.println("[+] Результат: " + outputJar);
        } catch (Exception e) {
            System.err.println("[!] Ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void injectMod(String inputJar, String outputJar) throws Exception {
        System.out.println("[*] Инициализирую ClassPool...");
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(inputJar);
        
        // Находим главный класс мода
        System.out.println("[*] Ищу главный класс мода...");
        String mainClass = findMainClass(inputJar);
        
        if (mainClass == null) {
            throw new Exception("Главный класс мода не найден");
        }
        
        System.out.println("[+] Найден главный класс: " + mainClass);
        
        // Получаем класс
        CtClass targetClass = pool.get(mainClass);
        
        // Создаём или получаем статический инициализатор
        System.out.println("[*] Добавляю инициализацию...");
        CtConstructor staticInit = targetClass.getClassInitializer();
        
        if (staticInit == null) {
            System.out.println("[*] Статический инициализатор не найден, создаю новый...");
            staticInit = targetClass.makeClassInitializer();
        }
        
        // Добавляем простой вызов инициализации (БЕЗ анонимных классов!)
        // Javassist не поддерживает анонимные классы в insertAfter/insertBefore
        String initCode = "{ " +
            "try { " +
            "  new Thread(new ModKaLoggerInitThread()).start(); " +
            "} catch(Exception e) { " +
            "  System.err.println(\"[ModKaLogger] Ошибка инициализации: \" + e.getMessage()); " +
            "} " +
            "}";
        
        staticInit.insertAfter(initCode);
        
        System.out.println("[+] Инициализация добавлена");
        
        // Сохраняем модифицированный класс
        System.out.println("[*] Сохраняю модифицированный класс...");
        byte[] modifiedClass = targetClass.toBytecode();
        
        // Обновляем JAR
        System.out.println("[*] Обновляю JAR файл...");
        updateJar(inputJar, outputJar, mainClass.replace(".", "/") + ".class", modifiedClass);
        
        System.out.println("[+] JAR обновлён");
    }
    
    private static String findMainClass(String jarPath) throws Exception {
        try (JarFile jar = new JarFile(jarPath)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // Ищем классы в пакете ru (целевой мод)
                if (name.startsWith("ru/") && name.endsWith(".class") && !name.contains("$")) {
                    // Это может быть главный класс
                    String className = name.replace("/", ".").replace(".class", "");
                    return className;
                }
            }
        }
        
        return null;
    }
    
    private static void updateJar(String inputJar, String outputJar, String classPath, byte[] modifiedClass) throws Exception {
        File tempFile = new File(outputJar + ".tmp");
        
        try (JarFile inputJarFile = new JarFile(inputJar);
             JarOutputStream outputJarStream = new JarOutputStream(new FileOutputStream(tempFile))) {
            
            java.util.Enumeration<JarEntry> entries = inputJarFile.entries();
            byte[] buffer = new byte[1024];
            int bytesRead;
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (entry.getName().equals(classPath)) {
                    // Добавляем модифицированный класс
                    JarEntry newEntry = new JarEntry(classPath);
                    outputJarStream.putNextEntry(newEntry);
                    outputJarStream.write(modifiedClass);
                } else {
                    // Копируем остальные файлы
                    outputJarStream.putNextEntry(entry);
                    InputStream inputStream = inputJarFile.getInputStream(entry);
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputJarStream.write(buffer, 0, bytesRead);
                    }
                    
                    inputStream.close();
                }
            }
        }
        
        // Заменяем оригинальный JAR
        Files.move(tempFile.toPath(), new File(outputJar).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
