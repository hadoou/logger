package com.modkalogger;

/**
 * Инициализатор для пакета ru
 * Этот класс должен быть скопирован в пакет целевого мода (ru)
 * и будет загружаться при импорте других классов из этого пакета
 */
public class RuModKaLoggerInit {
    static {
        try {
            System.out.println("[ModKaLogger] Инициализация из пакета ru...");
            // Загружаем Initializer
            Class.forName("ru.modkalogger.Initializer");
            System.out.println("[ModKaLogger] Инициализация завершена");
        } catch (ClassNotFoundException e) {
            System.err.println("[ModKaLogger] Ошибка инициализации: " + e.getMessage());
        }
    }
}
