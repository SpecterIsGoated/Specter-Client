package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.events.entity.EntityDestroyEvent;
import com.jfant.specter.core.events.entity.player.PickItemsEvent;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.SendMessageEvent;
import com.jfant.specter.core.events.packets.ContainerSlotUpdateEvent;
import com.jfant.specter.core.events.packets.InventoryEvent;
import com.jfant.specter.core.events.packets.PlaySoundPacketEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.pathing.BaritoneUtils;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class ClientPlayNetworkHandlerMixin extends ClientCommonPacketListenerImpl {
   @Shadow
   private ClientLevel level;

   protected ClientPlayNetworkHandlerMixin(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
      super(client, connection, connectionState);
   }

   @Inject(
      method = {"handleLogin"},
      at = {@At("HEAD")}
   )
   private void onGameJoinHead(ClientboundLoginPacket packet, CallbackInfo info, @Share("worldNotNull") LocalBooleanRef worldNotNull) {
      worldNotNull.set(this.level != null);
   }

   @Inject(
      method = {"handleLogin"},
      at = {@At("TAIL")}
   )
   private void onGameJoinTail(ClientboundLoginPacket packet, CallbackInfo info, @Share("worldNotNull") LocalBooleanRef worldNotNull) {
      if (worldNotNull.get()) {
         SpecterCore.EVENT_BUS.post(GameLeftEvent.get());
      }

      SpecterCore.EVENT_BUS.post(GameJoinedEvent.get());
   }

   @Inject(
      method = {"handleConfigurationStart"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )}
   )
   private void onEnterReconfiguration(ClientboundStartConfigurationPacket packet, CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(GameLeftEvent.get());
   }

   @Inject(
      method = {"handleSoundEvent"},
      at = {@At("HEAD")}
   )
   private void onPlaySound(ClientboundSoundPacket packet, CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(PlaySoundPacketEvent.get(packet));
   }

   @Inject(
      method = {"handleLevelChunkWithLight"},
      at = {@At("TAIL")}
   )
   private void onChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo info) {
      LevelChunk chunk = this.minecraft.level.getChunk(packet.getX(), packet.getZ());
      SpecterCore.EVENT_BUS.post(new ChunkDataEvent(chunk));
   }

   @Inject(
      method = {"handleContainerSetSlot"},
      at = {@At("TAIL")}
   )
   private void onContainerSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(ContainerSlotUpdateEvent.get(packet));
   }

   @Inject(
      method = {"handleContainerContent"},
      at = {@At("TAIL")}
   )
   private void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(InventoryEvent.get(packet));
   }

   @Inject(
      method = {"handleRemoveEntities"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/game/ClientboundRemoveEntitiesPacket;getEntityIds()Lit/unimi/dsi/fastutil/ints/IntList;"
      )}
   )
   private void onEntitiesDestroy(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
      IntListIterator var3 = packet.getEntityIds().iterator();

      while (var3.hasNext()) {
         int id = (Integer)var3.next();
         SpecterCore.EVENT_BUS.post(EntityDestroyEvent.get(this.minecraft.level.getEntity(id)));
      }
   }

   @Inject(
      method = {"handleTakeItemEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;",
         ordinal = 0
      )}
   )
   private void onItemPickupAnimation(ClientboundTakeItemEntityPacket packet, CallbackInfo info) {
      Entity itemEntity = this.minecraft.level.getEntity(packet.getItemId());
      Entity entity = this.minecraft.level.getEntity(packet.getPlayerId());
      if (itemEntity instanceof ItemEntity && entity == this.minecraft.player) {
         SpecterCore.EVENT_BUS.post(PickItemsEvent.get(((ItemEntity)itemEntity).getItem(), packet.getAmount()));
      }
   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChatMessage(String message, CallbackInfo ci, @Local(argsOnly = true) LocalRef<String> messageRef) {
      if (message.startsWith(Config.get().prefix.get()) || BaritoneUtils.IS_AVAILABLE && message.startsWith(BaritoneUtils.getPrefix())) {
         if (message.startsWith(Config.get().prefix.get())) {
            try {
               Commands.dispatch(message.substring(Config.get().prefix.get().length()));
            } catch (CommandSyntaxException var5) {
               ChatUtils.error(var5.getMessage());
            }

            this.minecraft.gui.getChat().addRecentChat(message);
            ci.cancel();
         }
      } else {
         SendMessageEvent event = (SendMessageEvent)SpecterCore.EVENT_BUS.post(SendMessageEvent.get(message));
         if (!event.isCancelled()) {
            messageRef.set(event.message);
         } else {
            ci.cancel();
         }
      }
   }
}
