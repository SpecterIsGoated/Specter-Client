package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.EntityAddedEvent;
import com.jfant.specter.core.events.entity.EntityRemovedEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientLevel.class})
public abstract class ClientWorldMixin {
   @Shadow
   @Nullable
   public abstract Entity getEntity(int var1);

   @Inject(
      method = {"addEntity"},
      at = {@At("TAIL")}
   )
   private void onAddEntity(Entity entity, CallbackInfo info) {
      if (entity != null) {
         SpecterCore.EVENT_BUS.post(EntityAddedEvent.get(entity));
      }
   }

   @Inject(
      method = {"removeEntity"},
      at = {@At("HEAD")}
   )
   private void onRemoveEntity(int entityId, RemovalReason removalReason, CallbackInfo info) {
      if (this.getEntity(entityId) != null) {
         SpecterCore.EVENT_BUS.post(EntityRemovedEvent.get(this.getEntity(entityId)));
      }
   }
}
