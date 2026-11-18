package com.jfant.specter.core.mixininterface;

import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.world.entity.Entity;

public interface IPlayerInteractEntityC2SPacket {
   ActionType meteor$getType();

   Entity meteor$getEntity();
}
