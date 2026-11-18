/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElement;
import com.jfant.specter.core.systems.modules.Category;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CrashReport.class)
public abstract class CrashReportMixin {
    @Inject(method = "getDetails(Ljava/lang/StringBuilder;)V", at = @At("TAIL"))
    private void onAddDetails(StringBuilder builder, CallbackInfo ci) {
        builder.append("\n\n-- Specter Client --\n\n");
        builder.append("Version: ").append(SpecterCore.VERSION).append("\n");
        if (!SpecterCore.BUILD_NUMBER.isEmpty()) {
            builder.append("Build: ").append(SpecterCore.BUILD_NUMBER).append("\n");
        }

        if (Modules.get() != null) {
            boolean modulesActive = false;
            for (Category category : Modules.loopCategories()) {
                List<Module> modules = Modules.get().getGroup(category);
                boolean categoryActive = false;

                for (Module module : modules) {
                    if (module == null || !module.isActive()) continue;

                    if (!modulesActive) {
                        modulesActive = true;
                        builder.append("\n[[ Active Modules ]]\n");
                    }

                    if (!categoryActive) {
                        categoryActive = true;
                        builder.append("\n[")
                            .append(category)
                            .append("]:\n");
                    }

                    builder.append(module.name).append("\n");
                }

            }

        }

        if (Hud.get() != null && Hud.get().active) {
            boolean hudActive = false;
            for (HudElement element : Hud.get()) {
                if (element == null || !element.isActive()) continue;

                if (!hudActive) {
                    hudActive = true;
                    builder.append("\n[[ Active Hud Elements ]]\n");
                }

                builder.append(element.info.name).append("\n");
            }
        }
    }
}
