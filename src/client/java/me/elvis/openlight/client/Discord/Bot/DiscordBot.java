package me.elvis.openlight.client.Discord.Bot;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.core.appender.db.jdbc.JdbcAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.transformer.ActivityStack;

public class DiscordBot {
    public static final Logger LOGGER = LoggerFactory.getLogger("openlight");
    private static final String APIKEY = DiscordAPIConfig.getAPIKey();
    private static final String ID = DiscordAPIConfig.getChannelID();

    public static JDA jda;

    public void discordBot() {
        try {
            jda = JDABuilder.createDefault(APIKEY)
                    .setStatus(OnlineStatus.ONLINE)
                    .build();
            jda.getPresence().setActivity(Activity.playing("Searching For Chest And Shulks :D"));

            LOGGER.info("BOT ONLINE and Running");

        } catch (Exception e) {
            LOGGER.error("Bot did not want to start:( ", e);
        }
    }

    public static void sendDiscordMsg(String message) {

        TextChannel channel = jda.getTextChannelById(ID);
        if (channel != null) {
            channel.sendMessage(message).queue();
        } else {
            LOGGER.error("bad Discord ID Message gone");
        }
    }


}
