package com.jfant.specter.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.phone.core.PhoneCommandEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Donut Phone: lets the phone see commands you send (only reads them), e.g. /sethome for Donut Maps. */
@Mixin(ClientPacketListener.class)
public abstract class PhoneCommandMixin {
   @Inject(method = "sendCommand", at = @At("HEAD"))
   private void specter$phoneSawCommand(String command, CallbackInfo ci) {
      SpecterCore.EVENT_BUS.post(PhoneCommandEvent.get(command));
   }
}
