package com.mcjty.rftools.items.teleportprobe;

import com.mcjty.rftools.RFTools;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class TeleportProbeItem extends Item {

    public TeleportProbeItem() {
        setMaxStackSize(1);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 1;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            player.openGui(RFTools.instance, RFTools.GUI_TELEPORTPROBE, player.worldObj, (int) player.posX, (int) player.posY, (int) player.posZ);
            return stack;
        }
        return stack;
    }

//    @Override
//    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float sx, float sy, float sz) {
//        if (world.isRemote) {
//            player.openGui(RFTools.instance, RFTools.GUI_TELEPORTPROBE, player.worldObj, (int) player.posX, (int) player.posY, (int) player.posZ);
//            return true;
//        }
//        return true;
//    }
}