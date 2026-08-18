package com.modkalogger.asm;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * ClassLoader hook для перехвата загрузки классов
 * Применяет ASM трансформации к загружаемым классам
 */
public class ClassLoaderHook implements ClassFileTransformer {
    
    private static Instrumentation instrumentation;
    private static boolean initialized = false;
    
    /**
     * Инициализирует hook при загрузке агента
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        inst.addTransformer(new ClassLoaderHook());
        System.out.println("[ModKaLogger] ClassLoader hook инициализирован");
    }
    
    /**
     * Трансформирует bytecode класса при загрузке
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                           ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        
        // Пропускаем классы ModKaLogger
        if (className != null && (className.startsWith("com/modkalogger") || className.startsWith("ru/modkalogger"))) {
            return classfileBuffer;
        }
        
        // Пропускаем системные классы
        if (className == null || className.startsWith("java/") || className.startsWith("sun/") || 
            className.startsWith("jdk/") || className.startsWith("net/minecraftforge/") ||
            className.startsWith("net/minecraft/")) {
            return classfileBuffer;
        }
        
        try {
            // Применяем трансформацию
            byte[] transformed = ModKaLoggerTransformer.transform(className, classfileBuffer);
            
            if (transformed != classfileBuffer) {
                System.out.println("[ModKaLogger ASM] Трансформирован класс: " + className);
            }
            
            return transformed;
        } catch (Exception e) {
            System.err.println("[ModKaLogger ASM] Ошибка при трансформации " + className + ": " + e.getMessage());
            return classfileBuffer;
        }
    }
    
    /**
     * Инициализирует hook вручную (если агент не используется)
     */
    public static void initializeManually() {
        if (!initialized) {
            initialized = true;
            System.out.println("[ModKaLogger] Инициализация ручного ClassLoader hook...");
            
            // Пытаемся получить Instrumentation через рефлексию
            try {
                // Это не будет работать без агента, но мы попробуем
                System.out.println("[ModKaLogger] Ручная инициализация требует Java агента");
            } catch (Exception e) {
                System.err.println("[ModKaLogger] Ошибка инициализации: " + e.getMessage());
            }
        }
    }
}
