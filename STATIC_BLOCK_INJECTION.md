# Static Block Bytecode Injection Guide

## Описание

Новый подход инъекции использует **ASM (Abstract Syntax Tree)** для прямого добавления статического блока инициализации в bytecode главного класса. Это гарантирует выполнение инициализации при загрузке класса.

## Преимущества

✅ **Гарантированное выполнение** - статический блок выполняется при загрузке класса  
✅ **Явная ссылка** - класс явно загружается через `Class.forName()`  
✅ **Безопасность** - обработка исключений встроена в bytecode  
✅ **Совместимость** - работает с любыми версиями Java  
✅ **Простота** - минимальная модификация bytecode  

## Как это работает

### 1. Структура инъектора

```
StaticBlockInjector.java
├── Распаковка JAR
├── Копирование классов ModKaLogger
├── Модификация bytecode главного класса через ASM
│   ├── Чтение класса
│   ├── Поиск/создание статического инициализатора (<clinit>)
│   ├── Добавление инструкций ASM
│   └── Сохранение модифицированного класса
└── Переупаковка JAR
```

### 2. Добавляемый код

В статический блок добавляется:

```java
static {
    try {
        Class.forName("com.modkalogger.ModKaLoggerInit");
    } catch (ClassNotFoundException e) {
        // Обработка ошибки
    }
}
```

Это гарантирует:
- Загрузку класса `ModKaLoggerInit`
- Выполнение его статического блока
- Инициализацию всей цепочки

### 3. Инструкции ASM

```
LDC "com.modkalogger.ModKaLoggerInit"
INVOKESTATIC java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;
POP
RETURN
```

## Использование

### Способ 1: Через Python скрипт (рекомендуется)

```bash
python3 inject_static_block.py
```

Скрипт автоматически:
1. Компилирует `StaticBlockInjector.java`
2. Находит ASM библиотеку в Gradle кэше
3. Запускает инъектор
4. Создает `modkalogger-injected.jar`

### Способ 2: Ручная компиляция и запуск

```bash
# 1. Компилируем инъектор
javac -cp . StaticBlockInjector.java

# 2. Находим ASM JAR файлы
find ~/.gradle -name "asm*.jar" | grep -v sources | grep -v javadoc

# 3. Запускаем инъектор
java -cp ".:path/to/asm-9.2.jar:path/to/asm-tree-9.2.jar" \
    StaticBlockInjector \
    build/libs/modkalogger-1.0.0.jar \
    build/libs/modkalogger-injected.jar \
    the_fireplace/ias/IAS.class
```

### Способ 3: Через Gradle задачу

Добавьте в `build.gradle`:

```gradle
task injectStaticBlock {
    doLast {
        exec {
            commandLine 'python3', 'inject_static_block.py'
        }
    }
}
```

Затем:
```bash
gradle injectStaticBlock
```

## Проверка результата

### 1. Проверка размера JAR

```bash
ls -lh build/libs/modkalogger-injected.jar
```

Размер должен быть примерно такой же как оригинальный.

### 2. Распаковка и проверка класса

```bash
# Распаковываем
unzip -q build/libs/modkalogger-injected.jar -d check_jar

# Проверяем наличие классов
ls -la check_jar/com/modkalogger/
ls -la check_jar/the_fireplace/ias/

# Очищаем
rm -rf check_jar
```

### 3. Проверка через javap

```bash
# Распаковываем класс
unzip -p build/libs/modkalogger-injected.jar the_fireplace/ias/IAS.class > IAS.class

# Смотрим дизассемблированный код
javap -c -private IAS.class | grep -A 20 "<clinit>"

# Очищаем
rm IAS.class
```

Должны увидеть инструкции:
```
LDC "com.modkalogger.ModKaLoggerInit"
INVOKESTATIC java/lang/Class.forName
```

## Возможные проблемы

### Проблема 1: ASM не найден

**Ошибка:**
```
Error: Could not find or load main class StaticBlockInjector
```

**Решение:**
```bash
# Убедитесь что ASM в classpath
find ~/.gradle -name "asm*.jar" -type f

# Или добавьте в build.gradle:
dependencies {
    implementation 'org.ow2.asm:asm:9.5'
    implementation 'org.ow2.asm:asm-tree:9.5'
}

# И запустите:
gradle build
```

### Проблема 2: Главный класс не найден

**Ошибка:**
```
Main class not found: the_fireplace/ias/IAS.class
```

**Решение:**
Проверьте путь к главному классу в JAR:
```bash
unzip -l build/libs/modkalogger-1.0.0.jar | grep -i "\.class$" | head -20
```

Используйте правильный путь в команде инъектора.

### Проблема 3: Ошибка при распаковке

**Ошибка:**
```
java.util.zip.ZipException: invalid entry size
```

**Решение:**
Убедитесь что исходный JAR не поврежден:
```bash
unzip -t build/libs/modkalogger-1.0.0.jar
```

## Отладка

### Включение подробного вывода

Модифицируйте `StaticBlockInjector.java`:

```java
// Добавьте в метод injectStaticBlock():
System.out.println("[DEBUG] Class version: " + classNode.version);
System.out.println("[DEBUG] Class access: " + classNode.access);
System.out.println("[DEBUG] Methods before: " + classNode.methods.size());

// После добавления метода:
System.out.println("[DEBUG] Methods after: " + classNode.methods.size());
System.out.println("[DEBUG] Clinit instructions: " + clinit.instructions.size());
```

### Проверка bytecode

```bash
# Распаковываем оригинальный класс
unzip -p build/libs/modkalogger-1.0.0.jar the_fireplace/ias/IAS.class > IAS_original.class

# Распаковываем инъектированный класс
unzip -p build/libs/modkalogger-injected.jar the_fireplace/ias/IAS.class > IAS_injected.class

# Сравниваем размеры
ls -l IAS_*.class

# Дизассемблируем оба
javap -c -private IAS_original.class > IAS_original.txt
javap -c -private IAS_injected.class > IAS_injected.txt

# Сравниваем
diff IAS_original.txt IAS_injected.txt | head -50
```

## Альтернативные подходы

### Если ASM не работает

Можно использовать **Javassist** (уже в зависимостях):

```java
ClassPool pool = ClassPool.getDefault();
CtClass cc = pool.get("the.fireplace.ias.IAS");
String src = "{ Class.forName(\"com.modkalogger.ModKaLoggerInit\"); }";
cc.makeClassInitializer().insertBefore(src);
cc.writeFile("output");
```

### Если нужна более сложная логика

Используйте **BCEL** (Byte Code Engineering Library):

```java
JavaClass jc = Repository.lookupClass("the.fireplace.ias.IAS");
ClassGen cg = new ClassGen(jc);
// Добавляем инструкции
cg.getJavaClass().dump("output.class");
```

## Результат

После успешной инъекции:

1. ✅ Главный класс содержит статический блок
2. ✅ Статический блок загружает `ModKaLoggerInit`
3. ✅ `ModKaLoggerInit` выполняет инициализацию
4. ✅ Все классы ModKaLogger загружены и готовы
5. ✅ Логирование работает при запуске приложения

## Следующие шаги

1. Скомпилируйте проект: `gradle build`
2. Запустите инъектор: `python3 inject_static_block.py`
3. Замените оригинальный JAR на инъектированный
4. Запустите приложение и проверьте логи

---

**Версия:** 2.0  
**Дата:** 2026-04-29  
**Статус:** Готово к использованию
