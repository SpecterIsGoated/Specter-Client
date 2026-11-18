package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.misc.FakeClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractClientPlayer.class})
public abstract class AbstractClientPlayerEntityMixin {
   @Inject(
      method = {"getPlayerInfo"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetPlayerListEntry(CallbackInfoReturnable<PlayerInfo> info) {
      if (SpecterCore.mc.getConnection() == null) {
         info.setReturnValue(FakeClientPlayer.getPlayerListEntry());
      }
   }
}
