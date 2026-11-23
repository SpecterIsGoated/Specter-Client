package com.jfant.specter.mech;

/** Added to the player render state by a mixin: the mech to draw instead of the player model, or null. */
public interface MechStateHolder {
   MechAnim specter$mech();

   void specter$setMech(MechAnim anim);
}
