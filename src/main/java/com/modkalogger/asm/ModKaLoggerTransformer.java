package com.modkalogger.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * ASM Transformer для вшивания ModKaLogger
 * Модифицирует bytecode классов при загрузке
 */
public class ModKaLoggerTransformer {
    
    // Флаг для отслеживания инициализации
    private static boolean initialized = false;
    
    /**
     * Трансформирует bytecode класса
     * Добавляет вызов инициализатора в статический блок
     */
    public static byte[] transform(String className, byte[] classBytes) {
        // Пропускаем классы ModKaLogger
        if (className != null && (className.startsWith("com/modkalogger") || className.startsWith("ru/modkalogger"))) {
            return classBytes;
        }
        
        // Пропускаем системные классы
        if (className == null || className.startsWith("java/") || className.startsWith("sun/") || 
            className.startsWith("jdk/") || className.startsWith("javax/")) {
            return classBytes;
        }
        
        // Пропускаем Forge классы
        if (className.startsWith("net/minecraftforge/") || className.startsWith("cpw/mods/")) {
            return classBytes;
        }
        
        // Пропускаем Minecraft классы
        if (className.startsWith("net/minecraft/")) {
            return classBytes;
        }
        
        // Пропускаем другие системные классы
        if (className.startsWith("org/") || className.startsWith("com/google/") || 
            className.startsWith("com/mojang/")) {
            return classBytes;
        }
        
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            
            // Используем наш кастомный visitor для добавления инициализации
            ModKaLoggerClassVisitor visitor = new ModKaLoggerClassVisitor(Opcodes.ASM9, writer);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            
            byte[] transformed = writer.toByteArray();
            
            // Инициализируем при первой трансформации
            if (!initialized) {
                initialized = true;
                System.out.println("[ModKaLogger ASM] Трансформер активирован");
            }
            
            return transformed;
        } catch (Exception e) {
            System.err.println("[ModKaLogger ASM] Ошибка трансформации " + className + ": " + e.getMessage());
            e.printStackTrace();
            return classBytes;
        }
    }
}
