package com.jfant.specter.mixin;

import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.modules.AutoReconnect;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({DisconnectedScreen.class})
public abstract class DisconnectedScreenMixin extends Screen {
   @Shadow
   @Final
   private LinearLayout layout;
   @Unique
   private Button specter$reconnectButton;
   @Unique
   private double specter$ticksLeft = -1.0;

   protected DisconnectedScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V",
         shift = Shift.BEFORE
      )}
   )
   private void specter$addButtons(CallbackInfo ci) {
      AutoReconnect module = Modules.get().get(AutoReconnect.class);
      if (module != null) {
         if (this.specter$ticksLeft < 0.0) {
            this.specter$ticksLeft = module.delayTicks();
         }

         if (module.hasServer() && !module.buttonsHidden()) {
            this.specter$reconnectButton = Button.builder(Component.literal(this.specter$label(module)), button -> module.reconnect()).build();
            this.layout.addChild(this.specter$reconnectButton);
            this.layout.addChild(Button.builder(Component.literal("Toggle Auto Reconnect"), button -> {
               module.toggle();
               this.specter$ticksLeft = module.delayTicks();
               this.specter$reconnectButton.setMessage(Component.literal(this.specter$label(module)));
            }).build());
         }
      }
   }

   @Override
   public void tick() {
      AutoReconnect module = Modules.get().get(AutoReconnect.class);
      if (module != null && module.isActive() && module.hasServer()) {
         if (this.specter$ticksLeft <= 0.0) {
            module.reconnect();
         } else {
            this.specter$ticksLeft--;
            if (this.specter$reconnectButton != null) {
               this.specter$reconnectButton.setMessage(Component.literal(this.specter$label(module)));
            }
         }
      }
   }

   @Unique
   private String specter$label(AutoReconnect module) {
      return module.isActive() ? String.format("Reconnect (%.1f)", Math.max(this.specter$ticksLeft, 0.0) / 20.0) : "Reconnect";
   }
}
