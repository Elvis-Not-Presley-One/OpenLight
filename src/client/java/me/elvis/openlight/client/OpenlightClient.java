package me.elvis.openlight.client;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;


public class OpenlightClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("openlight");

    @Override
    public void onInitializeClient()
    {

        ChestNet net = new ChestNet(BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getBaritone());
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().register(net);


    }


}
