package atomicstryker.battletowers.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.WorldEvent;
import atomicstryker.battletowers.common.AS_WorldGenTower.TowerTypes;
import cpw.mods.fml.common.IWorldGenerator;

public class WorldGenHandler implements IWorldGenerator
{
    
    private final static String fileName = "BattletowerPositionsFile.txt";
    
    private final static WorldGenHandler instance = new WorldGenHandler();
    private HashMap<String, Boolean> biomesMap;
    private HashMap<String, Boolean> providerMap;
    private static CopyOnWriteArrayList<TowerPosition> towerPositions;
    private static World lastWorld;
    private final AS_WorldGenTower generator;
    
    public WorldGenHandler()
    {
        biomesMap = new HashMap<String, Boolean>();
        providerMap = new HashMap<String, Boolean>();
        towerPositions = new CopyOnWriteArrayList<TowerPosition>();
        generator = new AS_WorldGenTower();
        
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @ForgeSubscribe
    public void eventWorldLoad(WorldEvent.Load evt)
    {
        loadPosFile(new File(getWorldSaveDir(evt.world), fileName), evt.world);
        lastWorld = evt.world;
    }
    
    @ForgeSubscribe
    public void eventWorldSave(WorldEvent.Save evt)
    {
        flushCurrentPosListToFile(evt.world);
    }
    
    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider)
    {        
        BiomeGenBase target = world.getBiomeGenForCoords(chunkX, chunkZ);
        if (target != BiomeGenBase.hell
        && getIsBiomeAllowed(target)
        && getIsChunkProviderAllowed(chunkProvider))
        {
            if (world != lastWorld)
            {
                if (lastWorld != null)
                {
                    flushCurrentPosListToFile(lastWorld);
                }
                loadPosFile(new File(getWorldSaveDir(world), fileName), world);
                lastWorld = world;
            }
            generateSurface(world, random, chunkX*16, chunkZ*16);
        }
    }

    private boolean getIsChunkProviderAllowed(IChunkProvider chunkProvider)
    {
        String name = chunkProvider.getClass().getSimpleName();
        if (providerMap.containsKey(name))
        {
            return providerMap.get(name);
        }
        
        Configuration config = AS_BattleTowersCore.configuration;
        config.load();
        boolean result = config.get("ChunkProviderAllowed", name, true).getBoolean(true);
        config.save();
        providerMap.put(name, result);
        return result;
    }

    private boolean getIsBiomeAllowed(BiomeGenBase target)
    {
        if (biomesMap.containsKey(target.biomeName))
        {
            return biomesMap.get(target.biomeName);
        }
        
        Configuration config = AS_BattleTowersCore.configuration;
        config.load();
        boolean result = config.get("BiomeSpawnAllowed", target.biomeName, true).getBoolean(true);
        config.save();
        biomesMap.put(target.biomeName, result);
        return result;
    }

    private synchronized void generateSurface(World world, Random random, int xActual, int zActual)
    {
        TowerPosition pos = canTowerSpawnAt(world, xActual, zActual);
        if (pos != null)
        {
            int y = getSurfaceBlockHeight(world, xActual, zActual);
            if (y > 49)
            {
                pos.y = y;
                if (attemptToSpawnTower(world, pos, random, xActual, y, zActual))
                {
                    towerPositions.add(pos);
                    //System.out.println("Battle Tower spawned at [ "+xActual+" | "+zActual+" ]");
                }
                else
                {
                    // spawn failed, bugger
                }
            }
        }
    }
    
    private boolean attemptToSpawnTower(World world, TowerPosition pos, Random random, int x, int y, int z)
    {
        int choice = generator.getChosenTowerOrdinal(world, random, x, y, z);
        pos.type = choice;
        
        if (choice >= 0)
        {
            pos.underground = world.rand.nextInt(100)+1 < AS_BattleTowersCore.chanceTowerIsUnderGround;
            generator.generate(world, x, y, z, choice, pos.underground);
            return true;
        }
        
        return false;
    }
    
