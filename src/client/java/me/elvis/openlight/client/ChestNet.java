package me.elvis.openlight.client;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.Spawner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.SpawnerMinecartEntity;
import net.minecraft.util.math.BlockPos;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    public static final Logger LOGGER = LoggerFactory.getLogger("openlight");

    // Need to get the parent of the users dir to prevent it from looking into run for some reason
    private static final Path ABSPATH = Paths.get(System.getProperty("user.dir")).getParent().resolve("Spawner Info/Test.csv").normalize();

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
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        pathing(a, 0, scheduler);
        }
    public void pathing(List<String[]> a, int index, ScheduledExecutorService scheduler)
    {
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


        BaritoneAPI.getSettings().allowSprint.value = true;
        BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;

        LOGGER.info("x: " + x + " y: " + y + " z: " + z);

        Goal goal = new GoalXZ(x, z);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);


        scheduler.scheduleAtFixedRate(() ->
        {

            Vec3d playerPos = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().playerFeetAsVec();
            boolean isAtGoal = goal.isInGoal(BlockPos.ofFloored(playerPos));


            if (isAtGoal || !BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive()
                    && !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing())
            {
                LOGGER.info("Goal reached at (" + x + ", " + z + ")!");

                searchForSpawner(x, y, z);
                searchAndCheckChest(x, y, z);

                pathing(a, index + 1, scheduler);
            }
            else
            {
                LOGGER.info("Still pathing...");
            }
        }, 0, 5, TimeUnit.SECONDS);

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
        return isThereSpawner;

    }


    public boolean searchAndCheckChest(int x, int y, int z)
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