package com.jfant.specter.core.mixin;

import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.client.ResourceLoadStateTracker.ReloadState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ResourceLoadStateTracker.class})
public interface ResourceReloadLoggerAccessor {
   @Accessor("reloadState")
   ReloadState meteor$getReloadState();
}
