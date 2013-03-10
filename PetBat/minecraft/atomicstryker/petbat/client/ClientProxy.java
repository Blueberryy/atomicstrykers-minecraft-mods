package atomicstryker.petbat.client;

import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import atomicstryker.petbat.common.EntityPetBat;
import atomicstryker.petbat.common.IProxy;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;

public class ClientProxy implements IProxy
{
    
    @Override
    public void onModLoad()
    {
        RenderingRegistry.registerEntityRenderingHandler(EntityPetBat.class, new RenderPetBat());
    }

    @Override
    public void displayGui(ItemStack itemStack)
    {
        FMLClientHandler.instance().getClient().displayGuiScreen(new GuiPetBatRename(itemStack));
    }
    
}
