package com.modkalogger;

/**
 * Bootstrap класс для инициализации ModKaLogger
 * Этот класс должен быть загружен как можно раньше
 */
public class Bootstrap {

    // Статический блок выполняется при загрузке класса
    static {
        try {
            System.out.println("[ModKaLogger Bootstrap] Инициализация...");
        } catch (Exception e) {
            System.err.println("[ModKaLogger Bootstrap] Ошибка инициализации: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Метод для явной инициализации
     */
    public static void init() {
        System.out.println("[ModKaLogger Bootstrap] init() вызван");
    }

    /**
     * Метод для проверки что Bootstrap загружен
     */
    public static boolean isLoaded() {
        return true;
    }
}
