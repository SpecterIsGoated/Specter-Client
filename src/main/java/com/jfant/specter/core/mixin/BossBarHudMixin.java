package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.RenderBossBarEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Iterator;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({BossHealthOverlay.class})
public abstract class BossBarHudMixin {
   @ModifyExpressionValue(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"
      )}
   )
   public Iterator<LerpingBossEvent> modifyBossBarIterator(Iterator<LerpingBossEvent> original) {
      RenderBossBarEvent.BossIterator event = (RenderBossBarEvent.BossIterator)SpecterCore.EVENT_BUS.post(RenderBossBarEvent.BossIterator.get(original));
      return event.iterator;
   }

   @ModifyExpressionValue(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"
      )}
   )
   public Component modifyBossBarName(Component original, @Local LerpingBossEvent clientBossBar) {
      RenderBossBarEvent.BossText event = (RenderBossBarEvent.BossText)SpecterCore.EVENT_BUS.post(RenderBossBarEvent.BossText.get(clientBossBar, original));
      return event.name;
   }

   @ModifyConstant(
      method = {"render"},
      constant = {@Constant(
         intValue = 9,
         ordinal = 1
      )}
   )
   public int modifySpacingConstant(int j) {
      RenderBossBarEvent.BossSpacing event = (RenderBossBarEvent.BossSpacing)SpecterCore.EVENT_BUS.post(RenderBossBarEvent.BossSpacing.get(j));
      return event.spacing;
   }
}
