package com.jfant.specter.phone.apps.travel;

import com.jfant.specter.modules.Atmosphere;
import com.jfant.specter.modules.Bioluminescence;
import com.jfant.specter.modules.ColorGrading;
import com.jfant.specter.modules.MarineLayer;
import com.jfant.specter.modules.NebulaNight;
import com.jfant.specter.modules.Skyboxes;
import com.jfant.specter.modules.Snowfall;
import com.jfant.specter.modules.TimeSet;
import com.jfant.specter.phone.ui.Glyph;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Every DonutTravel destination. {@link #register} adds one; the app lists them in order. */
public final class Destinations {
   private static final List<Destination> ALL = new ArrayList<>();

   private Destinations() {
   }

   public static synchronized void register(Destination d) {
      ALL.removeIf(x -> x.id().equals(d.id()));
      ALL.add(d);
   }

   public static synchronized List<Destination> all() {
      return List.copyOf(ALL);
   }

   @Nullable
   public static synchronized Destination get(String id) {
      for (Destination d : ALL) if (d.id().equals(id)) return d;
      return null;
   }

   /** Custom sky colours in Atmosphere (zenith, horizon, sunset). */
   private static void sky(TravelContext c, int zenith, int horizon, int sunset) {
      c.set(Atmosphere.class, "custom-sky-colors", true)
         .set(Atmosphere.class, "zenith-color", zenith)
         .set(Atmosphere.class, "horizon-color", horizon)
         .set(Atmosphere.class, "sunset-color", sunset)
         .turnOn(Atmosphere.class);
   }

   /** The destination's own 360 degree sky (Skybox module). */
   private static void skybox(TravelContext c, String sky) {
      c.set(Skyboxes.class, "sky", sky).set(Skyboxes.class, "rotation", 0).turnOn(Skyboxes.class);
   }

   static {
      register(new Destination("tropical", "Tropical Paradise", "Turquoise sky, high sun, puffy clouds.",
         "Midday in the tropics: a deep turquoise sky fading to a bright horizon, big cumulus clouds and vivid, saturated colour.",
         Glyph.SUN, 0xFF06B6D4, 0xFF10B981, c -> {
         c.set(TimeSet.class, "time", "Noon").turnOn(TimeSet.class);
         skybox(c, "Tropical");
         sky(c, 0xFF1A8CFF, 0xFFA6F4FF, 0xFFFFB37A);
         c.set(Atmosphere.class, "sky-saturation", 1.3).set(Atmosphere.class, "clouds", true).set(Atmosphere.class, "cloud-appearance", "Cumulus");
         c.set(ColorGrading.class, "look", "Vivid").turnOn(ColorGrading.class);
      }, AmbientParticles.Kind.POLLEN));
      register(new Destination("tokyo", "Tokyo Night", "Pink city glow and falling petals.",
         "A warm midnight with a soft pink glow on the horizon, a few stars, a dreamy grade and cherry petals drifting down.",
         Glyph.MOON, 0xFFEC4899, 0xFF4C1D95, c -> {
         c.set(TimeSet.class, "time", "Midnight").turnOn(TimeSet.class);
         c.set(NebulaNight.class, "stars", true).set(NebulaNight.class, "star-density", 0.4).set(NebulaNight.class, "horizon-glow", true)
            .set(NebulaNight.class, "nebula-color", "Specter").set(NebulaNight.class, "aurora", false).turnOn(NebulaNight.class);
         c.set(ColorGrading.class, "look", "Dream").turnOn(ColorGrading.class);
         skybox(c, "Tokyo");
      }, AmbientParticles.Kind.PETALS));
      register(new Destination("cyberpunk", "Cyberpunk City", "Neon haze, teal and magenta.",
         "Neon night: a violet haze over everything, teal shadows and magenta highlights, and glowing motes rising from the ground.",
         Glyph.SPARK, 0xFFD946EF, 0xFF0891B2, c -> {
         c.set(TimeSet.class, "time", "Midnight").turnOn(TimeSet.class);
         c.set(ColorGrading.class, "look", "Custom").set(ColorGrading.class, "shadow-tint-color", 0xFF00C2FF)
            .set(ColorGrading.class, "highlight-tint-color", 0xFFFF2BD6).turnOn(ColorGrading.class);
         c.set(MarineLayer.class, "preset", "Haze").set(MarineLayer.class, "color", "Custom").set(MarineLayer.class, "fog-color", 0xFF5B2A86)
            .turnOn(MarineLayer.class);
         c.set(NebulaNight.class, "horizon-glow", true).set(NebulaNight.class, "stars", false).turnOn(NebulaNight.class);
         skybox(c, "Cyberpunk");
      }, AmbientParticles.Kind.NEON));
      register(new Destination("sunset", "Sunset Beach", "Golden hour over the water.",
         "The sun low over the sea: a burning orange horizon under a violet sky, a light marine layer and a warm film grade.",
         Glyph.SUN, 0xFFF97316, 0xFF7C3AED, c -> {
         c.set(TimeSet.class, "time", "GoldenHour").turnOn(TimeSet.class);
         sky(c, 0xFF4B2A7A, 0xFFFF8A3D, 0xFFFF5E3A);
         c.set(Atmosphere.class, "sunset-intensity", 1.6);
         c.set(ColorGrading.class, "look", "Sunset").turnOn(ColorGrading.class);
         c.set(MarineLayer.class, "preset", "Coastal").turnOn(MarineLayer.class);
         skybox(c, "Sunset");
      }, null));
      register(new Destination("arctic", "Arctic", "Snow, ice-blue light, aurora at night.",
         "A cold clear morning: snow falling around you and across the screen, a pale ice-blue sky and the aurora once night falls.",
         Glyph.STAR, 0xFF93C5FD, 0xFF1E3A8A, c -> {
         c.set(TimeSet.class, "time", "Morning").turnOn(TimeSet.class);
         sky(c, 0xFF8EC5FF, 0xFFEAF6FF, 0xFFFFC2D9);
         c.set(Snowfall.class, "where", "Both").turnOn(Snowfall.class);
         c.set(ColorGrading.class, "look", "Winter").turnOn(ColorGrading.class);
         c.set(NebulaNight.class, "aurora", true).turnOn(NebulaNight.class);
         skybox(c, "Arctic");
      }, null));
      register(new Destination("space", "Outer Space", "The Milky Way, nebulae and meteors.",
         "Night with nothing in the way: a dense star field, the Milky Way, glowing nebulae, shooting stars and meteor showers.",
         Glyph.PLANE, 0xFF312E81, 0xFF020617, c -> {
         c.set(TimeSet.class, "time", "Midnight").turnOn(TimeSet.class);
         c.set(NebulaNight.class, "stars", true).set(NebulaNight.class, "star-density", 1.0).set(NebulaNight.class, "milky-way", true)
            .set(NebulaNight.class, "nebulae", true).set(NebulaNight.class, "nebula-color", "Amethyst").set(NebulaNight.class, "shooting-stars", true)
            .set(NebulaNight.class, "meteor-showers", true).turnOn(NebulaNight.class);
         c.set(Atmosphere.class, "clouds", false).turnOn(Atmosphere.class);
         skybox(c, "Space");
         c.set(ColorGrading.class, "look", "Dream").turnOn(ColorGrading.class);
      }, AmbientParticles.Kind.STARDUST));
      register(new Destination("desert", "Desert", "Heat haze and blowing sand.",
         "A bright afternoon in the dunes: a hazy sand-coloured horizon, warm light and sand dust blowing past.",
         Glyph.SUN, 0xFFF59E0B, 0xFFB45309, c -> {
         c.set(TimeSet.class, "time", "Afternoon").turnOn(TimeSet.class);
         sky(c, 0xFF6FA8DC, 0xFFF2C48D, 0xFFFF9F5A);
         c.set(Atmosphere.class, "haze", 0.7);
         c.set(MarineLayer.class, "preset", "Haze").set(MarineLayer.class, "color", "Custom").set(MarineLayer.class, "fog-color", 0xFFE3C08A)
            .turnOn(MarineLayer.class);
         c.set(ColorGrading.class, "look", "Sunset").turnOn(ColorGrading.class);
         skybox(c, "Desert");
      }, AmbientParticles.Kind.SAND));
      register(new Destination("forest", "Fantasy Forest", "Green mist, glowing water, fireflies.",
         "Dusk in an enchanted wood: green mist between the trees, softly glowing water, a lush grade and fireflies all around.",
         Glyph.SPARK, 0xFF22C55E, 0xFF14532D, c -> {
         c.set(TimeSet.class, "time", "Dusk").turnOn(TimeSet.class);
         c.set(MarineLayer.class, "preset", "Mist").set(MarineLayer.class, "color", "Custom").set(MarineLayer.class, "fog-color", 0xFF7FBF8A)
            .turnOn(MarineLayer.class);
         c.set(Bioluminescence.class, "palette", "Aurora").turnOn(Bioluminescence.class);
         c.set(ColorGrading.class, "look", "Emerald").turnOn(ColorGrading.class);
         skybox(c, "Forest");
      }, AmbientParticles.Kind.FIREFLIES));
   }
}