    public static void generateTower(World world, int x, int y, int z, int type, boolean underground)
    {
        instance.generator.generate(world, x, y, z, type, underground);
        towerPositions.add(instance.new TowerPosition(x, y, z, type, underground));
    }
    
    private int getSurfaceBlockHeight(World world, int x, int z)
    {
        int h = 50;
        
        do
        {
            h++;
        }
        while (world.getBlockId(x, h, z) != 0);
        
        return h-1;
    }
    
    public static TowerStageItemManager getTowerStageManagerForFloor(int floor, Random rand)
    {
        floor--; // subtract 1 to match the floors to the array
        
        if (floor >= AS_BattleTowersCore.floorItemManagers.length)
        {
            floor = AS_BattleTowersCore.floorItemManagers.length-1;
        }
        if (floor < 0)
        {
            floor = 0;
        }
        
        return new TowerStageItemManager(AS_BattleTowersCore.floorItemManagers[floor]);
    }
    
    private TowerPosition canTowerSpawnAt(World world, int xActual, int zActual)
    {
        ChunkCoordinates spawn = world.getSpawnPoint();
        if (Math.sqrt((spawn.posX - xActual)*(spawn.posX - xActual) + (spawn.posZ - zActual)*(spawn.posZ - zActual)) < AS_BattleTowersCore.minDistanceFromSpawn)
        {
            return null;
        }
        
        if (AS_BattleTowersCore.minDistanceBetweenTowers > 0)
        {
            int index = 0;
            double mindist = 9999f;
            TowerPosition temp;
            for (; index < towerPositions.size(); index++)
            {
                temp = towerPositions.get(index);
                int diffX = temp.x - xActual;
                int diffZ = temp.z - zActual;
                double dist = Math.sqrt(diffX*diffX + diffZ*diffZ);
                mindist = Math.min(mindist, dist);
                if (dist < AS_BattleTowersCore.minDistanceBetweenTowers)
                {
                    //System.out.printf("refusing site coords [%d,%d], mindist %f\n", xActual, zActual, mindist);
                    return null;
                }
            }
            //System.out.printf("Logged %d towers so far, new site coords [%d,%d], mindist %f\n", towerPositions.size(), xActual, zActual, mindist);
        }
        
        return new TowerPosition(xActual, 0, zActual, 0, false);
    }
    
    public class TowerPosition
    {
        int x;
        int y;
        int z;
        int type;
        boolean underground;
        
        public TowerPosition(int ix, int iy, int iz, int itype, boolean under)
        {
            x = ix;
            y = iy;
            z = iz;
            type = itype;
            underground = under;
        }
        
        @Override
        public String toString()
        {
            return x+" "+y+" "+z+" "+type+" "+underground;
        }
        
        public TowerPosition fromString(String s)
        {
            String[] data = s.split(" ");
            return new TowerPosition(Integer.valueOf(data[0]), Integer.valueOf(data[1]), Integer.valueOf(data[2]), Integer.valueOf(data[3]), Boolean.valueOf(data[4]));
        }
    }
    
