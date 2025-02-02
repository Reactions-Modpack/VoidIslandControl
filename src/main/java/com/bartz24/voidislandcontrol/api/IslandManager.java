package com.bartz24.voidislandcontrol.api;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.bartz24.voidislandcontrol.References;
import com.bartz24.voidislandcontrol.VoidIslandControl;
import com.bartz24.voidislandcontrol.config.ConfigOptions;
import com.google.common.base.Strings;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandGive;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IslandManager {
    public static ArrayList<IslandGen> IslandGenerations = new ArrayList<IslandGen>();

    public static ArrayList<IslandPos> CurrentIslandsList = new ArrayList<IslandPos>();

    public static ArrayList<String> spawnedPlayers = new ArrayList<String>();

    public static boolean worldOneChunk = false;
    public static boolean worldLoaded = false;
    public static int initialIslandDistance = ConfigOptions.islandSettings.islandDistance;

    public static void registerIsland(IslandGen gen) {
        IslandGenerations.add(gen);
    }

    public static List<String> getIslandGenTypes() {
        List<String> types = new ArrayList<String>();
        for (IslandGen g : IslandGenerations)
            types.add(g.Identifier);

        return types;
    }

    public static int getIndexOfIslandType(String type) {
        for (int i = 0; i < IslandGenerations.size(); i++)
            if (IslandGenerations.get(i).Identifier.equals(type))
                return i;
        return -1;
    }

    public static IslandPos getNextIsland() {
        int size = (int) Math.floor(Math.sqrt(CurrentIslandsList.size()));
        if (size % 2 == 0 && size > 0)
            size--;

        size = (size + 1) / 2;
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                if (!hasPosition(x, z)) {
                    return new IslandPos(x, z);
                }
            }
        }
        return null;
    }

    public static IslandPos getPlayerIsland(UUID playerUUID) {
        for (IslandPos pos : CurrentIslandsList) {
            if (pos.getPlayerUUIDs().contains(playerUUID.toString()))
                return pos;
        }
        return null;
    }

    public static IslandPos getIslandAtPos(int x, int y) {
        for (IslandPos pos : CurrentIslandsList) {
            if (pos.getX() == x && pos.getY() == y)
                return pos;
        }
        return null;
    }

    public static List<String> getPlayerNames(World world) {
        List<String> names = new ArrayList();
        for (IslandPos pos : CurrentIslandsList) {
            for (String s : pos.getPlayerUUIDs())

                names.add(world.getPlayerEntityByUUID(UUID.fromString(s)).getName());
        }
        return names;
    }

    public static boolean hasPosition(int x, int y) {
        for (IslandPos pos : CurrentIslandsList) {
            if (pos.getX() == x && pos.getY() == y)
                return true;
        }

        return false;
    }

    public static boolean playerHasIsland(UUID playerUUID) {
        for (IslandPos pos : CurrentIslandsList) {
            if (pos.getPlayerUUIDs().contains(playerUUID.toString()))
                return true;
        }

        return false;
    }

    public static void addPlayer(UUID playerUUID, IslandPos posAdd) {
        for (IslandPos pos : CurrentIslandsList) {
            if (pos.getX() == posAdd.getX() && pos.getY() == posAdd.getY()) {
                pos.addNewPlayer(playerUUID);
                return;
            }
        }
    }

    public static void removePlayer(UUID playerUUID) {
        IslandPos pos = getPlayerIsland(playerUUID);
        pos.removePlayer(playerUUID);
    }

    public static boolean hasPlayerSpawned(UUID playerUUID) {
        return spawnedPlayers.contains(playerUUID.toString());
    }

    public static void setStartingInv(EntityPlayerMP player) {
        if (ConfigOptions.islandSettings.resetInventory) {
            player.inventory.clear();
            int invSize = player.inventory.getSizeInventory();

            for (String stackString : ConfigOptions.islandSettings.startingItems) {
                Pair<Integer, ItemStack> pair = fromString(player, stackString);

                if (pair.getLeft() >= 0 && !pair.getRight().isEmpty()) {
                    if (pair.getLeft() < invSize) player.inventory.setInventorySlotContents(pair.getLeft(), pair.getRight());
                    else if (Loader.isModLoaded(References.BAUBLES)) {
                        IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
                        baubles.setStackInSlot(pair.getLeft() - invSize + 1, pair.getRight());
                    }
                }
            }
        }
    }

    public static void tpPlayerToPos(EntityPlayer player, BlockPos pos, IslandPos islandPos) {

        if (getSpawnOffset(islandPos) != null) {
            pos = pos.add(getSpawnOffset(islandPos));
        } else
            pos = pos.add(getSpawnOffset(IslandManager.CurrentIslandsList.get(0)));

        if (ConfigOptions.islandSettings.forceSpawn) {
            if (!player.getEntityWorld().isAirBlock(pos) && !player.getEntityWorld().isAirBlock(pos.up())) {
                pos = player.getEntityWorld().getTopSolidOrLiquidBlock(pos.up(2));

                player.sendMessage(new TextComponentString("Failed to spawn. Sent to top block of platform spawn."));
            }
        }
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, ConfigOptions.islandSettings.buffTimer, 20, false, false));
        player.extinguish();
        player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, ConfigOptions.islandSettings.buffTimer, 20, false, false));
        player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, ConfigOptions.islandSettings.buffTimer, 20, false, false));


        if (player.dimension != ConfigOptions.worldGenSettings.baseDimension && player instanceof EntityPlayerMP)
            player.getServer().getPlayerList().transferPlayerToDimension((EntityPlayerMP) player, ConfigOptions.worldGenSettings.baseDimension,
                    new VICTeleporter(player.getServer().getWorld(ConfigOptions.worldGenSettings.baseDimension),
                            pos.getX() + 0.5f, pos.getY() + 2.6f, pos.getZ() + 0.5f));
        else
            player.setPositionAndUpdate(pos.getX() + 0.5, pos.getY() + 2.6, pos.getZ() + 0.5);
    }

    public static BlockPos getSpawnOffset(IslandPos islandPos) {
        if (islandPos == null)
            return null;
        if (islandPos.getX() == 0 && islandPos.getY() == 0) {
            if (ConfigOptions.islandSettings.islandMainSpawnType.equals("bedrock") || ConfigOptions.islandSettings.islandMainSpawnType.equals("random"))
                return new BlockPos(0, 7, 0);
            else if (getIndexOfIslandType(ConfigOptions.islandSettings.islandMainSpawnType) != -1)
                return IslandGenerations.get(getIndexOfIslandType(ConfigOptions.islandSettings.islandMainSpawnType)).spawnOffset;
            else
                return new BlockPos(0, 0, 0);
        }
        return IslandGenerations.get(getIndexOfIslandType(islandPos.getType())).spawnOffset;
    }

    public static void tpPlayerToPosSpawn(EntityPlayer player, BlockPos pos, IslandPos islandPos) {
        tpPlayerToPos(player, pos, islandPos);

        if (getSpawnOffset(islandPos) != null) {
            pos = pos.add(getSpawnOffset(islandPos));
        } else
            pos = pos.add(getSpawnOffset(IslandManager.CurrentIslandsList.get(0)));

        player.setSpawnPoint(pos, true);
    }

    public static void setVisitLoc(EntityPlayer player, int x, int y) {
        NBTTagCompound persist = setPlayerData(player);

        persist.setInteger("VICVisitX", x);
        persist.setInteger("VICVisitY", y);
    }

    public static void removeVisitLoc(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        persist.removeTag("VICVisitX");
        persist.removeTag("VICVisitY");
    }

    public static boolean hasVisitLoc(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return persist.hasKey("VICVisitX") && persist.hasKey("VICVisitY");
    }

    public static IslandPos getVisitLoc(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return hasVisitLoc(player) ? new IslandPos(persist.getInteger("VICVisitX"), persist.getInteger("VICVisitY"))
                : null;
    }

    public static void setJoinLoc(EntityPlayer player, int x, int y) {
        NBTTagCompound persist = setPlayerData(player);

        persist.setInteger("VICJoinX", x);
        persist.setInteger("VICJoinY", y);
        persist.setInteger("VICJoinTime", 400);
    }

    public static void setLeaveConfirm(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        persist.setInteger("VICLeaveTime", 400);
    }

    public static void removeJoinLoc(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        persist.removeTag("VICJoinX");
        persist.removeTag("VICJoinY");
        persist.removeTag("VICJoinTime");
    }

    public static void removeLeaveConfirm(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        persist.removeTag("VICLeaveTime");
    }

    public static boolean hasJoinLoc(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return persist.hasKey("VICJoinX") && persist.hasKey("VICJoinY");
    }

    public static boolean hasLeaveConfirm(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return persist.hasKey("VICLeaveTime");
    }

    public static IslandPos getJoinLoc(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return hasJoinLoc(player) ? new IslandPos(persist.getInteger("VICJoinX"), persist.getInteger("VICJoinY"))
                : null;
    }

    public static int getJoinTime(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return hasJoinLoc(player) ? persist.getInteger("VICJoinTime") : -1;
    }

    public static int getLeaveTime(EntityPlayer player) {
        NBTTagCompound persist = setPlayerData(player);

        return hasLeaveConfirm(player) ? persist.getInteger("VICLeaveTime") : -1;
    }

    public static void setJoinTime(EntityPlayer player, int val) {
        NBTTagCompound persist = setPlayerData(player);

        persist.setInteger("VICJoinTime", val);
    }

    public static void setLeaveTime(EntityPlayer player, int val) {
        NBTTagCompound persist = setPlayerData(player);

        persist.setInteger("VICLeaveTime", val);
    }

    public static NBTTagCompound setPlayerData(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG))
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static Pair<Integer, ItemStack> fromString(EntityPlayer player, String s) {
        if (!Strings.isNullOrEmpty(s) && s.contains(":") && s.contains("*")) {
            String nbt = s.contains("#") ? s.split("#")[1] : null;
            int slot = Integer.parseInt(s.split("@")[0]);

            String shit0 = s.contains("#") ? s.split("#")[0] : s;
            String shit = shit0.split("@")[1].split("\\*")[0];
            String itemName = shit.split(":")[0] + ":" + shit.split(":")[1];
            int meta = Integer.parseInt(shit.split(":")[2]);

            int amount = Integer.parseInt(shit0.split("\\*")[1]);

            Item item;

            try {
                item = CommandGive.getItemByText(player, itemName);
            } catch (NumberInvalidException e) {
                VoidIslandControl.logger.error("Can't get the item: " + itemName, e);
                return Pair.of(-1, ItemStack.EMPTY);
            }

            NBTTagCompound tag = null;

            try {
                if (nbt != null) tag = JsonToNBT.getTagFromJson(nbt);
            } catch (NBTException e) {
                VoidIslandControl.logger.error("Can't get the nbt", e);
            }

            ItemStack stack = new ItemStack(item, 1, meta);
            stack.setCount(Math.min(amount, stack.getMaxStackSize()));

            if (tag != null) stack.setTagCompound(tag);

            return Pair.of(slot, stack);
        }

        return Pair.of(-1, ItemStack.EMPTY);
    }
}
