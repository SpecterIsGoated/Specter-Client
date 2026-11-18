/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Hud.class)
public abstract class HudMixin {

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;isHidden:Z", opcode = Opcodes.GETFIELD))
    private boolean onGetIsHidden(boolean original) {
        return original || meteor$ShouldHideHud();
    }

    @Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractDebugOverlay(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (meteor$ShouldHideHud()) ci.cancel();
    }

    @Unique
    private static boolean meteor$ShouldHideHud() {
        if (GuiThemes.get() == null || Modules.get() == null) return false;
        return SpecterCore.shouldHideHud();
    }
}