    private static void loadPosFile(File file, World world)
    {
        if (!file.getAbsolutePath().contains(world.getWorldInfo().getWorldName()))
        {
            return;
        }
        
        towerPositions.clear();
        
        try
        {
            if (!file.exists())
            {
                file.createNewFile();
            }
            int lineNumber = 1;
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = br.readLine();
            TowerPosition tp = instance.new TowerPosition(0, 0, 0, 0, false);
            while (line != null)
            {
                line = line.trim();
                if (!line.startsWith("#"))
                {
                    try
                    {
                        towerPositions.add(tp.fromString(line));
                    }
                    catch (Exception e)
                    {
                        System.err.println("Battletowers positions file is invalid in line "+lineNumber+", skipping...");
                    }
                }
                
                lineNumber++;
                line = br.readLine();
            }
            br.close();
            System.out.println("Battletower Positions reloaded. Lines "+lineNumber+", entries "+towerPositions.size());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private synchronized static void flushCurrentPosListToFile(World world)
    {
        if (towerPositions.isEmpty() || world.getWorldInfo().getWorldName().equals("MpServer"))
        {
            return;
        }
        
        File file = new File(getWorldSaveDir(world), fileName);
        if (file.exists())
        {
            file.delete();
        }
        
        try
        {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            pw.println("# Behold! The Battletower position management file. Below, you see all data accumulated by AtomicStrykers Battletowers during the last run of this World.");
            pw.println("# Data is noted as follows: Each line stands for one successfull Battletower spawn. Data syntax is:");
            pw.println("# xCoordinate yCoordinate zCoordinate towerType towerUnderground");
            pw.println("# everything but the last value is an integer value. Towertypes values are:");
            pw.println("# 0: Null, 1: Cobblestone, 2: Mossy Cobblestone, 3: Sandstone, 4: Ice, 5: Smoothstone, 6: Nether, 7: Jungle");
            pw.println("#");
            pw.println("# DO NOT EDIT THIS FILE UNLESS YOU ARE SURE OF WHAT YOU ARE DOING");
            pw.println("#");
            pw.println("# the primary function of this file is to enable regeneration or removal of spawned Battletowers.");
            pw.println("# that is possible via commands /regenerateallbattletowers and /deleteallbattletowers.");
            pw.println("# do not change values once towers have spawned! Either do that before creating a World (put this file in a world named folder)...");
            pw.println("# ... or use /deletebattletowers, exit the game, modify this file any way you want, load the world, then use /regeneratebattletowers!");
            
            for (TowerPosition t : towerPositions)
            {
                pw.println(t.toString());
            }
            
            pw.flush();
            pw.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    
    private static File getWorldSaveDir(World world)
    {
        ISaveHandler worldsaver = world.getSaveHandler();
        
        if (worldsaver.getChunkLoader(world.provider) instanceof AnvilChunkLoader)
        {
            AnvilChunkLoader loader = (AnvilChunkLoader) worldsaver.getChunkLoader(world.provider);
            
            for (Field f : loader.getClass().getDeclaredFields())
            {
                if (f.getType().equals(File.class))
                {
                    try
                    {
                        f.setAccessible(true);
                        File saveLoc = (File) f.get(loader);
                        return saveLoc;
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        return null;
    }

    public static TowerPosition deleteNearestTower(World world, int x, int z)
    {
        int index = -1;
        double lowestDist = 9999d;
        TowerPosition tp = null;
        
        for (int i = 0; i < towerPositions.size(); i++)
        {
            tp = towerPositions.get(i);
            double dist = Math.sqrt((tp.x - x)*(tp.x - x) + (tp.z - z)*(tp.z - z));
            if (dist < lowestDist)
            {
                index = i;
                lowestDist = dist;
            }
        }
        
        if (index > 0)
        {
            tp = towerPositions.get(index);
            instance.generator.generate(world, tp.x, tp.y, tp.z, TowerTypes.Null.ordinal(), tp.underground);
            towerPositions.remove(index);
        }
        return tp;
    }

    public static void deleteAllTowers(World world, boolean regenerate)
    {
        if (world != lastWorld)
        {
            flushCurrentPosListToFile(lastWorld);
            loadPosFile(new File(getWorldSaveDir(world), fileName), world);
            lastWorld = world;
        }
        
        for (Object o : world.loadedEntityList)
        {
            if (o instanceof AS_EntityGolem)
            {
                ((Entity)o).setDead();
            }
        }
        
        for (TowerPosition tp : towerPositions)
        {
            instance.generator.generate(world, tp.x, tp.y, tp.z, regenerate ? tp.type : TowerTypes.Null.ordinal(), tp.underground);
        }
        
        if (!regenerate)
        {
            towerPositions.clear();
        }
    }

}
