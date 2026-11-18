package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.HudElement;
import com.jfant.specter.core.systems.modules.Category;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import java.util.List;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CrashReport.class})
public abstract class CrashReportMixin {
   @Inject(
      method = {"getDetails"},
      at = {@At("TAIL")}
   )
   private void onAddDetails(StringBuilder sb, CallbackInfo info) {
      sb.append("\n\n-- Specter Client --\n\n");
      sb.append("Version: ").append(SpecterCore.VERSION).append("\n");
      if (!SpecterCore.BUILD_NUMBER.isEmpty()) {
         sb.append("Build: ").append(SpecterCore.BUILD_NUMBER).append("\n");
      }

      if (Modules.get() != null) {
         boolean modulesActive = false;

         for (Category category : Modules.loopCategories()) {
            List<Module> modules = Modules.get().getGroup(category);
            boolean categoryActive = false;

            for (Module module : modules) {
               if (module != null && module.isActive()) {
                  if (!modulesActive) {
                     modulesActive = true;
                     sb.append("\n[[ Active Modules ]]\n");
                  }

                  if (!categoryActive) {
                     categoryActive = true;
                     sb.append("\n[").append(category).append("]:\n");
                  }

                  sb.append(module.name).append("\n");
               }
            }
         }
      }

      if (Hud.get() != null && Hud.get().active) {
         boolean hudActive = false;

         for (HudElement element : Hud.get()) {
            if (element != null && element.isActive()) {
               if (!hudActive) {
                  hudActive = true;
                  sb.append("\n[[ Active Hud Elements ]]\n");
               }

               sb.append(element.info.name).append("\n");
            }
         }
      }
   }
}
