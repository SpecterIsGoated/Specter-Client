package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.cave.CaveAnalysis;
import com.jfant.specter.cave.CaveMemory;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.BlockUpdateEvent;
import com.jfant.specter.core.events.world.ChunkDataEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.core.utils.render.color.SettingColor;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;

public class CaveAnalyzer extends Module {
   private static final long SUMMARY_EVERY_MS = 30000L;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> range = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("range")
            .description("How many chunks around you to look at, counting chunks remembered from earlier this session.")
            .defaultValue(16)
            .range(4, 32)
            .sliderRange(4, 32)
            .build()
      );
   private final Setting<Integer> minY = this.sgGeneral
      .add(new IntSetting.Builder().name("min-y").description("The lowest height looked at.").defaultValue(-64).range(-64, 320).sliderRange(-64, 128).build());
   private final Setting<Integer> maxY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-y")
            .description("The highest height looked at. Open blocks above the ground never count as a cave, whatever this is.")
            .defaultValue(128)
            .range(-64, 320)
            .sliderRange(-64, 320)
            .build()
      );
   private final Setting<Integer> minCave = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-cave-size")
            .description("The fewest open blocks a pocket needs to count as a cave.")
            .defaultValue(500)
            .range(500, 5000)
            .sliderRange(500, 5000)
            .build()
      );
   private final Setting<Integer> maxCaves = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-caves")
            .description("How many of the nearest caves to mark.")
            .defaultValue(7)
            .range(0, 30)
            .sliderRange(0, 15)
            .build()
      );
   private final Setting<SettingColor> caveColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("cave-color")
            .description("The colour of cave markers, beams and labels.")
            .defaultValue(new SettingColor(120, 210, 255, 60))
            .build()
      );
   private final Setting<Boolean> beams = this.sgRender
      .add(new BoolSetting.Builder().name("beams").description("Draw a beam up from each cave marker.").defaultValue(true).build());
   private final Setting<Boolean> labels = this.sgRender
      .add(new BoolSetting.Builder().name("labels").description("Label each cave with its size.").defaultValue(true).build());
   private final CaveMemory memory = new CaveMemory("Cave Analyzer");
   private List<CaveAnalysis.Cave> caves = List.of();
   private CaveAnalysis.Result result;
   private String lastSummary = "";
   private long lastSummaryAt;
   private final Vector3d labelPos = new Vector3d();
   private final Color line = new Color();

   public CaveAnalyzer() {
      super(SpecterClientAddon.BASES, "cave-analyzer", "Marks the nearest caves with a beam and their size: Small, Medium, Large or Colossal Cave.");
   }

   @Override
   public void onActivate() {
      this.memory.start();
      this.caves = List.of();
      this.result = null;
      this.lastSummary = "";
   }

   @Override
   public void onDeactivate() {
      SpecterClientAddon.LOG.info("Cave Analyzer: off - {} chunks remembered, {} caves", this.memory.remembered(), this.caves.size());
      this.memory.stop();
      this.caves = List.of();
      this.result = null;
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (this.mc.player != null) {
         this.memory.onChunkData(event.chunk(), this.mc.player.getX(), this.mc.player.getZ(), this.range.get());
      }
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      this.memory.onBlockUpdate(event.pos);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      CaveAnalysis.Settings s = new CaveAnalysis.Settings(this.minCave.get(), true, false, 8, false);
      CaveAnalysis.Result r = this.memory.tick(this.mc, this.range.get(), this.minY.get(), this.maxY.get(), s);
      if (r != null) {
         this.result = r;
         this.caves = r.caves();
         int[] counts = r.sizeCounts();
         String summary = r.caves().size()
            + " caves in range (colossal "
            + counts[3]
            + ", large "
            + counts[2]
            + ", medium "
            + counts[1]
            + ", small "
            + counts[0]
            + ")";
         long now = System.currentTimeMillis();
         if (!summary.equals(this.lastSummary) && now - this.lastSummaryAt >= 30000L) {
            this.lastSummary = summary;
            this.lastSummaryAt = now;
            SpecterClientAddon.LOG
               .info("Cave Analyzer: {}, {} chunks looked at ({} remembered), {} ms", new Object[]{summary, r.chunks(), this.memory.remembered(), r.millis()});
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      int shown = Math.min(this.maxCaves.get(), this.caves.size());
      if (shown != 0) {
         SettingColor c = this.caveColor.get();
         this.line.set(c.r, c.g, c.b, 255);

         for (int i = 0; i < shown; i++) {
            CaveAnalysis.Cave cave = this.caves.get(i);
            event.renderer.box(cave.x() + 0.3, cave.y(), cave.z() + 0.3, cave.x() + 0.7, cave.y() + 0.4, cave.z() + 0.7, c, this.line, ShapeMode.Both, 0);
            if (this.beams.get()) {
               event.renderer.line(cave.x() + 0.5, cave.y() + 0.4, cave.z() + 0.5, cave.x() + 0.5, cave.y() + 40, cave.z() + 0.5, this.line);
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.labels.get()) {
         int shown = Math.min(this.maxCaves.get(), this.caves.size());
         if (shown != 0) {
            TextRenderer text = TextRenderer.get();
            SettingColor c = this.caveColor.get();
            this.line.set(c.r, c.g, c.b, 255);

            for (int i = 0; i < shown; i++) {
               CaveAnalysis.Cave cave = this.caves.get(i);
               this.labelPos.set(cave.x() + 0.5, cave.y() + 1.2, cave.z() + 0.5);
               if (NametagUtils.to2D(this.labelPos, 1.3)) {
                  String name = CaveAnalysis.SIZES[cave.size()] + " Cave";
                  NametagUtils.begin(this.labelPos);
                  text.begin();
                  text.render(name, -text.getWidth(name, true) / 2.0, -text.getHeight(true), this.line, true);
                  text.end();
                  NametagUtils.end();
               }
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return this.result == null ? null : String.valueOf(this.caves.size());
   }
}
