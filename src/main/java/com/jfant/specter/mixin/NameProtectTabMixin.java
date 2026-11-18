package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({PlayerTabOverlay.class})
public abstract class NameProtectTabMixin {
   @ModifyReturnValue(
      method = {"getNameForDisplay"},
      at = {@At("RETURN")}
   )
   private Component specter$protectTabName(Component original) {
      return NameProtectPlus.protect(original);
   }

   @ModifyArg(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         ordinal = 0,
         target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
      ),
      index = 0
   )
   private FormattedText specter$protectTabHeader(FormattedText header) {
      return NameProtectPlus.protectText(header);
   }

   @ModifyArg(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         ordinal = 1,
         target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"
      ),
      index = 0
   )
   private FormattedText specter$protectTabFooter(FormattedText footer) {
      return NameProtectPlus.protectText(footer);
   }

   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"
      )}
   )
   private PlayerSkin specter$protectTabHead(PlayerInfo entry, Operation<PlayerSkin> original) {
      PlayerSkin real = (PlayerSkin)original.call(new Object[]{entry});
      return NameProtectPlus.hidesName(entry.getProfile().name()) ? NameProtectPlus.hiddenHead(entry.getProfile().id()) : real;
   }
}
