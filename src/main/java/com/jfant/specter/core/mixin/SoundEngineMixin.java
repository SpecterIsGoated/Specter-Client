/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.PlaySoundEvent;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow
    public abstract void stop(SoundInstance soundInstance);

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void onPlay(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        PlaySoundEvent event = SpecterCore.EVENT_BUS.post(PlaySoundEvent.get(instance));

        if (event.isCancelled()) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }

}
