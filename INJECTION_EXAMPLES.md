# Примеры использования инъекторов

## 📋 Содержание

1. [Пример 1: Базовое использование](#пример-1-базовое-использование)
2. [Пример 2: С проверкой результата](#пример-2-с-проверкой-результата)
3. [Пример 3: Автоматизация](#пример-3-автоматизация)
4. [Пример 4: Отладка](#пример-4-отладка)
5. [Пример 5: Альтернативные подходы](#пример-5-альтернативные-подходы)

---

## Пример 1: Базовое использование

### Самый простой способ

```bash
# Шаг 1: Перейдите в корень проекта
cd /path/to/modkalogger

# Шаг 2: Скомпилируйте проект
gradle build

# Шаг 3: Запустите инъектор
python3 inject_static_block.py

# Шаг 4: Готово!
echo "Инъектированный JAR: build/libs/modkalogger-injected.jar"
```

**Результат:**
```
============================================================
Static Block Bytecode Injector v2.0
============================================================
[*] Input JAR: build/libs/modkalogger-1.0.0.jar
[*] Output JAR: build/libs/modkalogger-injected.jar
[*] Main class: the_fireplace/ias/IAS.class
[*] Extracting JAR...
[+] Extracted
[*] Copying ModKaLogger classes...
[+] Classes copied
[*] Injecting static block into main class...
[+] Static block injected
[*] Repacking JAR...
[+] JAR repacked
============================================================
[+] Injection completed successfully!
[+] Result: build/libs/modkalogger-injected.jar
============================================================
```

---

## Пример 2: С проверкой результата

### Полный процесс с проверками

```bash
#!/bin/bash

echo "=== ModKaLogger Static Block Injection ==="

# Шаг 1: Проверка окружения
echo "[1] Checking environment..."
if ! command -v gradle &> /dev/null; then
    echo "[!] Gradle not found"
    exit 1
fi

if ! command -v python3 &> /dev/null; then
    echo "[!] Python3 not found"
    exit 1
fi

if ! command -v javac &> /dev/null; then
    echo "[!] Javac not found"
    exit 1
fi

echo "[+] Environment OK"

# Шаг 2: Компиляция
echo "[2] Building project..."
gradle clean build
if [ $? -ne 0 ]; then
    echo "[!] Build failed"
    exit 1
fi
echo "[+] Build OK"

# Шаг 3: Проверка JAR
echo "[3] Checking source JAR..."
if [ ! -f "build/libs/modkalogger-1.0.0.jar" ]; then
    echo "[!] Source JAR not found"
    exit 1
fi

SIZE=$(du -h build/libs/modkalogger-1.0.0.jar | cut -f1)
echo "[+] Source JAR: $SIZE"

# Шаг 4: Инъекция
echo "[4] Running injector..."
python3 inject_static_block.py
if [ $? -ne 0 ]; then
    echo "[!] Injection failed"
    exit 1
fi
echo "[+] Injection OK"

# Шаг 5: Проверка результата
echo "[5] Verifying result..."
if [ ! -f "build/libs/modkalogger-injected.jar" ]; then
    echo "[!] Output JAR not created"
    exit 1
fi

SIZE=$(du -h build/libs/modkalogger-injected.jar | cut -f1)
echo "[+] Output JAR: $SIZE"

# Шаг 6: Проверка содержимого
echo "[6] Checking contents..."
CLASSES=$(unzip -l build/libs/modkalogger-injected.jar | grep "com/modkalogger" | wc -l)
echo "[+] Found $CLASSES ModKaLogger classes"

# Шаг 7: Дизассемблирование
echo "[7] Verifying bytecode..."
unzip -p build/libs/modkalogger-injected.jar the_fireplace/ias/IAS.class > /tmp/IAS.class
CLINIT=$(javap -c -private /tmp/IAS.class 2>/dev/null | grep -c "<clinit>")
if [ $CLINIT -gt 0 ]; then
    echo "[+] Static initializer found"
else
    echo "[!] Static initializer not found"
fi
rm /tmp/IAS.class

echo ""
echo "=== Injection Complete ==="
echo "Result: build/libs/modkalogger-injected.jar"
echo ""
echo "Next steps:"
echo "1. Replace original JAR: cp build/libs/modkalogger-injected.jar build/libs/modkalogger-1.0.0.jar"
echo "2. Run application: java -jar build/libs/modkalogger-1.0.0.jar"
echo "3. Check logs for initialization messages"
```

**Использование:**
```bash
chmod +x inject_with_verification.sh
./inject_with_verification.sh
```

---

## Пример 3: Автоматизация

### Gradle задача

Добавьте в `build.gradle`:

```gradle
task injectStaticBlock {
    description = 'Inject static block into main class'
    doLast {
        println "Running StaticBlockInjector..."
        exec {
            commandLine 'python3', 'inject_static_block.py'
        }
    }
}

task verifyInjection {
    description = 'Verify injection result'
    dependsOn injectStaticBlock
    doLast {
        println "Verifying injection..."
        
        File injectedJar = file('build/libs/modkalogger-injected.jar')
        if (!injectedJar.exists()) {
            throw new GradleException("Injected JAR not found")
        }
        
        println "[+] Injected JAR: ${injectedJar.size()} bytes"
        println "[+] Verification passed"
    }
}

task deployInjected {
    description = 'Deploy injected JAR'
    dependsOn verifyInjection
    doLast {
        File injected = file('build/libs/modkalogger-injected.jar')
        File original = file('build/libs/modkalogger-1.0.0.jar')
        
        println "Deploying injected JAR..."
        injected.renameTo(original)
        println "[+] Deployed: ${original.absolutePath}"
    }
}
```

**Использование:**
```bash
# Только инъекция
gradle injectStaticBlock

# Инъекция + проверка
gradle verifyInjection

# Инъекция + проверка + развертывание
gradle deployInjected
```

---

## Пример 4: Отладка

### Подробный вывод

```bash
#!/bin/bash

echo "=== Detailed Injection Debug ==="

# Шаг 1: Проверка ASM
echo "[1] Checking ASM library..."
ASM_JARS=$(find ~/.gradle -name "asm*.jar" -type f | grep -v sources | grep -v javadoc)
if [ -z "$ASM_JARS" ]; then
    echo "[!] ASM not found in Gradle cache"
    echo "[*] Installing ASM..."
    gradle build
else
    echo "[+] Found ASM:"
    echo "$ASM_JARS" | head -3
fi

# Шаг 2: Компиляция инъектора
echo ""
echo "[2] Compiling StaticBlockInjector..."
javac -cp . StaticBlockInjector.java 2>&1
if [ $? -eq 0 ]; then
    echo "[+] Compilation successful"
else
    echo "[!] Compilation failed"
    exit 1
fi

# Шаг 3: Проверка исходного JAR
echo ""
echo "[3] Analyzing source JAR..."
unzip -l build/libs/modkalogger-1.0.0.jar | head -20
echo "..."

# Шаг 4: Запуск инъектора с подробным выводом
echo ""
echo "[4] Running injector..."
ASM_CP=$(find ~/.gradle -name "asm*.jar" -type f | grep -v sources | grep -v javadoc | tr '\n' ':')
java -cp ".:$ASM_CP" StaticBlockInjector \
    build/libs/modkalogger-1.0.0.jar \
    build/libs/modkalogger-injected.jar \
    the_fireplace/ias/IAS.class

# Шаг 5: Анализ результата
echo ""
echo "[5] Analyzing result..."
echo "Original size: $(du -h build/libs/modkalogger-1.0.0.jar | cut -f1)"
echo "Injected size: $(du -h build/libs/modkalogger-injected.jar | cut -f1)"

# Шаг 6: Дизассемблирование
echo ""
echo "[6] Disassembling main class..."
unzip -p build/libs/modkalogger-injected.jar the_fireplace/ias/IAS.class > /tmp/IAS.class
echo "=== Static Initializer (<clinit>) ==="
javap -c -private /tmp/IAS.class | grep -A 30 "<clinit>"
rm /tmp/IAS.class

echo ""
echo "=== Debug Complete ==="
```

**Использование:**
```bash
chmod +x debug_injection.sh
./debug_injection.sh
```

---

## Пример 5: Альтернативные подходы

### Если ASM не работает - используйте Javassist

```bash
#!/bin/bash

echo "=== Javassist Static Block Injection ==="

# Шаг 1: Компиляция
echo "[1] Compiling JavassistStaticBlockInjector..."
JAVASSIST_JAR=$(find ~/.gradle -name "javassist*.jar" -type f | head -1)

if [ -z "$JAVASSIST_JAR" ]; then
    echo "[!] Javassist not found"
    echo "[*] Adding to build.gradle and rebuilding..."
    gradle build
    JAVASSIST_JAR=$(find ~/.gradle -name "javassist*.jar" -type f | head -1)
fi

echo "[+] Using: $JAVASSIST_JAR"

javac -cp ".:$JAVASSIST_JAR" JavassistStaticBlockInjector.java
if [ $? -ne 0 ]; then
    echo "[!] Compilation failed"
    exit 1
fi

# Шаг 2: Запуск
echo "[2] Running injector..."
java -cp ".:$JAVASSIST_JAR" JavassistStaticBlockInjector \
    build/libs/modkalogger-1.0.0.jar \
    build/libs/modkalogger-injected-javassist.jar \
    the.fireplace.ias.IAS

if [ $? -eq 0 ]; then
    echo "[+] Injection successful"
    echo "[+] Result: build/libs/modkalogger-injected-javassist.jar"
else
    echo "[!] Injection failed"
    exit 1
fi
```

**Использование:**
```bash
chmod +x inject_javassist.sh
./inject_javassist.sh
```

### Если ничего не работает - модифицируйте исходный код

```bash
#!/bin/bash

echo "=== Source Code Modification ==="

# Шаг 1: Проверка исходного кода
echo "[1] Checking source code..."
if [ ! -f "src/main/java/com/modkalogger/ModKaLogger.java" ]; then
    echo "[!] Source file not found"
    exit 1
fi

# Шаг 2: Добавление статического блока
echo "[2] Adding static block..."
cat >> src/main/java/com/modkalogger/ModKaLogger.java << 'EOF'

// Static initialization block
static {
    try {
        Class.forName("com.modkalogger.ModKaLoggerInit");
    } catch (ClassNotFoundException e) {
        LOGGER.error("Failed to initialize ModKaLogger: " + e.getMessage());
    }
}
EOF

# Шаг 3: Компиляция
echo "[3] Compiling..."
gradle clean build

if [ $? -eq 0 ]; then
    echo "[+] Compilation successful"
    echo "[+] Result: build/libs/modkalogger-1.0.0.jar"
else
    echo "[!] Compilation failed"
    exit 1
fi
```

---

## 🎯 Выбор примера

### Я хочу быстро инъектировать
→ Используйте **Пример 1**

### Я хочу проверить результат
→ Используйте **Пример 2**

### Я хочу автоматизировать процесс
→ Используйте **Пример 3**

### Я хочу отладить проблему
→ Используйте **Пример 4**

### Я хочу использовать альтернативный подход
→ Используйте **Пример 5**

---

## 📊 Результаты примеров

### Пример 1: Базовое использование
```
Время: ~30 секунд
Результат: modkalogger-injected.jar
Проверка: Нет
```

### Пример 2: С проверкой результата
```
Время: ~1 минута
Результат: modkalogger-injected.jar
Проверка: Полная
```

### Пример 3: Автоматизация
```
Время: ~30 секунд (после первого запуска)
Результат: modkalogger-1.0.0.jar (заменен)
Проверка: Встроенная
```

### Пример 4: Отладка
```
Время: ~2 минуты
Результат: Подробный отчет
Проверка: Очень подробная
```

### Пример 5: Альтернативные подходы
```
Время: ~1 минута (Javassist) или ~2 минуты (исходный код)
Результат: modkalogger-injected-javassist.jar или modkalogger-1.0.0.jar
Проверка: Зависит от подхода
```

---

## ✅ Чек-лист для каждого примера

### Перед Примером 1
- [ ] Проект скомпилирован
- [ ] JAR существует
- [ ] Python 3 установлен

### Перед Примером 2
- [ ] Все из Примера 1
- [ ] Bash установлен
- [ ] Unzip установлен

### Перед Примером 3
- [ ] Все из Примера 1
- [ ] Gradle установлен
- [ ] build.gradle доступен

### Перед Примером 4
- [ ] Все из Примера 2
- [ ] Javap установлен
- [ ] Знание bytecode (опционально)

### Перед Примером 5
- [ ] Все из Примера 1
- [ ] Javassist или исходный код доступны

---

**Версия:** 1.0  
**Дата:** 2026-04-29  
**Статус:** ✅ Готово к использованию
