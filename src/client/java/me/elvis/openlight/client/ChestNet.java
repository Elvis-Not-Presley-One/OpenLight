package me.elvis.openlight.client;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import me.elvis.openlight.client.Discord.Bot.DiscordBot;
import net.minecraft.block.*;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/*
*NOTE* All B commands need to have methods:
* tabComplete
* getShortDesc
* getLongDesc
* execute
* con with super B, String name of the command
*
*
*
*
* Need to add a baritone config method ran first in the excute
*
* also need to fix the baritone is at goal and
* replace it with a method the checks useing the
* norm of u-v
*
 */

public class ChestNet extends Command {
    private final Set<BlockPos> openedChests = ConcurrentHashMap.newKeySet();
    public static final Logger LOGGER = LoggerFactory.getLogger("openlight");
    private final ScheduledExecutorService openChestScheduler = Executors.newSingleThreadScheduledExecutor();
    // Need to get the parent of the users dir to prevent it from looking into run for some reason

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final int DELAY = 10;

    public ChestNet(IBaritone baritone) {
        super(baritone, "ChestNet");

    }

    @Override
    public void execute(String label, IArgConsumer args) {
        baritoneConfig();
        List<String[]> a = csvReader();
        DiscordBot aa = new DiscordBot();
        aa.discordBot();

        pathing(a, 0, scheduler);

    }

