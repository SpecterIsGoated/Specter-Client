package com.jfant.specter.scan;

public enum Indicator {
   AMETHYST_CLUSTER(
      "amethyst",
      "amethyst-grown",
      "Fully grown amethyst clusters, counted only in geodes with no partial buds left. Worldgen always leaves small/medium/large buds behind, so an all-grown geode was kept loaded by a player. Ten clusters reach certainty 4 - with fewer in sight, its buds may just be out of view.",
      4.0,
      false,
      true,
      false
   ),
   BEEHIVE(
      "beehive",
      "beehive-crafted",
      "Crafted beehives. Worldgen only places bee nests, so a beehive was put there by a player. One reaches certainty 4.",
      40.0,
      true,
      false,
      false
   ),
   SURFACE_KELP(
      "kelp",
      "kelp",
      "Kelp columns whose top has reached the water surface. Kelp only grows under random ticks. Too common on DonutSMP to narrow down a base, so it adds to the score but never triangulates.",
      0.1,
      false,
      false,
      false
   ),
   GROUNDED_VINE(
      "vine",
      "vine",
      "Long vine columns reaching the ground. Vines only creep downward while loaded. Adds to the score but never triangulates, for the same reason as kelp.",
      0.1,
      false,
      false,
      false
   ),
   SCULK(
      "sculk",
      "sculk-far",
      "Sculk family blocks away from the deep dark - sculk only spreads where mobs die near a catalyst, so this means a farm. Ignored within a chunk of any deep dark, where natural patches spill over the edge. Ten blocks reach certainty 4.",
      4.0,
      true,
      true,
      false
   ),
   CAVE_VINES(
      "cave-vine",
      "cave-vine",
      "Glow-berry vines 23 or longer still carrying berries. Worldgen stops at 22 and they lengthen only under random ticks, so each one was grown. Two reach certainty 4.",
      20.0,
      false,
      true,
      false
   ),
   HIDDEN_GLOW(
      "glow",
      "glow-clusters",
      "Amethyst clusters in the geode, read from its light: anti-xray hides the clusters and the geode but not the ring of light around each cluster. Worldgen leaves a handful (0-13 even in a big geode); a geode kept loaded grows one on every open face, 20 to 100+. Scores the clusters of the whole geode (this chunk and the 8 around it, visible ones counted where light shows fewer), so 30 reach certainty 4.",
      1.34,
      false,
      true,
      false
   ),
   DRIPSTONE(
      "dripstone",
      "dripstone-long",
      "The dripstone Dripstone Bypass finds, by that module's settings but only 4 or more long (short ones are mostly natural): air needles in water where the server removed a stalactite or stalagmite, stone hanging from dripstone blocks, and any real dripstone in the same needle. Dripstone only grows under random ticks. Three finds in a chunk reach certainty 4.",
      13.4,
      false,
      true,
      false
   ),
   AMETHYST_BUDS("amethyst-buds", "amethyst-buds", "", 0.0, false, false, true),
   DEEP_DARK("deep-dark", "deep-dark", "", 0.0, false, false, true),
   TRIAL_CHAMBER("trial-chamber", "trial-chamber", "", 0.0, false, false, true),
   AMETHYST_LISTED(
      "amethyst-listed",
      "amethyst-listed",
      "Grown amethyst the server hid, read from the block list: every 16x16x16 section arrives with the list of blocks it is made of, and the anti-xray can paint a block over without taking it off that list. A chunk whose sections list fully grown amethyst clusters but no partial buds, with no cluster in sight, has a grown geode somewhere in it. Worldgen keeps buds, so a geode kept loaded by a player is the one without. Only a presence flag, so it adds a little rather than flagging a chunk alone - it backs up the glow. Off by default because it is not proven on this server.",
      20.0,
      false,
      false,
      false,
      false
   ),
   SHAFT("shaft", "shaft", "", 0.0, false, false, true);

   public static final Indicator[] VALUES = values();
   public static final int COUNT = VALUES.length;
   public final String csvName;
   public final String columnName;
   public final String description;
   public final double weight;
   public final boolean entityTick;
   public final boolean triangulates;
   public final boolean hidden;
   public final boolean defaultOn;

   private Indicator(String csvName, String columnName, String description, double weight, boolean entityTick, boolean triangulates, boolean hidden) {
      this(csvName, columnName, description, weight, entityTick, triangulates, hidden, true);
   }

   private Indicator(
      String csvName, String columnName, String description, double weight, boolean entityTick, boolean triangulates, boolean hidden, boolean defaultOn
   ) {
      this.defaultOn = defaultOn;
      this.csvName = csvName;
      this.columnName = columnName;
      this.description = description;
      this.weight = weight;
      this.entityTick = entityTick;
      this.triangulates = triangulates;
      this.hidden = hidden;
   }
}
