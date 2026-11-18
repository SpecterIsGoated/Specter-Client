/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.RenderBossBarEvent;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"))
    public Iterator<LerpingBossEvent> modifyBossBarIterator(Iterator<LerpingBossEvent> original) {
        RenderBossBarEvent.BossIterator event = SpecterCore.EVENT_BUS.post(RenderBossBarEvent.BossIterator.get(original));
        return event.iterator;
    }

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    public Component modifyBossBarName(Component original, @Local(name = "event") LerpingBossEvent event) {
        RenderBossBarEvent.BossText bossTextEvent = SpecterCore.EVENT_BUS.post(RenderBossBarEvent.BossText.get(event, original));
        return bossTextEvent.name;
    }

    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 9, ordinal = 1))
    public int modifySpacingConstant(int j) {
        RenderBossBarEvent.BossSpacing event = SpecterCore.EVENT_BUS.post(RenderBossBarEvent.BossSpacing.get(j));
        return event.spacing;
    }
}
