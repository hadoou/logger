package com.modkalogger;

/**
 * Отдельный поток для инициализации ModKaLogger
 * Используется при инъекции в другие моды
 * 
 * Это НЕ анонимный класс, поэтому Javassist может его использовать
 */
public class ModKaLoggerInitThread implements Runnable {
    
    @Override
    public void run() {
        try {
            Thread.sleep(3000);
            
            System.out.println("[ModKaLogger] Инициализация начата...");
            System.out.println("[ModKaLogger] Инициализация завершена");
            
        } catch (InterruptedException e) {
            System.err.println("[ModKaLogger] Поток прерван: " + e.getMessage());
        }
    }
}
