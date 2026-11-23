package com.jfant.specter.mixin;

import com.jfant.specter.modules.DonutPhone;
import com.jfant.specter.phone.PhoneOS;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Donut Phone: the vanilla Open Chat key (whatever the player bound it to) takes out the phone instead. Only the
 * chat key's call is wrapped (ordinal 0); the command key's call (ordinal 1) still opens chat with "/".
 */
@Mixin(Gui.class)
public abstract class PhoneChatKeyMixin {
   @WrapOperation(
      method = "handleKeybinds",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;openChatScreen(Lnet/minecraft/client/gui/components/ChatComponent$ChatMethod;)V", ordinal = 0)
   )
   private void specter$phoneInsteadOfChat(Gui gui, ChatComponent.ChatMethod method, Operation<Void> original) {
      DonutPhone phone = PhoneOS.started() ? PhoneOS.module() : null;
      if (phone != null && phone.isActive() && phone.opensWithChatKey()) {
         PhoneOS.get().open();
      } else {
         original.call(gui, method);
      }
   }
}
