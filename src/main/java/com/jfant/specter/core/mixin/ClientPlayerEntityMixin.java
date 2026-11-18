package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.DropItemsEvent;
import com.jfant.specter.core.events.entity.player.PlayerTickMovementEvent;
import com.jfant.specter.core.events.entity.player.SendMovementPacketsEvent;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LocalPlayer.class})
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayer {
   @Shadow
   public ClientInput input;

   public ClientPlayerEntityMixin(ClientLevel world, GameProfile profile) {
      super(world, profile);
   }

   @Inject(
      method = {"drop"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDropSelectedItem(boolean dropEntireStack, CallbackInfoReturnable<Boolean> info) {
      if (((DropItemsEvent)SpecterCore.EVENT_BUS.post(DropItemsEvent.get(this.getMainHandItem()))).isCancelled()) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"aiStep"},
      at = {@At("HEAD")}
   )
   private void preTickMovement(CallbackInfo ci) {
      SpecterCore.EVENT_BUS.post(PlayerTickMovementEvent.get());
   }

   @ModifyReturnValue(
      method = {"pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;"},
      at = {@At("RETURN")}
   )
   private static HitResult onUpdateTargetedEntity(HitResult original, @Local HitResult hitResult) {
      return original instanceof EntityHitResult ehr && ehr.getEntity() instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit ? hitResult : original;
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("HEAD")}
   )
   private void onSendMovementPacketsHead(CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
         ordinal = 1
      )}
   )
   private void onTickHasVehicleBeforeSendPackets(CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("TAIL")}
   )
   private void onSendMovementPacketsTail(CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
         ordinal = 1,
         shift = Shift.AFTER
      )}
   )
   private void onTickHasVehicleAfterSendPackets(CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
   }
}
