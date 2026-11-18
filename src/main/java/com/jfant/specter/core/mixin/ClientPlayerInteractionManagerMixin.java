package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.DropItemsEvent;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.entity.player.BlockBreakingCooldownEvent;
import com.jfant.specter.core.events.entity.player.BreakBlockEvent;
import com.jfant.specter.core.events.entity.player.InteractBlockEvent;
import com.jfant.specter.core.events.entity.player.InteractEntityEvent;
import com.jfant.specter.core.events.entity.player.InteractItemEvent;
import com.jfant.specter.core.events.entity.player.StartBreakingBlockEvent;
import com.jfant.specter.core.mixininterface.IClientPlayerInteractionManager;
import com.jfant.specter.core.utils.world.BlockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiPlayerGameMode.class})
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
   @Shadow
   private int destroyDelay;

   @Shadow
   protected abstract void ensureHasSentCarriedItem();

   @Shadow
   public abstract boolean destroyBlock(BlockPos var1);

   @Shadow
   public abstract void startPrediction(ClientLevel var1, PredictiveAction var2);

   @Inject(
      method = {"handleInventoryMouseClick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onClickSlot(int syncId, int slotId, int button, ClickType actionType, Player player, CallbackInfo info) {
      if (actionType == ClickType.THROW && slotId >= 0 && slotId < player.containerMenu.slots.size()) {
         if (((DropItemsEvent)SpecterCore.EVENT_BUS.post(DropItemsEvent.get(player.containerMenu.slots.get(slotId).getItem()))).isCancelled()) {
            info.cancel();
         }
      } else if (slotId == -999 && ((DropItemsEvent)SpecterCore.EVENT_BUS.post(DropItemsEvent.get(player.containerMenu.getCarried()))).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"startDestroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> info) {
      if (((StartBreakingBlockEvent)SpecterCore.EVENT_BUS.post(StartBreakingBlockEvent.get(blockPos, direction))).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"useItemOn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void interactBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
      if (((InteractBlockEvent)SpecterCore.EVENT_BUS
            .post(InteractBlockEvent.get(player.getMainHandItem().isEmpty() ? InteractionHand.OFF_HAND : hand, hitResult)))
         .isCancelled()) {
         cir.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(
      method = {"attack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttackEntity(Player player, Entity target, CallbackInfo info) {
      if (((AttackEntityEvent)SpecterCore.EVENT_BUS.post(AttackEntityEvent.get(target))).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"interact"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInteractEntity(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info) {
      if (((InteractEntityEvent)SpecterCore.EVENT_BUS.post(InteractEntityEvent.get(entity, hand))).isCancelled()) {
         info.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(
      method = {"handleCreativeModeItemDrop"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDropCreativeStack(ItemStack stack, CallbackInfo info) {
      if (((DropItemsEvent)SpecterCore.EVENT_BUS.post(DropItemsEvent.get(stack))).isCancelled()) {
         info.cancel();
      }
   }

   @Redirect(
      method = {"continueDestroyBlock"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
         opcode = 181,
         ordinal = 1
      )
   )
   private void creativeBreakDelayChange(MultiPlayerGameMode interactionManager, int value) {
      BlockBreakingCooldownEvent event = (BlockBreakingCooldownEvent)SpecterCore.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.destroyDelay = event.cooldown;
   }

   @Redirect(
      method = {"continueDestroyBlock"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
         opcode = 181,
         ordinal = 2
      )
   )
   private void survivalBreakDelayChange(MultiPlayerGameMode interactionManager, int value) {
      BlockBreakingCooldownEvent event = (BlockBreakingCooldownEvent)SpecterCore.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.destroyDelay = event.cooldown;
   }

   @Redirect(
      method = {"startDestroyBlock"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
         opcode = 181
      )
   )
   private void creativeBreakDelayChange2(MultiPlayerGameMode interactionManager, int value) {
      BlockBreakingCooldownEvent event = (BlockBreakingCooldownEvent)SpecterCore.EVENT_BUS.post(BlockBreakingCooldownEvent.get(value));
      this.destroyDelay = event.cooldown;
   }

   @Inject(
      method = {"destroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onBreakBlock(BlockPos blockPos, CallbackInfoReturnable<Boolean> info) {
      if (((BreakBlockEvent)SpecterCore.EVENT_BUS.post(BreakBlockEvent.get(blockPos))).isCancelled()) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"useItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInteractItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info) {
      InteractItemEvent event = (InteractItemEvent)SpecterCore.EVENT_BUS.post(InteractItemEvent.get(hand));
      if (event.toReturn != null) {
         info.setReturnValue(event.toReturn);
      }
   }

   @Inject(
      method = {"stopDestroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCancelBlockBreaking(CallbackInfo info) {
      if (BlockUtils.breaking) {
         info.cancel();
      }
   }

   @Override
   public void meteor$syncSelected() {
      this.ensureHasSentCarriedItem();
   }
}
