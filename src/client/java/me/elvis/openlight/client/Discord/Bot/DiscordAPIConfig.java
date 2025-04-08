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

    //Note stored into the system enviroment to hide both channel and api token
    // only certin systems are allowed to run the bot for this specfic bot
    // others if they want to run the bot they can add their own discord token and also channel id
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


