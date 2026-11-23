package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.lyrics.Lrc;
import com.jfant.specter.lyrics.Lyrics;
import com.jfant.specter.lyrics.LyricsFinder;
import com.jfant.specter.lyrics.SpotifyClock;
import com.jfant.specter.render.BorrowedTexture;
import com.jfant.specter.render.GpuKit;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.labystudio.spotifyapi.SpotifyAPI;
import de.labystudio.spotifyapi.SpotifyAPIFactory;
import de.labystudio.spotifyapi.SpotifyListenerAdapter;
import de.labystudio.spotifyapi.model.Track;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

public class SkyLyrics extends Module {
   private static final String FONT_PATH = "/assets/specter/font/caveat-bold.ttf";
   private static final long LAST_LINE_MS = 6000L;
   private static final long MAX_LINE_MS = 9000L;
   private static final long WORD_FADE_MS = 260L;
   private static final long LINE_FADE_MS = 450L;
   private static final long APPEAR_MS = 350L;
   private static final long OFFSCREEN_MS = 750L;
   private static final double REVEAL_SHARE = 0.85;
   private static final long SYLLABLE_MS = 240L;
   private static final double SLOPE = -0.11;
   private static final double WORD_GAP = 0.26;
   private static final double ROW_GAP = 1.4;
   private static final double ROW_INDENT = 0.22;
   private static final int MAX_ROW_CHARS = 26;
   private static final int ATLAS_MAX_WIDTH = 4096;
   // 26.x: drawn directly in a render pass (the RenderType/BufferSource route was reworked). Reversed Z, so the plain
   // pipeline's "nearer or equal" test is GREATER_THAN_OR_EQUAL; the solid-only one tests depth in its shader.
   private static final RenderPipeline SOLID_ONLY = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/sky_lyrics"))
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
      .withVertexShader(Identifier.fromNamespaceAndPath("specter-client", "core/sky_lyrics"))
      .withFragmentShader(Identifier.fromNamespaceAndPath("specter-client", "core/sky_lyrics"))
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.QUADS)
      .build();
   private static final RenderPipeline PLAIN = RenderPipeline.builder()
      .withLocation(Identifier.fromNamespaceAndPath("specter-client", "pipeline/sky_lyrics_plain"))
      .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
      .withBindGroupLayout(BindGroupLayouts.PROJECTION)
      .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
      .withVertexShader("core/position_tex_color")
      .withFragmentShader("core/position_tex_color")
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .withCull(false)
      .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.QUADS)
      .build();
   private static final Identifier SOLID_DEPTH = Identifier.fromNamespaceAndPath("specter-client", "sky_lyrics/solid_depth");
   private static final long SEEK_BACK_MS = 2500L;
   private static SkyLyrics instance;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<SkyLyrics.Placement> placement = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("placement"))
                  .description(
                     "Sky: each line is written on the sky where you face when it begins and stays there. Follow view: stays put until you look far enough away, then drifts slowly back in front of you."
                  ))
               .defaultValue(SkyLyrics.Placement.Sky))
            .build()
      );
   private final Setting<Integer> followAngle = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("follow-angle")
            .description("How far you can look away (degrees) before the words start drifting after you.")
            .defaultValue(30)
            .range(5, 90)
            .sliderRange(5, 90)
            .visible(() -> this.placement.get() == SkyLyrics.Placement.FollowView)
            .build()
      );
   private final Setting<Double> followSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("follow-speed")
            .description("How quickly the words drift back in front of you once they start.")
            .defaultValue(1.0)
            .range(0.1, 5.0)
            .sliderRange(0.1, 3.0)
            .visible(() -> this.placement.get() == SkyLyrics.Placement.FollowView)
            .build()
      );
   private final Setting<Integer> offset = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("timing-offset")
            .description("Shift the lyrics in time, in milliseconds: raise it if the words come late, lower it if they come early.")
            .defaultValue(0)
            .range(-3000, 3000)
            .sliderRange(-1500, 1500)
            .build()
      );
   private final Setting<Boolean> wordTiming = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("netease-word-timing")
            .description(
               "Get lyrics timed word by word from NetEase Cloud Music when it has them (about half of songs), so each word appears exactly as it is sung. Sends the song's title and artist to NetEase (an unofficial service). Off, or for songs it lacks, lrclib is used and the words are estimated."
            )
            .defaultValue(true)
            .onChanged(v -> this.lyricsSourceChanged())
            .build()
      );
   private final Setting<Integer> skyHeight = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sky-height")
            .description("How far above the horizon the words are written, in degrees.")
            .defaultValue(18)
            .range(0, 60)
            .sliderRange(0, 60)
            .build()
      );
   private final Setting<Double> size = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("size")
            .description("How big the writing is.")
            .defaultValue(1.0)
            .range(0.4, 3.0)
            .sliderRange(0.4, 2.5)
            .onChanged(v -> this.stale = true)
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("The writing's colour; its alpha is how solid it is.")
            .defaultValue(new SettingColor(255, 255, 255, 235))
            .onChanged(v -> this.stale = true)
            .build()
      );
   private final Setting<Boolean> glow = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("glow")
            .description("A soft glow round the letters, as if lit against the night.")
            .defaultValue(true)
            .onChanged(v -> this.stale = true)
            .build()
      );
   private final Setting<Boolean> hideWhenPaused = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-when-paused").description("Fade the words out while Spotify is paused.").defaultValue(true).build());
   private SpotifyAPI api;
   private volatile Track track;
   private volatile SpotifyClock clock;
   private LyricsFinder finder;
   private Font font;
   private boolean fontFailed;
   private SkyLyrics.Line shown;
   private boolean stale;
   private int textureCounter;
   private long positionMs = -1L;
   private long positionReadAt;
   private long lastPosition = -1L;
   private String lastSong = "";
   private TextureTarget solidDepth;
   private BorrowedTexture solidDepthTexture;
   private boolean depthCaptured;
   private long lastFrameNanos;

   public SkyLyrics() {
      super(
         SpecterClientAddon.VISUALS,
         "sky-lyrics",
         "Writes the lyrics of what Spotify is playing on the sky as they are sung, word by word, in white handwriting. Terrain covers them; water and clouds do not."
      );
      instance = this;
   }

   @Override
   public void onActivate() {
      this.track = null;
      // Specter ships only the Windows part of the Spotify library (the Linux/macOS parts run shell commands, which
      // malware scanners flag), so reading Spotify works on Windows only.
      if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
         this.warning("Reading Spotify only works on Windows.");
         this.toggle();
         return;
      }
      SpotifyAPI created = SpotifyAPIFactory.create();
      created.registerListener(new SpotifyListenerAdapter() {
         public void onTrackChanged(Track changed) {
            SkyLyrics.this.track = changed;
         }

         public void onDisconnect(Exception exception) {
            SkyLyrics.this.track = null;
         }
      });
      this.api = created;
      Thread thread = new Thread(() -> {
         try {
            created.initialize();
            SpotifyClock attached = SpotifyClock.attach(created);
            if (this.api == created) {
               this.clock = attached;
            } else if (attached != null) {
               attached.close();
            }
         } catch (Throwable var3) {
            SpecterClientAddon.LOG.warn("Sky Lyrics: Spotify reader failed to start", var3);
         }
      }, "specter-sky-lyrics-spotify");
      thread.setDaemon(true);
      thread.start();
   }

   @Override
   public void onDeactivate() {
      if (this.clock != null) {
         this.clock.close();
         this.clock = null;
      }

      if (this.api != null) {
         try {
            this.api.shutdown();
         } catch (Throwable var2) {
         }

         this.api = null;
      }

      if (this.finder != null) {
         this.finder.close();
         this.finder = null;
      }

      this.drop(this.shown);
      this.shown = null;
      if (this.solidDepthTexture != null) {
         this.mc.getTextureManager().release(SOLID_DEPTH);
         this.solidDepthTexture = null;
      }

      if (this.solidDepth != null) {
         this.solidDepth.destroyBuffers();
         this.solidDepth = null;
      }

      this.depthCaptured = false;
   }

   private void lyricsSourceChanged() {
      if (this.finder != null) {
         this.finder.forget();
      }

      this.stale = true;
   }

   /** True when the words are on screen and need this frame's solid-world depth. */
   public static boolean wantsSolidDepth() {
      SkyLyrics self = instance;
      return self != null && self.isActive() && self.shown != null;
   }

   public static void captureSolidDepth() {
      SkyLyrics self = instance;
      if (self != null && self.isActive() && self.shown != null) {
         self.copyDepth();
      }
   }

   private void copyDepth() {
      RenderTarget main = this.mc.gameRenderer.mainRenderTarget();
      this.solidDepth = GpuKit.copyDepth(this.solidDepth, "specter-sky-lyrics-depth", main);
      if (this.solidDepthTexture == null) {
         this.solidDepthTexture = new BorrowedTexture();
         this.mc.getTextureManager().register(SOLID_DEPTH, this.solidDepthTexture);
      }

      this.solidDepthTexture.point(this.solidDepth);
      this.depthCaptured = true;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         long now = System.currentTimeMillis();
         SpotifyAPI spotify = this.api;
         Track current = this.track;
         boolean playing = spotify != null && spotify.isConnected() && current != null && spotify.isPlaying();
         String song = current == null ? "" : current.getArtist() + " - " + current.getName();
         long position = current != null && spotify != null ? this.position(spotify, song, now, playing) : -1L;
         boolean solidOnly = this.depthCaptured;
         this.depthCaptured = false;
         long frameNanos = System.nanoTime();
         double frameSeconds = this.lastFrameNanos == 0L ? 0.0 : Math.min(0.25, (frameNanos - this.lastFrameNanos) / 1.0E9);
         this.lastFrameNanos = frameNanos;
         Lrc.Line line = null;
         int index = -1;
         long endMs = 0L;
         if (position >= 0L) {
            if (this.finder == null) {
               this.finder = new LyricsFinder(this.wordTiming::get);
            }

            Lyrics found = this.finder.lookup(new LyricsFinder.Query(current.getName(), current.getArtist(), Math.round(current.getLength() / 1000.0F)));
            if (found.status() == Lyrics.Status.SYNCED) {
               Lrc lrc = found.synced();
               index = lrc.indexAt(position);
               if (index >= 0 && !lrc.line(index).gap()) {
                  line = lrc.line(index);
                  endMs = index + 1 < lrc.size() ? lrc.line(index + 1).timeMs() : line.timeMs() + 6000L;
                  endMs = Math.min(endMs, line.timeMs() + 9000L);
               }
            }
         }

         double tanY = RenderUtils.projection.m11() != 0.0F
            ? 1.0F / Math.abs(RenderUtils.projection.m11())
            : Math.tan(Math.toRadians(this.mc.options.fov().get().intValue() / 2.0));
         double tanX = RenderUtils.projection.m00() != 0.0F ? 1.0F / Math.abs(RenderUtils.projection.m00()) : tanY;
         int pixelHeight = this.mc.getWindow().getHeight();
         boolean changed = line == null
            ? this.shown != null
            : this.shown == null || !this.shown.song.equals(song) || this.shown.index != index || this.shown.pixelHeight != pixelHeight || this.stale;
         if (changed) {
            SkyLyrics.Line old = this.shown;
            this.shown = line != null && this.loadFont() != null ? this.build(song, index, line, endMs, pixelHeight) : null;
            if (this.shown != null && old != null) {
               boolean sameLine = old.song.equals(this.shown.song) && old.index == this.shown.index;
               if (sameLine || this.placement.get() == SkyLyrics.Placement.FollowView) {
                  this.shown.anchorYaw = old.anchorYaw;
                  this.shown.drifting = old.drifting;
               }

               if (sameLine) {
                  this.shown.appearedAt = old.appearedAt;
               }
            }

            this.drop(old);
            this.stale = false;
         }

         if (this.shown != null) {
            float lineAlpha = Mth.clamp((float)(this.shown.endMs - position) / 450.0F, 0.0F, 1.0F);
            if (this.hideWhenPaused.get() && !playing) {
               lineAlpha = 0.0F;
            }

            if (!(lineAlpha <= 0.01F)) {
               Camera camera = this.mc.gameRenderer.mainCamera();
               if (this.placement.get() == SkyLyrics.Placement.FollowView) {
                  this.follow(this.shown, camera.yRot(), frameSeconds);
               } else if (this.offscreen(this.shown, camera, tanX, tanY)) {
                  if (this.shown.offscreenSince < 0L) {
                     this.shown.offscreenSince = now;
                  } else if (now - this.shown.offscreenSince > 750L) {
                     this.shown.anchorYaw = camera.yRot();
                     this.shown.appearedAt = now;
                     this.shown.offscreenSince = -1L;
                  }
               } else {
                  this.shown.offscreenSince = -1L;
               }

               lineAlpha *= Mth.clamp((float)(now - this.shown.appearedAt) / 350.0F, 0.0F, 1.0F);
               if (!(lineAlpha <= 0.01F)) {
                  this.draw(this.shown, position, lineAlpha, tanY, solidOnly);
               }
            }
         }
      }
   }

   private long position(SpotifyAPI spotify, String song, long now, boolean playing) {
      SpotifyClock precise = this.clock;
      long base = precise == null ? -1L : precise.positionMs();
      if (base < 0L) {
         int read;
         try {
            read = spotify.hasPosition() ? spotify.getPosition() : -1;
         } catch (IllegalStateException var11) {
            read = -1;
         }

         if (read < 0) {
            return -1L;
         }

         if (read != this.positionMs) {
            this.positionMs = read;
            this.positionReadAt = now;
         }

         base = playing ? this.positionMs + (now - this.positionReadAt) : this.positionMs;
      }

      long position = base + this.offset.get().intValue();
      if (song.equals(this.lastSong) && position < this.lastPosition && this.lastPosition - position < 2500L) {
         position = this.lastPosition;
      }

      this.lastSong = song;
      this.lastPosition = position;
      return position;
   }

   private void follow(SkyLyrics.Line line, float cameraYaw, double seconds) {
      float off = Mth.wrapDegrees(cameraYaw - line.anchorYaw);
      if (Math.abs(off) > this.followAngle.get().intValue()) {
         line.drifting = true;
      }

      if (line.drifting) {
         line.anchorYaw = line.anchorYaw + off * (float)(1.0 - Math.exp(-seconds * this.followSpeed.get()));
         if (Math.abs(off) < 2.0F) {
            line.drifting = false;
         }
      }
   }

   private boolean offscreen(SkyLyrics.Line line, Camera camera, double tanX, double tanY) {
      Vector3d spot = direction(-this.skyHeight.get(), line.anchorYaw);
      Vector3d forward = direction(camera.xRot(), camera.yRot());
      double depth = spot.dot(forward);
      if (depth <= 0.05) {
         return true;
      } else {
         Vector3d right = right(forward);
         Vector3d up = new Vector3d(right).cross(forward).normalize();
         double x = spot.dot(right) / depth / tanX;
         double y = spot.dot(up) / depth / tanY;
         return Math.abs(x) > 1.1 || Math.abs(y) > 1.1;
      }
   }

   private static Vector3d direction(double pitch, double yaw) {
      double p = Math.toRadians(pitch);
      double w = Math.toRadians(yaw);
      return new Vector3d(-Math.sin(w) * Math.cos(p), -Math.sin(p), Math.cos(w) * Math.cos(p));
   }

   private static Vector3d right(Vector3d forward) {
      Vector3d right = new Vector3d(forward).cross(0.0, 1.0, 0.0);
      if (right.lengthSquared() < 1.0E-6) {
         right.set(1.0, 0.0, 0.0);
      }

      return right.normalize();
   }

   private void draw(SkyLyrics.Line line, long position, float lineAlpha, double tanY, boolean solidOnly) {
      double distance = Math.max(this.mc.options.getEffectiveRenderDistance() * 16.0F * 4.0F, this.mc.options.cloudRange().get() * 16.0F) * 0.6;
      double perPixel = 2.0 * distance * tanY / line.pixelHeight;
      Vector3d spot = direction(-this.skyHeight.get(), line.anchorYaw);
      Vector3d right = right(spot);
      Vector3d up = new Vector3d(right).cross(spot).normalize();
      Vector3d center = new Vector3d(spot).mul(distance);
      double base = this.baseSize(line.pixelHeight);
      ByteBufferBuilder bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4 * Math.max(1, line.words.size()));
      BufferBuilder buffer = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

      for (SkyLyrics.Word word : line.words) {
         float wordAlpha = Mth.clamp((float)(position - word.revealAt()) / 260.0F, 0.0F, 1.0F);
         if (!(wordAlpha <= 0.0F)) {
            int alpha = Math.round(255.0F * wordAlpha * lineAlpha);
            double rise = (1.0F - easeOut(wordAlpha)) * base * 0.18;
            double left = word.x() - word.originX() - line.centerX;
            double top = word.y() - word.originY() - line.centerY + rise;
            double rightEdge = left + word.width();
            double bottom = top + word.height();
            float u0 = (float)word.atlasX() / line.atlasWidth;
            float v0 = (float)word.atlasY() / line.atlasHeight;
            float u1 = (float)(word.atlasX() + word.width()) / line.atlasWidth;
            float v1 = (float)(word.atlasY() + word.height()) / line.atlasHeight;
            int argb = alpha << 24 | 16777215;
            corner(buffer, center, right, up, left, top, perPixel, u0, v0, argb);
            corner(buffer, center, right, up, left, bottom, perPixel, u0, v1, argb);
            corner(buffer, center, right, up, rightEdge, bottom, perPixel, u1, v1, argb);
            corner(buffer, center, right, up, rightEdge, top, perPixel, u1, v0, argb);
         }
      }

      try (MeshData mesh = buffer.build()) {
         if (mesh != null) {
            this.submit(line, mesh, solidOnly);
         }
      } finally {
         bytes.close();
      }
   }

   private void submit(SkyLyrics.Line line, MeshData mesh, boolean solidOnly) {
      AbstractTexture words = this.mc.getTextureManager().getTexture(line.texture);
      if (words == null || words.getTextureView() == null || solidOnly && (this.solidDepth == null || !this.depthCaptured)) {
         return;
      }

      RenderPipeline pipeline = solidOnly ? SOLID_ONLY : PLAIN;
      int indexCount = mesh.drawState().indexCount();
      RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      GpuBuffer indices = quadIndices.getBuffer(indexCount);
      GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
         .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
      RenderTarget main = this.mc.gameRenderer.mainRenderTarget();
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

      try (GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "Specter sky lyrics", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
         try (RenderPass pass = pipeline.wantsDepthTexture()
               ? encoder.createRenderPass(() -> "Specter sky lyrics", main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())
               : encoder.createRenderPass(() -> "Specter sky lyrics", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("Sampler0", words.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            if (solidOnly) {
               GpuTextureView depth = this.solidDepth.getDepthTextureView();
               pass.setUniform("Sampler1", depth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            }

            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indices, quadIndices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
         }
      }
   }

   private static void corner(
      VertexConsumer buffer, Vector3d center, Vector3d right, Vector3d up, double px, double py, double perPixel, float u, float v, int argb
   ) {
      double x = center.x + right.x * px * perPixel - up.x * py * perPixel;
      double y = center.y + right.y * px * perPixel - up.y * py * perPixel;
      double z = center.z + right.z * px * perPixel - up.z * py * perPixel;
      buffer.addVertex((float)x, (float)y, (float)z).setUv(u, v).setColor(argb);
   }

   private static float easeOut(float t) {
      return 1.0F - (1.0F - t) * (1.0F - t);
   }

   private double baseSize(int pixelHeight) {
      return pixelHeight * 0.052 * this.size.get();
   }

   private SkyLyrics.Line build(String song, int index, Lrc.Line lyric, long endMs, int pixelHeight) {
      String[] tokens = lyric.text().trim().split("\\s+");
      if (tokens.length != 0 && !tokens[0].isEmpty()) {
         double base = this.baseSize(pixelHeight);
         int stressed = stressedWord(tokens);
         int split = rowSplit(tokens);
         int weightSum = 0;

         for (String token : tokens) {
            weightSum += syllables(token);
         }

         long lineTime = Math.max(0L, endMs - lyric.timeMs());
         long span = Math.min((long)(lineTime * 0.85), 240L * weightSum + 150L);
         long[] stamped = lyric.wordStarts() != null && lyric.wordStarts().length == tokens.length ? lyric.wordStarts() : null;
         FontRenderContext frc = new FontRenderContext(null, true, true);
         List<BufferedImage> images = new ArrayList<>(tokens.length);
         List<double[]> places = new ArrayList<>(tokens.length);
         double minX = 1.7976931348623157E308;
         double minY = 1.7976931348623157E308;
         double maxX = -1.7976931348623157E308;
         double maxY = -1.7976931348623157E308;
         double x = 0.0;
         int weightBefore = 0;

         for (int i = 0; i < tokens.length; i++) {
            if (i == split) {
               x = (maxX - minX) * 0.22;
            }

            int row = i < split ? 0 : 1;
            long seed = mix(tokens[i].hashCode() * 31L + i + index * 977L);
            double factor = i == stressed ? 1.38 : 0.82 + unit(seed) * 0.3;
            Font wordFont = this.font.deriveFont((float)(base * factor));
            TextLayout layout = new TextLayout(tokens[i], wordFont, frc);
            double advance = layout.getAdvance();
            double angle = Math.atan(-0.11) + (unit(seed >>> 17) - 0.5) * 0.09;
            double wobble = (unit(seed >>> 31) - 0.5) * base * 0.22;
            double wordY = row * base * 1.4 + (x + advance / 2.0) * -0.11 + wobble;
            int[] origin = new int[2];
            images.add(this.render(wordFont, layout, angle, origin));
            long estimated = lyric.timeMs() + (weightSum == 0 ? 0L : span * weightBefore / weightSum);
            long revealAt = stamped != null && stamped[i] >= 0L ? stamped[i] : estimated;
            places.add(new double[]{x, wordY, origin[0], origin[1], revealAt});
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x + advance);
            minY = Math.min(minY, wordY - layout.getAscent());
            maxY = Math.max(maxY, wordY + layout.getDescent());
            x += advance + base * 0.26;
            weightBefore += syllables(tokens[i]);
         }

         int atlasWidth = 0;
         int rowWidth = 0;
         int rowHeight = 0;
         int rowTop = 0;
         int[][] at = new int[images.size()][];

         for (int i = 0; i < images.size(); i++) {
            BufferedImage image = images.get(i);
            if (rowWidth > 0 && rowWidth + image.getWidth() > 4096) {
               rowTop += rowHeight;
               rowHeight = 0;
               rowWidth = 0;
            }

            at[i] = new int[]{rowWidth, rowTop};
            rowWidth += image.getWidth();
            rowHeight = Math.max(rowHeight, image.getHeight());
            atlasWidth = Math.max(atlasWidth, rowWidth);
         }

         int atlasHeight = rowTop + rowHeight;
         NativeImage pixels = new NativeImage(atlasWidth, atlasHeight, true);
         List<SkyLyrics.Word> words = new ArrayList<>(images.size());

         for (int i = 0; i < images.size(); i++) {
            BufferedImage image = images.get(i);
            int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());

            for (int py = 0; py < image.getHeight(); py++) {
               for (int px = 0; px < image.getWidth(); px++) {
                  pixels.setPixel(at[i][0] + px, at[i][1] + py, argb[py * image.getWidth() + px]);
               }
            }

            double[] place = places.get(i);
            words.add(
               new SkyLyrics.Word(place[0], place[1], (int)place[2], (int)place[3], at[i][0], at[i][1], image.getWidth(), image.getHeight(), (long)place[4])
            );
         }

         Identifier id = Identifier.fromNamespaceAndPath("specter-client", "sky_lyrics/line_" + this.textureCounter++);
         this.mc.getTextureManager().register(id, new DynamicTexture(() -> "specter-sky-lyrics-line", pixels));
         SkyLyrics.Line line = new SkyLyrics.Line(
            song, index, pixelHeight, words, (minX + maxX) / 2.0, (minY + maxY) / 2.0, lyric.timeMs(), endMs, id, atlasWidth, atlasHeight
         );
         line.anchorYaw = this.mc.gameRenderer.mainCamera().yRot();
         line.appearedAt = System.currentTimeMillis() - 350L;
         return line;
      } else {
         return null;
      }
   }

   private static int stressedWord(String[] tokens) {
      int best = 0;

      for (int i = 1; i < tokens.length; i++) {
         if (letters(tokens[i]) >= letters(tokens[best])) {
            best = i;
         }
      }

      return best;
   }

   private static int syllables(String word) {
      int count = 0;
      boolean inVowel = false;

      for (int i = 0; i < word.length(); i++) {
         boolean vowel = "aeiouyAEIOUY".indexOf(word.charAt(i)) >= 0;
         if (vowel && !inVowel) {
            count++;
         }

         inVowel = vowel;
      }

      if (count > 1 && (word.endsWith("e") || word.endsWith("E")) && !word.endsWith("le")) {
         count--;
      }

      return Math.max(1, count);
   }

   private static int letters(String word) {
      int n = 0;

      for (int i = 0; i < word.length(); i++) {
         if (Character.isLetterOrDigit(word.charAt(i))) {
            n++;
         }
      }

      return n;
   }

   private static int rowSplit(String[] tokens) {
      int total = String.join(" ", tokens).length();
      if (tokens.length >= 3 && total > 14) {
         int best = tokens.length;
         int bestScore = 2147483647;
         int before = 0;

         for (int i = 1; i < tokens.length; i++) {
            before += tokens[i - 1].length() + 1;
            int first = before - 1;
            int second = total - before;
            if (first <= 26 && second <= 32) {
               int score = Math.abs(first - second) - (tokens[i - 1].endsWith(",") ? 6 : 0);
               if (score < bestScore) {
                  bestScore = score;
                  best = i;
               }
            }
         }

         return best;
      } else {
         return tokens.length;
      }
   }

   private BufferedImage render(Font wordFont, TextLayout layout, double angle, int[] origin) {
      Rectangle2D bounds = layout.getBounds();
      int blur = this.glow.get() ? Math.max(2, (int)Math.round(wordFont.getSize2D() * 0.09)) : 0;
      int pad = blur * 3 + 4;
      Rectangle2D turned = AffineTransform.getRotateInstance(angle).createTransformedShape(bounds).getBounds2D();
      int width = (int)Math.ceil(turned.getWidth()) + pad * 2;
      int height = (int)Math.ceil(turned.getHeight()) + pad * 2;
      origin[0] = (int)Math.round(pad - turned.getX());
      origin[1] = (int)Math.round(pad - turned.getY());
      BufferedImage mask = new BufferedImage(width, height, 2);
      Graphics2D g = mask.createGraphics();
      hints(g);
      g.translate(origin[0], origin[1]);
      g.rotate(angle);
      g.setColor(Color.WHITE);
      layout.draw(g, 0.0F, 0.0F);
      g.dispose();
      SettingColor c = this.color.get();
      int[] shape = mask.getRGB(0, 0, width, height, null, 0, width);
      int[] glowAlpha = blur > 0 ? boxBlur(alphaOf(shape), width, height, blur) : null;
      int[] shadowAlpha = boxBlur(alphaOf(shape), width, height, Math.max(1, blur / 2));
      int shadowDx = Math.max(1, (int)Math.round(wordFont.getSize2D() * 0.03));
      int shadowDy = shadowDx + 1;
      float red = c.r / 255.0F;
      float green = c.g / 255.0F;
      float blue = c.b / 255.0F;
      int[] out = new int[width * height];

      for (int py = 0; py < height; py++) {
         for (int px = 0; px < width; px++) {
            int i = py * width + px;
            int sx = px - shadowDx;
            int sy = py - shadowDy;
            float shadowA = sx >= 0 && sy >= 0 ? shadowAlpha[sy * width + sx] / 255.0F * 0.38F : 0.0F;
            float glowA = glowAlpha == null ? 0.0F : glowAlpha[i] / 255.0F * 0.42F;
            float textA = (shape[i] >>> 24) / 255.0F * c.a / 255.0F;
            float lit = glowA + textA * (1.0F - glowA);
            float alpha = lit + shadowA * (1.0F - lit);
            if (!(alpha <= 0.0F)) {
               float share = lit / alpha;
               out[i] = Math.round(alpha * 255.0F) << 24
                  | Math.round(red * share * 255.0F) << 16
                  | Math.round(green * share * 255.0F) << 8
                  | Math.round(blue * share * 255.0F);
            }
         }
      }

      BufferedImage image = new BufferedImage(width, height, 2);
      image.setRGB(0, 0, width, height, out, 0, width);
      return image;
   }

   private static int[] alphaOf(int[] argb) {
      int[] out = new int[argb.length];

      for (int i = 0; i < argb.length; i++) {
         out[i] = argb[i] >>> 24;
      }

      return out;
   }

   private static int[] boxBlur(int[] alpha, int width, int height, int radius) {
      int[] a = alpha;
      int[] b = new int[alpha.length];

      for (int pass = 0; pass < 2; pass++) {
         for (int y = 0; y < height; y++) {
            int sum = 0;

            for (int x = -radius; x <= radius; x++) {
               sum += a[y * width + Mth.clamp(x, 0, width - 1)];
            }

            for (int x = 0; x < width; x++) {
               b[y * width + x] = sum / (radius * 2 + 1);
               sum += a[y * width + Math.min(width - 1, x + radius + 1)] - a[y * width + Math.max(0, x - radius)];
            }
         }

         for (int x = 0; x < width; x++) {
            int sum = 0;

            for (int y = -radius; y <= radius; y++) {
               sum += b[Mth.clamp(y, 0, height - 1) * width + x];
            }

            for (int y = 0; y < height; y++) {
               a[y * width + x] = sum / (radius * 2 + 1);
               sum += b[Math.min(height - 1, y + radius + 1) * width + x] - b[Math.max(0, y - radius) * width + x];
            }
         }
      }

      return a;
   }

   private static void hints(Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setComposite(AlphaComposite.SrcOver);
   }

   private void drop(SkyLyrics.Line line) {
      if (line != null) {
         this.mc.getTextureManager().release(line.texture);
      }
   }

   private Font loadFont() {
      if (this.font == null && !this.fontFailed) {
         try (InputStream in = SkyLyrics.class.getResourceAsStream("/assets/specter/font/caveat-bold.ttf")) {
            if (in == null) {
               throw new IllegalStateException("missing /assets/specter/font/caveat-bold.ttf");
            }

            this.font = Font.createFont(0, in);
         } catch (Exception var6) {
            this.fontFailed = true;
            SpecterClientAddon.LOG.warn("Sky Lyrics: could not load the handwriting font", var6);
         }

         return this.font;
      } else {
         return this.font;
      }
   }

   private static long mix(long v) {
      v = (v ^ v >>> 33) * -49064778989728563L;
      v = (v ^ v >>> 33) * -4265267296055464877L;
      return v ^ v >>> 33;
   }

   private static double unit(long v) {
      return (mix(v) >>> 11) * 1.1102230246251565E-16;
   }

   private static final class Line {
      final String song;
      final int index;
      final int pixelHeight;
      final int atlasWidth;
      final int atlasHeight;
      final List<SkyLyrics.Word> words;
      final double centerX;
      final double centerY;
      final long startMs;
      final long endMs;
      final Identifier texture;
      float anchorYaw;
      long appearedAt;
      long offscreenSince = -1L;
      boolean drifting;

      Line(
         String song,
         int index,
         int pixelHeight,
         List<SkyLyrics.Word> words,
         double centerX,
         double centerY,
         long startMs,
         long endMs,
         Identifier texture,
         int atlasWidth,
         int atlasHeight
      ) {
         this.song = song;
         this.index = index;
         this.pixelHeight = pixelHeight;
         this.words = words;
         this.centerX = centerX;
         this.centerY = centerY;
         this.startMs = startMs;
         this.endMs = endMs;
         this.texture = texture;
         this.atlasWidth = atlasWidth;
         this.atlasHeight = atlasHeight;
      }
   }

   public static enum Placement {
      Sky,
      FollowView;
   }

   private record Word(double x, double y, int originX, int originY, int atlasX, int atlasY, int width, int height, long revealAt) {
   }
}
