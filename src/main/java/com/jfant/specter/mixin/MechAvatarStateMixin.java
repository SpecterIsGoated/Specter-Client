package com.jfant.specter.mixin;

import com.jfant.specter.mech.MechAnim;
import com.jfant.specter.mech.MechStateHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Jet Mech: carries the mech to draw (or null) from extraction to submission. */
@Mixin(AvatarRenderState.class)
public class MechAvatarStateMixin implements MechStateHolder {
   @Unique
   private MechAnim specter$mech;

   @Override
   public MechAnim specter$mech() {
      return this.specter$mech;
   }

   @Override
   public void specter$setMech(MechAnim anim) {
      this.specter$mech = anim;
   }
}
