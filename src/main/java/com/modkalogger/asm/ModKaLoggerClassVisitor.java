package com.modkalogger.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ClassVisitor для добавления инициализации ModKaLogger
 */
public class ModKaLoggerClassVisitor extends ClassVisitor {
    
    private String className;
    private boolean hasStaticInit = false;
    
    public ModKaLoggerClassVisitor(int api, ClassVisitor cv) {
        super(api, cv);
    }
    
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // Если это статический инициализатор, оборачиваем его
        if (name.equals("<clinit>") && descriptor.equals("()V")) {
            hasStaticInit = true;
            // Возвращаем обёрнутый visitor который добавит вызов инициализатора
            return new StaticInitMethodVisitor(mv);
        }
        
        return mv;
    }
    
    @Override
    public void visitEnd() {
        // Если статического инициализатора нет, создаём его
        if (!hasStaticInit) {
            MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
            );
            
            mv.visitCode();
            
            // Добавляем вызов инициализатора
            addInitializerCall(mv);
            
            // Возвращаемся
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
        }
        
        super.visitEnd();
    }
    
    /**
     * Добавляет вызов инициализатора ModKaLogger
     */
    private void addInitializerCall(MethodVisitor mv) {
        // Вызываем: com.modkalogger.Initializer.init()
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/modkalogger/Initializer",
            "init",
            "()V",
            false
        );
    }
    
    /**
     * Внутренний класс для оборачивания существующего статического инициализатора
     */
    private static class StaticInitMethodVisitor extends MethodVisitor {
        
        public StaticInitMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            // Добавляем вызов инициализатора в начало статического блока
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/modkalogger/Initializer",
                "init",
                "()V",
                false
            );
        }
    }
}
