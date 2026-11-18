package com.jfant.specter.mixin;

import com.jfant.specter.modules.NameProtectPlus;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({GuiMessage.class})
public abstract class NameProtectChatMixin {
   @ModifyArg(
      method = {"splitLines"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"
      ),
      index = 0
   )
   private FormattedText specter$protectChatLine(FormattedText text) {
      return NameProtectPlus.protectText(text);
   }
}