    public void pathing(List<String[]> a, int index, ScheduledExecutorService scheduler) {
        ArrayList<Integer> getBlockPOS = new ArrayList<>();

        if (index >= a.size()) {
            LOGGER.info("Finished pathing; Index is done");
            scheduler.shutdown();
            return;
        }

        String[] mainArray = a.get(index);

        int x = Integer.parseInt(mainArray[0]);
        int y = Integer.parseInt(mainArray[1]);
        int z = Integer.parseInt(mainArray[2]);

        getBlockPOS.add(x);
        getBlockPOS.add(y);
        getBlockPOS.add(z);


        if (Math.abs(x) <= 5000 || Math.abs(z) <= 5000)
        {
            pathing(a, index + 1, scheduler);
        }
        else {

            Goal goal = new GoalXZ(x, z);
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);


            scheduler.scheduleAtFixedRate(() ->
            {
                Vec3d playerPos = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerFeetAsVec();

                // we want to check to make sure that, we have reached the goal 100% no mistakes
                if (isBaritoneNearGoal(getBlockPOS, playerPos)) {
                    LOGGER.info("Goal reached at (" + x + ", " + z + ")!");

                    boolean isThereAChest = searchForChest(x, y, z);

                    if (isThereAChest) {
                        List<int[]> chestLocationsArray = getChestLocation(x, y, z);
                        pathToChest(chestLocationsArray, 0, () -> pathing(a, index + 1, scheduler));
                    } else {
                        pathing(a, index + 1, scheduler);
                    }

                } else {
                    LOGGER.info("Still pathing...");
                }
            }, 0, DELAY, TimeUnit.SECONDS);
        }
    }

    public void pathToChest(List<int[]> chestArray, int index, Runnable onComplete) {

        ArrayList<Integer> goalBlockPOS = new ArrayList<>();

        if (index >= chestArray.size()) {
            LOGGER.info("Chest Path Complte");
            onComplete.run();
            return;
        }

        LOGGER.info("Chest spawner locations: " +
                chestArray.stream()
                        .map(Arrays::toString)
                        .collect(Collectors.joining(", ")));


        int[] singleChestArray = chestArray.get(index);
        LOGGER.info(Arrays.toString(singleChestArray));

        int chestX = (singleChestArray[0]);
        int chestY = (singleChestArray[1]);
        int chestZ = (singleChestArray[2]);

        goalBlockPOS.add(chestX);
        goalBlockPOS.add(chestY+1);
        goalBlockPOS.add(chestZ);

        LOGGER.info("CHEST X LOCATION TEST: " + chestX);
        Goal newGoal = new GoalBlock(chestX, chestY + 1, chestZ);

        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(newGoal);

        ScheduledExecutorService chestScheduler = Executors.newSingleThreadScheduledExecutor();
        chestScheduler.scheduleAtFixedRate(() -> {

            Vec3d playerPos = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerFeetAsVec();

            if (isBaritoneNearGoal(goalBlockPOS, playerPos)) {

                chestScheduler.shutdown();

                openAndSearchChest(chestX, chestY, chestZ, () -> {
                    LOGGER.info("Finished searching chest, moving to next...");

                    int indexCheck = index + 1;
                    if (indexCheck < chestArray.size()) {
                        pathToChest(chestArray, index + 1, onComplete);
                    }
                    else
                    {
                        LOGGER.info("All chests in the room checked, running onComplete...");
                        onComplete.run();
                    }
                });
            }
        }, 0, DELAY, TimeUnit.SECONDS);
    }



    public void openAndSearchChest(int x, int y, int z, Runnable onChestChecked)
    {
        ArrayList<String> chestWithShulkerInv = new ArrayList<>();

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world != null && player != null) {
            BlockPos chestPOS = new BlockPos(x, y, z);
            BlockState chestState = world.getBlockState(chestPOS);

            if (chestState.getBlock() instanceof ChestBlock) {
                LOGGER.info("Opening Chest");

                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().interactionManager.interactBlock(player,
                            Hand.MAIN_HAND, new BlockHitResult(new Vec3d(x + .5, y + .5, z + .5),
                                    Direction.DOWN, chestPOS, false));
                });

                openChestScheduler.schedule(() -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (!world.isChunkLoaded(chestPOS)) {
                            LOGGER.warn("Chunk at {} is not loaded!", chestPOS);
                            return;
                        }

                        ScreenHandler screenHandler = player.currentScreenHandler;
                        if (screenHandler instanceof GenericContainerScreenHandler) {
                            List<ItemStack> stacks = screenHandler.getStacks();
                            boolean hasShulker = false;

                            for (ItemStack stack : stacks) {
                                LOGGER.info(stack.toString());

                                if (!stack.isEmpty() && stack.getItem() instanceof BlockItem &&
                                        ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock)
                                {
                                    chestWithShulkerInv.add(stack.getName().getString());
                                    hasShulker = true;

                                }
                            }

                            if (hasShulker)
                            {
                                String shulkerFoundMsg = " \n\n ***Shulker found! :D X: " + x + " Y: " + y + " Z: " + z +
                                        "\n Contents: *** " + String.join(", ", chestWithShulkerInv);

                                LOGGER.info("\n\n\nChest contains a Shulker Box!\n\n\n");
                                DiscordBot.sendDiscordMsg(shulkerFoundMsg);
                            }


                        } else {
                            LOGGER.warn("Failed to read chest contents - screen handler is not a GenericContainerScreenHandler.");
                        }

                        MinecraftClient.getInstance().execute(() -> {
                            player.closeHandledScreen();
                        });

                        if (onChestChecked != null)
                        {
                            onChestChecked.run();
                        }
                    });
                }, DELAY, TimeUnit.SECONDS);
            }
        }
    }



    public boolean searchForSpawner(int x, int y, int z)
    {
        BlockPos allegedSpawnerPos = new BlockPos(x, y,z );
        World world = MinecraftClient.getInstance().world;
        boolean isThereSpawner = false;

        for (BlockPos pos : BlockPos.iterate(allegedSpawnerPos.add(-5, 0, -5), allegedSpawnerPos.add(5, 5, 5)))
        {
            BlockState state = world.getBlockState(pos);

            if (state.getBlock() instanceof SpawnerBlock)
            {
                BlockEntity blockEntity = world.getBlockEntity(pos);

                if (blockEntity instanceof MobSpawnerBlockEntity)
                {

                    LOGGER.info("Spawner found at: " + pos);
                    isThereSpawner = true;
                }
            }
        }
        LOGGER.info(String.valueOf(isThereSpawner));
        return isThereSpawner;

    }

    public List<int[]> getChestLocation(int x, int y, int z) {
        BlockPos spawnerPos = new BlockPos(x, y, z);
        World world = MinecraftClient.getInstance().world;
        List<int[]> locationArray = new ArrayList<>();
        int[] blockPosArray;

        for (BlockPos pos : BlockPos.iterate(spawnerPos.add(-10, 0, -10), spawnerPos.add(10, 10, 10))) {
            BlockState state = world.getBlockState(pos);

            if (state.getBlock() instanceof ChestBlock) {

                   int chestx = pos.getX();
                   int chesty = pos.getY();
                   int chestz = pos.getZ();

                   LOGGER.info(String.valueOf(chestx));

                    blockPosArray = new int[]{chestx, chesty, chestz};
                    locationArray.add(blockPosArray);
                    LOGGER.info("Chest locations: " +
                            locationArray.stream()
                                    .map(Arrays::toString)
                                    .collect(Collectors.joining(", ")));

            }
        }
        return locationArray;
    }


    public boolean searchForChest(int x, int y, int z)
    {
        BlockPos spawnerPos = new BlockPos(x, y, z);
        World world = MinecraftClient.getInstance().world;
        boolean isThereChest = false;

        for (BlockPos pos : BlockPos.iterate(spawnerPos.add(-10, 0, -10), spawnerPos.add(10, 10, 10)))
        {
            BlockState state = world.getBlockState(pos);

            if (state.getBlock() instanceof ChestBlock)
            {
                BlockEntity blockEntity = world.getBlockEntity(pos);

                if (blockEntity instanceof ChestBlockEntity)
                {
                    LOGGER.info("Chest found at: " + pos);
                    isThereChest = true;
                }
            }
        }
        return isThereChest;
    }

    public List<String[]> csvReader() {
        String splitBy = ",";
        List<String[]> fullList = new ArrayList<>();

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("FilesSpawners.csv");

        if (inputStream == null) {
            LOGGER.info("Could not find 'FilesSpawners.csv' in resources.");
            return fullList;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] coords = line.split(splitBy);
                fullList.add(coords);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fullList;
    }


    public void baritoneConfig()
    {
        BaritoneAPI.getSettings().allowSprint.value = true;
        BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;
        BaritoneAPI.getSettings().allowDownward.value = true;
        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().chatDebug.value = true;
        BaritoneAPI.getSettings().allowDiagonalDescend.value = true;
        BaritoneAPI.getSettings().allowDiagonalAscend.value = true;
        BaritoneAPI.getSettings().allowParkour.value = true;
        BaritoneAPI.getSettings().allowPlace.value = true;
        BaritoneAPI.getSettings().allowParkourPlace.value = true;
        BaritoneAPI.getSettings().allowParkourAscend.value = true;
        BaritoneAPI.getSettings().allowVines.value = true;
    }

    public boolean isBaritoneNearGoal(ArrayList<Integer> blockPOSOfGoal, Vec3d playerPOS)
    {
        // yay more linear algebra fuck my life
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPOS);

        double vectorX = blockPOSOfGoal.get(0) - playerBlockPos.getX();
        double vectorY = blockPOSOfGoal.get(1) - playerBlockPos.getY();
        double vectorZ = blockPOSOfGoal.get(2) - playerBlockPos.getZ();

        //normaly for the norm you want to sqrt but for performance no need for it
        double getNorm = vectorX * vectorX + vectorY * vectorY + vectorZ * vectorZ;

        return getNorm <= 2.5;
    }


    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc()
    {
       return "Chest Searching Time";
    }

    @Override
    public  List<String> getLongDesc()
    {
        return Arrays.asList(
                "Blank as of now"
        );
    }

}