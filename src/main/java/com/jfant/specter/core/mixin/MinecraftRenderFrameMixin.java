/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.jfant.specter.core.renderer.MeshUniforms;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.render.postprocess.OutlineUniforms;
import com.jfant.specter.core.utils.render.postprocess.PostProcessShader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(Minecraft.class)
public abstract class MinecraftRenderFrameMixin {
}
