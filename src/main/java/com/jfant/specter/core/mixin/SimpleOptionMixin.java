package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.ISimpleOption;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({OptionInstance.class})
public abstract class SimpleOptionMixin implements ISimpleOption {
   @Shadow
   Object value;
   @Shadow
   @Final
   private Consumer<Object> onValueUpdate;

   @Override
   public void meteor$set(Object value) {
      if (!Minecraft.getInstance().isRunning()) {
         this.value = value;
      } else if (!Objects.equals(this.value, value)) {
         this.value = value;
         this.onValueUpdate.accept(this.value);
      }
   }
}
