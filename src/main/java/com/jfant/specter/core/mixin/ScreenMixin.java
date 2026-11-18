/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.text.MeteorClickEvent;
import com.jfant.specter.core.utils.misc.text.RunnableClickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mojang.blaze3d.platform.InputConstants.*;

@Mixin(value = Screen.class, priority = 500) // needs to be before baritone
public abstract class ScreenMixin {

    @Unique
    private static boolean meteor$isArray(int key) {
        return key == KEY_RIGHT || key == KEY_LEFT || key == KEY_DOWN || key == KEY_UP;
    }

    @Inject(method = "defaultHandleClickEvent", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V", remap = false), cancellable = true)
    private static void onDefaultHandleClickEvent(ClickEvent event, Minecraft minecraft, Screen activeScreen, CallbackInfo ci) {
        if (event instanceof RunnableClickEvent runnableClickEvent) {
            runnableClickEvent.runnable.run();
            ci.cancel();
        } else if (event instanceof MeteorClickEvent meteorClickEvent && meteorClickEvent.value.startsWith(Config.get().prefix.get())) {
            try {
                Commands.dispatch(meteorClickEvent.value.substring(Config.get().prefix.get().length()));
            } catch (CommandSyntaxException e) {
                SpecterCore.LOG.error("Failed to run command", e);
            } finally {
                ci.cancel();
            }
        }
    }

}
