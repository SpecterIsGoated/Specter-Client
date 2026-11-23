package com.jfant.specter.phone.api;

import com.jfant.specter.phone.gfx.IconRaster;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * An app icon: either painted in code (crisp at any size, see {@code AppIcons} for the house style) or a texture
 * from a resource pack / mod jar ({@code mymod:textures/phone/icon.png}, square, with its own rounded corners).
 */
public final class AppIcon {
   @FunctionalInterface
   public interface Painter {
      /** Paint the whole icon (background and glyph) into a square raster. */
      void paint(IconRaster r);
   }

   public final String key;
   @Nullable
   public final Painter painter;
   @Nullable
   public final Identifier texture;

   private AppIcon(String key, @Nullable Painter painter, @Nullable Identifier texture) {
      this.key = key;
      this.painter = painter;
      this.texture = texture;
   }

   public static AppIcon painted(String key, Painter painter) {
      return new AppIcon(Objects.requireNonNull(key), Objects.requireNonNull(painter), null);
   }

   public static AppIcon texture(Identifier texture) {
      return new AppIcon(texture.toString(), null, Objects.requireNonNull(texture));
   }
}
