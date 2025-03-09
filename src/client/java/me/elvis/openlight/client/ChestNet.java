package me.elvis.openlight.client;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
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
 */

public class ChestNet extends Command
{
    private final Set<BlockPos> openedChests = ConcurrentHashMap.newKeySet();
    public static final Logger LOGGER = LoggerFactory.getLogger("openlight");
    private final ScheduledExecutorService openChestScheduler = Executors.newSingleThreadScheduledExecutor();
    // Need to get the parent of the users dir to prevent it from looking into run for some reason
    private static final Path ABSPATH = Paths.get(System.getProperty("user.dir")).getParent().resolve("Spawner Info/Test.csv").normalize();
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ChestNet(IBaritone baritone)
    {
        super(baritone, "ChestNet");

    }

    @Override
    public void execute(String label, IArgConsumer args)
    {
        List<String[]> a = csvReader();
        LOGGER.info(String.valueOf(ABSPATH));
        LOGGER.info(Arrays.toString(a.get(0)));

        pathing(a, 0, scheduler);

    }

    public void pathing(List<String[]> a, int index, ScheduledExecutorService scheduler)
    {
        BaritoneAPI.getSettings().allowSprint.value = true;
        BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;
        BaritoneAPI.getSettings().allowDownward.value = true;
        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().chatDebug.value = true;
        BaritoneAPI.getSettings().allowDiagonalDescend.value = true;
        BaritoneAPI.getSettings().allowDiagonalAscend.value = true;

        if (index >= a.size())
        {
            LOGGER.info("Finished pathing; Index is done");
            scheduler.shutdown();
            return;
        }

        String[] mainArray = a.get(index);

        int x = Integer.parseInt(mainArray[0]);
        int y = Integer.parseInt(mainArray[1]);
        int z = Integer.parseInt(mainArray[2]);

        LOGGER.info("x: " + x + " y: " + y + " z: " + z);

        Goal goal = new GoalXZ(x, z);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);


        scheduler.scheduleAtFixedRate(() ->
        {

            Vec3d playerPos = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerFeetAsVec();
            boolean isAtGoal = goal.isInGoal(BlockPos.ofFloored(playerPos));

            // we want to check to make sure that, we have reached the goal 100% no mistakes
            if (isAtGoal || !BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive()
                    || !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing())
            {
                LOGGER.info("Goal reached at (" + x + ", " + z + ")!");

                searchForSpawner(x, y, z);

                if (searchForChest(x, y, z))
                {
                    List<int[]> chestLocationsArray = getChestLocation(x, y, z);
                    pathToChest(chestLocationsArray, 0, () -> pathing(a, index+1, scheduler));
                }

            }
            else
            {
                LOGGER.info("Still pathing...");
            }
        }, 0, 10, TimeUnit.SECONDS);

    }

    public void pathToChest(List<int[]> chestArray, int index, Runnable onComplete)
    {
        // I dont know if these are global settings for B I would assume so but... just in case
        BaritoneAPI.getSettings().allowSprint.value = true;
        BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;
        BaritoneAPI.getSettings().allowDownward.value = true;
        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().chatDebug.value = true;
        BaritoneAPI.getSettings().allowDiagonalDescend.value = true;
        BaritoneAPI.getSettings().allowDiagonalAscend.value = true;
        BaritoneAPI.getSettings().allowInventory.value = true;


        if (index >= chestArray.size())
        {
            LOGGER.info("Chest Path Complte");
            onComplete.run();
            return;
        }

        LOGGER.info("Chest spawner locations: " +
                chestArray.stream()
                        .map(Arrays::toString)
                        .collect(Collectors.joining(", ")));


        int[] singleChestArray =chestArray.get(index);
        LOGGER.info(Arrays.toString(singleChestArray));

        int chestX = (singleChestArray[0]);
        int chestY = (singleChestArray[1]);
        int chestZ = (singleChestArray[2]);

        LOGGER.info("CHEST X LOCATION TEST: " + chestX);
        Goal newGoal = new GoalBlock(chestX, chestY+1, chestZ);

        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(newGoal);

        ScheduledExecutorService chestScheduler = Executors.newSingleThreadScheduledExecutor();
        chestScheduler.scheduleAtFixedRate(() -> {

        Vec3d playerPos = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerFeetAsVec();
        boolean isAtGoal = newGoal.isInGoal(BlockPos.ofFloored(playerPos));


        // we want to check to make sure that, we have reached the goal 100% no mistakes
        if (isAtGoal || !BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive()
                || !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing())
        {

            openAndSearchChest(chestX, chestY, chestZ);
            LOGGER.info("Chest Reached at: " + playerPos);
            chestScheduler.shutdown();
            //pathToChest(chestArray, index + 1, onComplete);
        }
        else
        {
            LOGGER.info("Still Pathing");
        }
    }, 0, 10, TimeUnit.SECONDS);

    }

    public void openAndSearchChest(int x, int y, int z)
    {
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

                        // FIX: Read from the player's open screen handler instead of block entity
                        ScreenHandler screenHandler = player.currentScreenHandler;
                        if (screenHandler instanceof GenericContainerScreenHandler) {
                            List<ItemStack> stacks = screenHandler.getStacks();
                            boolean hasShulker = false;

                            for (ItemStack stack : stacks) {
                                LOGGER.info(stack.toString());

                                if (!stack.isEmpty() && stack.getItem() instanceof BlockItem &&
                                        ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                                    hasShulker = true;
                                    break;
                                }
                            }

                            if (hasShulker) {
                                LOGGER.info("\n\n\n\nChest contains a Shulker Box!\n\n\n");
                            }
                        } else {
                            LOGGER.warn("Failed to read chest contents - screen handler is not a GenericContainerScreenHandler.");
                        }

                        // FIX: Close screen on the render thread
                        MinecraftClient.getInstance().execute(() -> {
                            player.closeHandledScreen();
                        });

                    });
                }, 2, TimeUnit.SECONDS); // Adjust delay if needed
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

    public List<String[]> csvReader()
    {
        String line = "";
        String splitBy = ",";
        ArrayList<String[]> fullList = new ArrayList<>();
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(String.valueOf(ABSPATH)));
            while ((line = br.readLine()) != null) {

                String[] coords = line.split(splitBy);
                fullList.add(coords);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return fullList;
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