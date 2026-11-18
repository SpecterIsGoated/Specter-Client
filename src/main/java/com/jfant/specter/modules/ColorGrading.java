package com.jfant.specter.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Spring;
import com.jfant.specter.anim.Tween;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.fx.FxParams;
import com.jfant.specter.gui.PageActions;
import com.jfant.specter.notify.Notifications;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ColorGrading extends Module implements PageActions {
   private static final int EXPOSURE = 0;
   private static final int BRIGHTNESS = 1;
   private static final int CONTRAST = 2;
   private static final int SATURATION = 3;
   private static final int VIBRANCE = 4;
   private static final int TEMPERATURE = 5;
   private static final int TINT = 6;
   private static final int GAMMA = 7;
   private static final int HIGHLIGHTS = 8;
   private static final int SHADOWS = 9;
   private static final int VIGNETTE = 10;
   private static final int GRAIN = 11;
   private static final int FADE = 12;
   private static final int SHADOW_TINT = 13;
   private static final int HIGHLIGHT_TINT = 14;
   private static final int DIALS = 15;
   private static final String[] NAMES = new String[]{
      "exposure",
      "brightness",
      "contrast",
      "saturation",
      "vibrance",
      "temperature",
      "tint",
      "gamma",
      "highlights",
      "shadows",
      "vignette",
      "grain",
      "fade",
      "shadow-tint",
      "highlight-tint"
   };
   private static final double[] NEUTRAL = new double[]{0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
   private static ColorGrading instance;
   private static volatile boolean on;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgLight = this.settings.createGroup("Light");
   private final SettingGroup sgTone = this.settings.createGroup("Tone");
   private final SettingGroup sgColor = this.settings.createGroup("Color");
   private final SettingGroup sgFinish = this.settings.createGroup("Finish");
   private final SettingGroup sgSaved = this.settings.createGroup("Saved Grades");
   private final Setting<ColorGrading.Look> look = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("look"))
                  .description("A ready-made grade, or Custom to set every dial yourself. Use \"Copy look to Custom\" below to start from one and change it."))
               .defaultValue(ColorGrading.Look.Custom))
            .build()
      );
   private final Setting<Double> strength = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("strength")
            .description("How much of the grade is applied: 0 is the untouched picture, 1 the full grade.")
            .defaultValue(1.0)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final List<Setting<Double>> dials = new ArrayList<>();
   private final Setting<Double> exposure = this.dial(this.sgLight, "exposure", "Overall light, in stops: +1 doubles it, -1 halves it.", 0.0, -2.0, 2.0);
   private final Setting<Double> brightness = this.dial(this.sgLight, "brightness", "A plain lift or drop of every tone.", 0.0, -0.3, 0.3);
   private final Setting<Double> gamma = this.dial(
      this.sgLight, "gamma", "Bends the mid tones lighter (above 1) or darker without moving black and white.", 1.0, 0.4, 2.2
   );
   private final Setting<Double> temperature = this.dial(
      this.sgLight, "temperature", "White balance: left cools the picture toward blue, right warms it toward orange.", 0.0, -1.0, 1.0
   );
   private final Setting<Double> tint = this.dial(this.sgLight, "tint", "White balance: left toward green, right toward magenta.", 0.0, -1.0, 1.0);
   private final Setting<Double> contrast = this.dial(
      this.sgTone, "contrast", "The gap between dark and bright. Below 1 is flat, above 1 punchy.", 1.0, 0.0, 2.0
   );
   private final Setting<Double> highlights = this.dial(
      this.sgTone, "highlights", "The bright end only: down pulls skies and snow back, up makes them glow.", 0.0, -1.0, 1.0
   );
   private final Setting<Double> shadows = this.dial(
      this.sgTone, "shadows", "The dark end only: up lifts detail out of caves and night, down crushes it to black.", 0.0, -1.0, 1.0
   );
   private final Setting<Double> saturation = this.dial(this.sgColor, "saturation", "How strong every colour is. 0 is black and white.", 1.0, 0.0, 2.0);
   private final Setting<Double> vibrance = this.dial(
      this.sgColor, "vibrance", "Saturation that leaves already vivid colours alone: lifts dull ones without turning grass neon.", 0.0, -1.0, 1.0
   );
   private final Setting<SettingColor> shadowTintColor = this.sgColor
      .add(
         new ColorSetting.Builder()
            .name("shadow-tint-color")
            .description("A colour washed into the dark parts of the picture (teal is the film classic).")
            .defaultValue(new SettingColor(30, 110, 140))
            .visible(this::custom)
            .build()
      );
   private final Setting<Double> shadowTint = this.dial(this.sgColor, "shadow-tint", "How much of the shadow colour is washed in.", 0.0, 0.0, 1.0);
   private final Setting<SettingColor> highlightTintColor = this.sgColor
      .add(
         new ColorSetting.Builder()
            .name("highlight-tint-color")
            .description("A colour washed into the bright parts (warm orange is the film classic).")
            .defaultValue(new SettingColor(255, 176, 112))
            .visible(this::custom)
            .build()
      );
   private final Setting<Double> highlightTint = this.dial(this.sgColor, "highlight-tint", "How much of the highlight colour is washed in.", 0.0, 0.0, 1.0);
   private final Setting<Double> vignette = this.dial(this.sgFinish, "vignette", "Darkens the corners, drawing the eye to the middle.", 0.0, 0.0, 1.0);
   private final Setting<Double> grain = this.dial(this.sgFinish, "grain", "Fine moving film grain.", 0.0, 0.0, 1.0);
   private final Setting<Double> fade = this.dial(this.sgFinish, "fade", "Lifts the blacks and softens the whites: the washed look of old film.", 0.0, 0.0, 1.0);
   private final Setting<String> saveName = this.sgSaved
      .add(
         new StringSetting.Builder()
            .name("grade-name")
            .description("The name to save the Custom grade under, or of a saved grade to load or delete. Saved grades: " + savedList())
            .defaultValue("my-grade")
            .build()
      );
   private final Tween presence = Tween.progress(Spec.WORLD_FADE, false);
   private final Spring[] shown = new Spring[15];
   private final Spring[] shownShadowTint = new Spring[3];
   private final Spring[] shownHighlightTint = new Spring[3];
   private ColorGrading.Look lastLook;
   private boolean primed;
   private double lookChangedAt = -100.0;

   public ColorGrading() {
      super(
         SpecterClientAddon.VISUALS,
         "color-grading",
         "Grade the whole picture: exposure, white balance, contrast, colour, vignette and grain, with ready-made looks. Only you see it."
      );
      instance = this;

      for (int i = 0; i < 15; i++) {
         this.shown[i] = new Spring(0.28, Channel.WORLD, NEUTRAL[i]);
      }

      for (int i = 0; i < 3; i++) {
         this.shownShadowTint[i] = new Spring(0.28, Channel.WORLD, 0.5);
         this.shownHighlightTint[i] = new Spring(0.28, Channel.WORLD, 0.5);
      }
   }

   private boolean custom() {
      return this.look.get() == ColorGrading.Look.Custom;
   }

   private Setting<Double> dial(SettingGroup group, String name, String description, double def, double min, double max) {
      Setting<Double> setting = group.add(
         new DoubleSetting.Builder()
            .name(name)
            .description(description)
            .defaultValue(def)
            .range(min, max)
            .sliderRange(min, max)
            .decimalPlaces(2)
            .visible(this::custom)
            .build()
      );
      this.dials.add(setting);
      return setting;
   }

   private Setting<Double> setting(int index) {
      return switch (index) {
         case 0 -> this.exposure;
         case 1 -> this.brightness;
         case 2 -> this.contrast;
         case 3 -> this.saturation;
         case 4 -> this.vibrance;
         case 5 -> this.temperature;
         case 6 -> this.tint;
         case 7 -> this.gamma;
         case 8 -> this.highlights;
         case 9 -> this.shadows;
         case 10 -> this.vignette;
         case 11 -> this.grain;
         case 12 -> this.fade;
         case 13 -> this.shadowTint;
         default -> this.highlightTint;
      };
   }

   @Override
   public void onActivate() {
      on = true;
      this.presence.to(1.0, Spec.WORLD_FADE);
   }

   @Override
   public void onDeactivate() {
      this.presence.to(0.0, Spec.WORLD_FADE);
   }

   public static boolean on() {
      return on;
   }

   public static void fill(FxParams p) {
      if (instance != null && on) {
         instance.fillParams(p);
      }
   }

   private void fillParams(FxParams p) {
      float mix = this.presence.getF();
      if (mix <= 0.001F && !this.isActive()) {
         on = false;
         this.primed = false;
      } else {
         ColorGrading.Look chosen = this.look.get();
         double response = chosen != this.lastLook ? Spec.WORLD.millis() / 1000.0 : 0.28;
         if (chosen != this.lastLook && this.lastLook != null) {
            this.lookChangedAt = Motion.now();
         }

         if (Motion.now() - this.lookChangedAt < Spec.WORLD.millis() / 1000.0) {
            response = Spec.WORLD.millis() / 1000.0;
         }

         this.lastLook = chosen;
         double[] target = new double[15];
         float[] sTint = new float[3];
         float[] hTint = new float[3];
         if (chosen == ColorGrading.Look.Custom) {
            for (int i = 0; i < 15; i++) {
               target[i] = this.setting(i).get();
            }

            rgb(this.shadowTintColor.get(), sTint);
            rgb(this.highlightTintColor.get(), hTint);
         } else {
            System.arraycopy(chosen.values, 0, target, 0, 15);
            rgb(chosen.shadowTint, sTint);
            rgb(chosen.highlightTint, hTint);
         }

         float[] v = new float[15];

         for (int i = 0; i < 15; i++) {
            if (!this.primed) {
               this.shown[i].set(target[i]);
            }

            v[i] = (float)this.shown[i].response(response).follow(target[i]);
         }

         for (int i = 0; i < 3; i++) {
            if (!this.primed) {
               this.shownShadowTint[i].set(sTint[i]);
               this.shownHighlightTint[i].set(hTint[i]);
            }

            p.shadowTint[i] = (float)this.shownShadowTint[i].response(response).follow(sTint[i]);
            p.highlightTint[i] = (float)this.shownHighlightTint[i].response(response).follow(hTint[i]);
         }

         this.primed = true;
         p.grade = true;
         p.gradeMix = mix * this.strength.get().floatValue();
         p.exposure = v[0];
         p.brightness = v[1];
         p.contrast = v[2];
         p.saturation = v[3];
         p.vibrance = v[4];
         p.temperature = v[5];
         p.tint = v[6];
         p.gamma = v[7];
         p.highlights = v[8];
         p.shadows = v[9];
         p.vignette = v[10];
         p.grain = v[11];
         p.fade = v[12];
         p.shadowTintAmount = v[13];
         p.highlightTintAmount = v[14];
      }
   }

   private static void rgb(SettingColor c, float[] out) {
      out[0] = c.r / 255.0F;
      out[1] = c.g / 255.0F;
      out[2] = c.b / 255.0F;
   }

   private static void rgb(int packed, float[] out) {
      if (packed == 0) {
         out[0] = out[1] = out[2] = 0.5F;
      } else {
         out[0] = (packed >> 16 & 0xFF) / 255.0F;
         out[1] = (packed >> 8 & 0xFF) / 255.0F;
         out[2] = (packed & 0xFF) / 255.0F;
      }
   }

   @Override
   public List<PageActions.Action> pageActions() {
      return List.of(
         new PageActions.Action(
            "Copy look to Custom", "Copies the picked look's values onto the dials and switches to Custom, so you can change it.", this::copyToCustom
         ),
         new PageActions.Action("Save grade", "Saves the Custom dials under the name in Grade Name.", this::save),
         new PageActions.Action("Load grade", "Loads the saved grade named in Grade Name onto the dials.", this::load),
         new PageActions.Action("Delete grade", "Deletes the saved grade named in Grade Name.", true, this::delete),
         new PageActions.Action("Reset dials", "Puts every Custom dial back to doing nothing.", true, this::resetDials)
      );
   }

   private void copyToCustom() {
      ColorGrading.Look from = this.look.get();
      if (from == ColorGrading.Look.Custom) {
         Notifications.keyed(this, Notifications.Kind.INFO, "Color Grading", "Already on Custom");
      } else {
         for (int i = 0; i < 15; i++) {
            this.setting(i).set(from.values[i]);
         }

         if (from.shadowTint != 0) {
            this.shadowTintColor.set(new SettingColor(from.shadowTint >> 16 & 0xFF, from.shadowTint >> 8 & 0xFF, from.shadowTint & 0xFF));
         }

         if (from.highlightTint != 0) {
            this.highlightTintColor.set(new SettingColor(from.highlightTint >> 16 & 0xFF, from.highlightTint >> 8 & 0xFF, from.highlightTint & 0xFF));
         }

         this.look.set(ColorGrading.Look.Custom);
         Notifications.keyed(this, Notifications.Kind.SUCCESS, "Color Grading", from + " copied to Custom");
      }
   }

   private void resetDials() {
      for (int i = 0; i < 15; i++) {
         this.setting(i).set(NEUTRAL[i]);
      }

      Notifications.keyed(this, Notifications.Kind.INFO, "Color Grading", "Dials reset");
   }

   private static File folder() {
      return new File(new File(SpecterCore.FOLDER, "specter"), "color-grades");
   }

   private File fileFor(String name) {
      String safe = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
      return safe.isEmpty() ? null : new File(folder(), safe + ".json");
   }

   private static String savedList() {
      String[] files = folder().list((dir, filex) -> filex.endsWith(".json"));
      if (files != null && files.length != 0) {
         StringBuilder out = new StringBuilder();

         for (String file : files) {
            if (out.length() > 0) {
               out.append(", ");
            }

            out.append(file, 0, file.length() - 5);
            if (out.length() > 120) {
               out.append("...");
               break;
            }
         }

         return out + ".";
      } else {
         return "none yet.";
      }
   }

   private void save() {
      File file = this.fileFor(this.saveName.get());
      if (file == null) {
         Notifications.keyed(this, Notifications.Kind.ERROR, "Color Grading", "Type a name first");
      } else {
         JsonObject json = new JsonObject();

         for (int i = 0; i < 15; i++) {
            json.addProperty(NAMES[i], this.setting(i).get());
         }

         json.addProperty("shadow-tint-color", this.shadowTintColor.get().getPacked());
         json.addProperty("highlight-tint-color", this.highlightTintColor.get().getPacked());

         try {
            file.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file.toPath(), gson.toJson((JsonElement)json), StandardCharsets.UTF_8);
            Notifications.keyed(this, Notifications.Kind.SUCCESS, "Color Grading", "Saved as " + file.getName().replace(".json", ""));
         } catch (IOException var4) {
            Notifications.keyed(this, Notifications.Kind.ERROR, "Color Grading", "Could not save: " + var4.getMessage());
         }
      }
   }

   private void load() {
      File file = this.fileFor(this.saveName.get());
      if (file != null && file.isFile()) {
         try {
            JsonObject json = new Gson().fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), JsonObject.class);

            for (int i = 0; i < 15; i++) {
               this.setting(i).set(json.has(NAMES[i]) ? json.get(NAMES[i]).getAsDouble() : NEUTRAL[i]);
            }

            if (json.has("shadow-tint-color")) {
               this.shadowTintColor.set(unpack(json.get("shadow-tint-color").getAsInt()));
            }

            if (json.has("highlight-tint-color")) {
               this.highlightTintColor.set(unpack(json.get("highlight-tint-color").getAsInt()));
            }

            this.look.set(ColorGrading.Look.Custom);
            Notifications.keyed(this, Notifications.Kind.SUCCESS, "Color Grading", "Loaded " + file.getName().replace(".json", ""));
         } catch (RuntimeException | IOException var4) {
            Notifications.keyed(this, Notifications.Kind.ERROR, "Color Grading", "Could not read that grade");
         }
      } else {
         Notifications.keyed(this, Notifications.Kind.ERROR, "Color Grading", "No saved grade with that name. Saved: " + savedList());
      }
   }

   private void delete() {
      File file = this.fileFor(this.saveName.get());
      if (file != null && file.isFile() && file.delete()) {
         Notifications.keyed(this, Notifications.Kind.INFO, "Color Grading", "Deleted " + file.getName().replace(".json", ""));
      } else {
         Notifications.keyed(this, Notifications.Kind.ERROR, "Color Grading", "No saved grade with that name");
      }
   }

   private static SettingColor unpack(int packed) {
      Color c = new Color(packed);
      return new SettingColor(c.r, c.g, c.b, c.a);
   }

   public static enum Look {
      Custom("Custom", null, 0, 0),
      Vivid("Vivid", grade(0.05, 0.0, 1.1, 1.22, 0.35, 0.02, 0.0, 1.0, -0.05, 0.1, 0.1, 0.0, 0.0, 0.0, 0.0), 0, 0),
      Cinematic("Cinematic", grade(-0.05, 0.0, 1.14, 0.94, 0.15, 0.06, -0.02, 0.98, -0.2, 0.05, 0.38, 0.12, 0.1, 0.55, 0.45), 1994636, 16756848),
      Sunset("Warm Sunset", grade(0.05, 0.0, 1.06, 1.12, 0.2, 0.42, 0.06, 1.0, -0.1, 0.12, 0.22, 0.0, 0.05, 0.25, 0.45), 8010912, 16761466),
      Winter("Winter Chill", grade(0.08, 0.0, 1.05, 0.86, 0.1, -0.38, -0.03, 1.03, 0.1, 0.12, 0.18, 0.0, 0.08, 0.4, 0.25), 2776016, 15135743),
      Emerald("Emerald", grade(0.0, 0.0, 1.08, 1.1, 0.3, -0.06, -0.22, 1.0, -0.05, 0.08, 0.2, 0.0, 0.0, 0.35, 0.15), 1011290, 15925208),
      Dream("Dream", grade(0.12, 0.01, 0.94, 1.08, 0.25, 0.1, 0.1, 1.05, 0.15, 0.25, 0.15, 0.0, 0.3, 0.35, 0.35), 11558112, 16766704),
      Film("Faded Film", grade(0.02, 0.01, 0.92, 0.82, 0.05, 0.14, 0.02, 1.02, -0.1, 0.18, 0.3, 0.38, 0.55, 0.3, 0.3), 3959386, 16245432),
      Noir("Noir", grade(0.0, 0.0, 1.28, 0.0, 0.0, 0.0, 0.0, 0.96, -0.1, -0.12, 0.48, 0.3, 0.05, 0.0, 0.0), 0, 0);

      private final String title;
      final double[] values;
      final int shadowTint;
      final int highlightTint;

      private Look(String title, double[] values, int shadowTint, int highlightTint) {
         this.title = title;
         this.values = values;
         this.shadowTint = shadowTint;
         this.highlightTint = highlightTint;
      }

      private static double[] grade(double... values) {
         return values;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
