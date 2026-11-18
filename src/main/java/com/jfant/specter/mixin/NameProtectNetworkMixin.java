package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class NameProtectNetworkMixin {
   @Inject(
      method = {"handlePlayerInfoUpdate"},
      at = {@At("TAIL")}
   )
   private void specter$protectPlayerList(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
      NameProtectPlus.onPlayerInfoApplied(packet);
   }

   @ModifyExpressionValue(
      method = {"handlePlayerCombatKill"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;message()Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component specter$protectDeathMessage(Component message) {
      return NameProtectPlus.protect(message);
   }
}
