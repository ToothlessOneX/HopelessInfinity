package com.hopeless.infinity.town;

import com.hopeless.infinity.HopelessInfinity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 0.26.0: sounds the ported machines play (assets/hopelessinfinity/sounds.json, from Hopeless Deep). */
public final class CitySounds {
    private CitySounds() {}
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HopelessInfinity.MODID);
    static RegistryObject<SoundEvent> sound(String id) { return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HopelessInfinity.MODID, id))); }
    public static final RegistryObject<SoundEvent> ENGINE_DIESEL = sound("engine_diesel");
}
