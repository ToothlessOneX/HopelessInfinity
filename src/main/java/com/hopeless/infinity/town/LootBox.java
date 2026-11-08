package com.hopeless.infinity.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 0.30.0: the 27 slots of a safe or a car trunk (filled by LootFill the first time it is opened, like a chest). */
public class LootBox extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    public LootBox(BlockPos p, BlockState s) { super(TownReg.LOOT_BOX.get(), p, s); }
    /** 0.32.0: the city's other containers (fridge, register, PO box, supply cabinet) use their own type and their block's name. */
    protected LootBox(net.minecraft.world.level.block.entity.BlockEntityType<?> t, BlockPos p, BlockState s) { super(t, p, s); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> l) { items = l; }
    @Override protected Component getDefaultName() {
        if (getBlockState().is(TownReg.WRECK.get())) return Component.translatable("container.hopelessinfinity.trunk");
        if (getBlockState().is(TownReg.SAFE.get())) return Component.translatable("container.hopelessinfinity.safe");
        return getBlockState().getBlock().getName(); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return ChestMenu.threeRows(id, inv, this); }
    @Override public int getContainerSize() { return 27; }
    @Override public void load(CompoundTag t) { super.load(t); items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY); if (!tryLoadLootTable(t)) ContainerHelper.loadAllItems(t, items); }
    @Override protected void saveAdditional(CompoundTag t) { super.saveAdditional(t); if (!trySaveLootTable(t)) ContainerHelper.saveAllItems(t, items); }
}
