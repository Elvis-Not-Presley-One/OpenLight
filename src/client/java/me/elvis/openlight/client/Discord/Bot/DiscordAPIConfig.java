package me.elvis.openlight.client.Discord.Bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordAPIConfig
{
    public static final Logger LOGGER = LoggerFactory.getLogger("openlight");

    public static String getAPIKey()
    {
        String apiKey = System.getenv("DISCORD_BOT_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.info("ERROR NO DISCORD API KEY FOUND");
            return null;
        }

        return apiKey;
    }
    public static String getChannelID()
    {
        String ID = System.getenv("DISCORD-CHANNEL-ID");

        if (ID == null || ID.isEmpty()) {
            LOGGER.info("ERROR NO DISCORD ID FOUND");
            return null;
        }

        return ID;
    }
}


