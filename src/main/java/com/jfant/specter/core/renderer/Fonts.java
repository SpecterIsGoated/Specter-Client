package com.jfant.specter.core.renderer;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.CustomFontChangedEvent;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.renderer.text.CustomTextRenderer;
import com.jfant.specter.core.renderer.text.FontFace;
import com.jfant.specter.core.renderer.text.FontFamily;
import com.jfant.specter.core.renderer.text.FontInfo;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.PreInit;
import com.jfant.specter.core.utils.render.FontUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Fonts {
   public static final String[] BUILTIN_FONTS = new String[]{"JetBrains Mono", "Comfortaa", "Tw Cen MT", "Pixelation"};
   public static String DEFAULT_FONT_FAMILY;
   public static FontFace DEFAULT_FONT;
   public static final List<FontFamily> FONT_FAMILIES = new ArrayList<>();
   public static CustomTextRenderer RENDERER;

   private Fonts() {
   }

   @PreInit
   public static void refresh() {
      FONT_FAMILIES.clear();

      for (String builtinFont : BUILTIN_FONTS) {
         FontUtils.loadBuiltin(FONT_FAMILIES, builtinFont);
      }

      for (String fontPath : FontUtils.getSearchPaths()) {
         FontUtils.loadSystem(FONT_FAMILIES, new File(fontPath));
      }

      FONT_FAMILIES.sort(Comparator.comparing(FontFamily::getName));
      SpecterCore.LOG.info("Found {} font families.", FONT_FAMILIES.size());
      DEFAULT_FONT_FAMILY = FontUtils.getBuiltinFontInfo(BUILTIN_FONTS[1]).family();
      DEFAULT_FONT = getFamily(DEFAULT_FONT_FAMILY).get(FontInfo.Type.Regular);
      Config config = Config.get();
      load(config != null ? config.font.get() : DEFAULT_FONT);
   }

   public static void load(FontFace fontFace) {
      if (RENDERER != null) {
         if (RENDERER.fontFace.equals(fontFace)) {
            return;
         }

         RENDERER.destroy();
      }

      try {
         RENDERER = new CustomTextRenderer(fontFace);
         SpecterCore.EVENT_BUS.post(CustomFontChangedEvent.get());
      } catch (Exception var2) {
         if (fontFace.equals(DEFAULT_FONT)) {
            throw new RuntimeException("Failed to load default font: " + fontFace, var2);
         }

         SpecterCore.LOG.error("Failed to load font: {}", fontFace, var2);
         load(DEFAULT_FONT);
      }

      if (SpecterCore.mc.screen instanceof WidgetScreen && Config.get().customFont.get()) {
         ((WidgetScreen)SpecterCore.mc.screen).invalidate();
      }
   }

   public static FontFamily getFamily(String name) {
      for (FontFamily fontFamily : FONT_FAMILIES) {
         if (fontFamily.getName().equalsIgnoreCase(name)) {
            return fontFamily;
         }
      }

      return null;
   }
}
