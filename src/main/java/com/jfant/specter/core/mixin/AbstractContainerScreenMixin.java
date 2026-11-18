/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    @Shadow
    @Nullable
    protected abstract Slot getHoveredSlot(double x, double y);

    @Shadow
    public abstract @NonNull T getMenu();

    @Shadow
    private boolean doubleclick;

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput);

    @Shadow
    public abstract void onClose();

    public AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @ModifyReturnValue(method = "showTooltipWithItemInHand", at = @At("RETURN"))
    private boolean showTooltipWithItemInHand(boolean original, ItemStack item) {
        if (item.getTooltipImage().orElse(null) instanceof ClientTooltipComponent component) {
            return original || component.showTooltipWithItemInHand();
        }

        return original;
    }
}
