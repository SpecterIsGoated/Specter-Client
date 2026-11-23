package com.jfant.specter.mixin;

import com.jfant.specter.modules.JetMech;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Options;
import net.minecraft.server.level.ClientInformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Jet Mech broadcast: sets the unused top bit of the skin-customisation byte so other Specter clients know to draw you
 * as a mech. Vanilla only reads bits 0-6 (cape, jacket, sleeves, trousers, hat), so nothing changes for anyone else.
 */
@Mixin(Options.class)
public class MechOptionsMixin {
   @ModifyReturnValue(method = "buildPlayerInformation", at = @At("RETURN"))
   private ClientInformation specter$mechBit(ClientInformation info) {
      if (!JetMech.broadcasting()) return info;
      return new ClientInformation(
         info.language(),
         info.viewDistance(),
         info.chatVisibility(),
         info.chatColors(),
         info.modelCustomisation() | JetMech.BROADCAST_BIT,
         info.mainHand(),
         info.textFilteringEnabled(),
         info.allowsListing(),
         info.particleStatus()
      );
   }
}
