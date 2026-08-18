package byteinjector;

import javassist.*;

import java.io.*;
import java.lang.instrument.*;
import java.nio.file.*;
import java.util.jar.*;

/**
 * Инъектор классов через Javassist
 * Модифицирует bytecode Java классов
 */
public class ClassInjector {
    
    /**
     * Добавляет статический вызов в начало метода
     */
    public static byte[] injectStaticCall(byte[] classBytes, String methodName, 
                                          String callClass, String callMethod) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));
        
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                method.insertBefore(callClass + "." + callMethod + "();");
            }
        }
        
        return ctClass.toBytecode();
    }
    
    /**
     * Добавляет код в статический инициализатор
     */
    public static byte[] injectClinit(byte[] classBytes, String code) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));
        
        CtConstructor clinit = ctClass.getClassInitializer();
        if (clinit == null) {
            clinit = ctClass.makeClassInitializer();
        }
        
        clinit.insertBefore(code);
        return ctClass.toBytecode();
    }
    
    /**
     * Создаёт новый метод в классе
     */
    public static byte[] addMethod(byte[] classBytes, String methodName, 
                                   String returnType, String body) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));
        
        CtMethod method = new CtMethod(pool.get(returnType), methodName, new CtClass[0], ctClass);
        method.setBody(body);
        ctClass.addMethod(method);
        
        return ctClass.toBytecode();
    }
    
    /**
     * Модифицирует существующий метод
     */
    public static byte[] modifyMethod(byte[] classBytes, String methodName, 
                                      String insertBefore, String insertAfter) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));
        
        CtMethod method = ctClass.getDeclaredMethod(methodName);
        
        if (insertBefore != null) {
            method.insertBefore(insertBefore);
        }
        if (insertAfter != null) {
            method.insertAfter(insertAfter);
        }
        
        return ctClass.toBytecode();
    }
}
