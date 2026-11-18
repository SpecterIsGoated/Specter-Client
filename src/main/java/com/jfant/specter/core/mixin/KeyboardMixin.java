package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.CharTypedEvent;
import com.jfant.specter.core.gui.GuiKeyEvents;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardHandler.class})
public abstract class KeyboardMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(
      method = {"keyPress"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void onKey(long window, int action, KeyEvent input, CallbackInfo ci) {
      int modifiers = input.modifiers();
      if (input.key() != -1) {
         if (action == 1) {
            modifiers |= Input.getModifier(input.key());
         } else if (action == 0) {
            modifiers &= ~Input.getModifier(input.key());
         }

         if (this.minecraft.screen instanceof WidgetScreen && action == 2) {
            ((WidgetScreen)this.minecraft.screen).keyRepeated(new KeyEvent(input.key(), input.scancode(), modifiers));
         }

         if (GuiKeyEvents.canUseKeys) {
            Input.setKeyState(input.key(), action != 0);
            if (((com.jfant.specter.core.events.meteor.KeyEvent)SpecterCore.EVENT_BUS
                  .post(com.jfant.specter.core.events.meteor.KeyEvent.get(new KeyEvent(input.key(), input.scancode(), modifiers), KeyAction.get(action))))
               .isCancelled()) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"charTyped"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onChar(long window, CharacterEvent input, CallbackInfo ci) {
      if (Utils.canUpdate()
         && !this.minecraft.isPaused()
         && (this.minecraft.screen == null || this.minecraft.screen instanceof WidgetScreen)
         && ((CharTypedEvent)SpecterCore.EVENT_BUS.post(CharTypedEvent.get((char)input.codepoint()))).isCancelled()) {
         ci.cancel();
      }
   }
}
