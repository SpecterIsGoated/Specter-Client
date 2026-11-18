package com.jfant.specter.mixin;

import com.jfant.specter.bypass.SimulationSignals;
import com.jfant.specter.scan.LightArrivals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class PlayerBypassNetworkMixin {
   private static boolean specter$main() {
      return Minecraft.getInstance().isSameThread();
   }

   @Inject(
      method = {"handleLogin"},
      at = {@At("HEAD")}
   )
   private void specter$login(ClientboundLoginPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onLogin(packet.simulationDistance(), packet.chunkRadius(), packet.commonPlayerSpawnInfo().dimension());
      }
   }

   @Inject(
      method = {"handleRespawn"},
      at = {@At("HEAD")}
   )
   private void specter$respawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onRespawn(packet.commonPlayerSpawnInfo().dimension());
      }
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")}
   )
   private void specter$teleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onTeleport();
      }
   }

   @Inject(
      method = {"handleSetSimulationDistance"},
      at = {@At("HEAD")}
   )
   private void specter$simulation(ClientboundSetSimulationDistancePacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onSimulationDistance(packet.simulationDistance());
      }
   }

   @Inject(
      method = {"handleSetChunkCacheRadius"},
      at = {@At("HEAD")}
   )
   private void specter$view(ClientboundSetChunkCacheRadiusPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onViewDistance(packet.getRadius());
      }
   }

   @Inject(
      method = {"handleLevelChunkWithLight"},
      at = {@At("TAIL")}
   )
   private void specter$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onChunk(packet.getX(), packet.getZ());
      }
   }

   @Inject(
      method = {"applyLightData"},
      at = {@At("TAIL")}
   )
   private void specter$lightApplied(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data, boolean scheduleBlockRenders, CallbackInfo ci) {
      LightArrivals.applied(chunkX, chunkZ, data);
   }

   @Inject(
      method = {"handleMoveEntity"},
      at = {@At("HEAD")}
   )
   private void specter$move(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onMove(packet);
      }
   }

   @Inject(
      method = {"handleEntityPositionSync"},
      at = {@At("HEAD")}
   )
   private void specter$positionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onPositionSync(packet);
      }
   }

   @Inject(
      method = {"handleRotateMob"},
      at = {@At("HEAD")}
   )
   private void specter$head(ClientboundRotateHeadPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onHeadRotation(packet);
      }
   }

   @Inject(
      method = {"handleSetEntityData"},
      at = {@At("HEAD")}
   )
   private void specter$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onEntityData(packet);
      }
   }

   @Inject(
      method = {"handleEntityEvent"},
      at = {@At("HEAD")}
   )
   private void specter$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onEntityEvent(packet);
      }
   }

   @Inject(
      method = {"handleAddEntity"},
      at = {@At("HEAD")}
   )
   private void specter$addEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onAddEntity(packet);
      }
   }

   @Inject(
      method = {"handleRemoveEntities"},
      at = {@At("HEAD")}
   )
   private void specter$removeEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onRemoveEntities(packet.getEntityIds());
      }
   }

   @Inject(
      method = {"handleBlockUpdate"},
      at = {@At("HEAD")}
   )
   private void specter$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onBlockUpdate(packet);
      }
   }

   @Inject(
      method = {"handleChunkBlocksUpdate"},
      at = {@At("HEAD")}
   )
   private void specter$section(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onSectionUpdate(packet);
      }
   }

   @Inject(
      method = {"handleTakeItemEntity"},
      at = {@At("HEAD")}
   )
   private void specter$takeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onTakeItem(packet);
      }
   }

   @Inject(
      method = {"handleDamageEvent"},
      at = {@At("HEAD")}
   )
   private void specter$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onDamage(packet);
      }
   }

   @Inject(
      method = {"handleSetEquipment"},
      at = {@At("HEAD")}
   )
   private void specter$equipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
      if (specter$main()) {
         SimulationSignals.onEquipment(packet);
      }
   }
}
