package com.jfant.specter.core.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractClientPlayer.class})
public interface AbstractClientPlayerEntityAccessor {
   @Accessor("playerInfo")
   void meteor$setPlayerListEntry(PlayerInfo var1);
}
