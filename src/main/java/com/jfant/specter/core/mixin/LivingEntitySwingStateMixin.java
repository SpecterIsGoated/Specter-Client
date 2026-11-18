/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(LivingEntity.SwingState.class)
public abstract class LivingEntitySwingStateMixin {
}
