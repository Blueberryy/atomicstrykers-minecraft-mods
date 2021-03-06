package atomicstryker.petbat.common;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import java.util.UUID;

public class ItemPocketedPetBat extends Item {

    protected ItemPocketedPetBat() {
        super((new Item.Properties()).maxStackSize(1).maxDamage(28).group(ItemGroup.COMBAT));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (world.isRemote) {
            PetBatMod.proxy.displayGui(itemStack);
        }

        return new ActionResult<>(ActionResultType.PASS, itemStack);
    }

    @Override
    public boolean getIsRepairable(ItemStack batStack, ItemStack repairStack) {
        return false;
    }

    @Override
    public boolean shouldSyncTag() {
        return true;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return stack.getTag() != null && PetBatMod.instance().getLevelFromExperience(stack.getOrCreateChildTag("petbatmod").getInt("BatXP")) > 5;
    }

    public static ItemStack fromBatEntity(EntityPetBat batEnt) {
        if (batEnt.world.isRemote) {
            return ItemStack.EMPTY;
        }

        ItemStack batstack = new ItemStack(PetBatMod.instance().itemPocketedBat);
        batstack.setDisplayName(batEnt.getDisplayName());
        writeCompoundStringToItemStack(batstack, "petbatmod", "Owner", batEnt.getOwnerUUID() == null ? "null" : batEnt.getOwnerUUID().toString());
        writeCompoundIntegerToItemStack(batstack, "petbatmod", "BatXP", batEnt.getBatExperience());
        writeCompoundFloatToItemStack(batstack, "petbatmod", "health", batEnt.getHealth());
        batstack.getOrCreateChildTag("petbatmod").putFloat("health", batEnt.getHealth());
        batstack.setDamage((int) invertHealthValue(batEnt.getHealth(), batEnt.getMaxHealth()));
        return batstack;
    }

    public static EntityPetBat toBatEntity(World world, ItemStack batStack, PlayerEntity player) {
        EntityPetBat batEnt = new EntityPetBat(world);
        String owner = batStack.getTag() != null ? batStack.getOrCreateChildTag("petbatmod").getString("Owner") : player.getUniqueID().toString();
        String name = batStack.getDisplayName().getUnformattedComponentText();
        int xp = batStack.getTag() != null ? batStack.getOrCreateChildTag("petbatmod").getInt("BatXP") : 0;
        if (name.equals("")) {
            name = "Battus Genericus";
        }
        if (owner.isEmpty() || owner.equals("null")) {
            batEnt.setNames(player.getUniqueID(), name);
            batEnt.setOwnerEntity(player);
        } else {
            PetBatMod.LOGGER.debug("about to load UUID from owner string [{}]", owner);
            batEnt.setNames(UUID.fromString(owner), name);
            batEnt.setOwnerEntity(player);
        }

        batEnt.setBatExperience(xp);
        batEnt.setHealth(batStack.getTag() != null ? batStack.getOrCreateChildTag("petbatmod").getFloat("health") : batEnt.getMaxHealth());
        return batEnt;
    }

    /**
     * @param input value to invert
     * @param max   maximum health value
     * @return inverted value
     */
    public static double invertHealthValue(double input, double max) {
        return Math.abs(input - max);
    }

    public static void writeCompoundIntegerToItemStack(ItemStack stack, String tag, String key, int data) {
        stack.getOrCreateChildTag(tag).putInt(key, data);
    }

    public static void writeCompoundFloatToItemStack(ItemStack stack, String tag, String key, float data) {
        stack.getOrCreateChildTag(tag).putFloat(key, data);
    }

    public static ItemStack writeCompoundStringToItemStack(ItemStack stack, String tag, String key, String data) {
        stack.getOrCreateChildTag(tag).putString(key, data);
        return stack;
    }

    @Override
    public ITextComponent getDisplayName(ItemStack itemStack) {
        return new TranslationTextComponent(TextFormatting.DARK_PURPLE + super.getDisplayName(itemStack).getUnformattedComponentText());
    }
}