package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.lyrics.Lrc;
import com.jfant.specter.lyrics.Lyrics;
import com.jfant.specter.lyrics.LyricsFinder;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import com.jfant.specter.render.PurpleCard;
import com.mojang.blaze3d.platform.NativeImage;
import de.labystudio.spotifyapi.SpotifyAPI;
import de.labystudio.spotifyapi.SpotifyAPIFactory;
import de.labystudio.spotifyapi.SpotifyListenerAdapter;
import de.labystudio.spotifyapi.model.MediaKey;
import de.labystudio.spotifyapi.model.Track;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

public class SpotifyHud extends Module {
   private static final Identifier ART_TEXTURE = Identifier.fromNamespaceAndPath("specter-client", "spotify_art");
   private static final int ART_TEXTURE_SIZE = 128;
   private static final int BAR_LOW = -10806072;
   private static final int SEEK_FILL_BOTTOM = -4178720;
   private static final int ART_DIM = 1879048192;
   private static final int PAD = 7;
   private static final int ART = 36;
   private static final int GAP = 8;
   private static final int LINE = 12;
   private static final int TEXT_BLOCK = 32;
   private static final int EQ_HEIGHT = 10;
   private static final int BUTTON = 9;
   private static final long FLASH_MS = 180L;
   private static final int LYRIC_LINE = 10;
   private static final int LYRIC_GAP = 3;
   private static final int LYRIC_FAR = -7374667;
   private static final long LYRIC_GLIDE_MS = 260L;
   private static final int BAR_WIDTH = 2;
   private static final int BAR_GAP = 1;
   private static final int MAX_BARS = 256;
   private static final long MARQUEE_PAUSE_MS = 2000L;
   private static final float MARQUEE_SPEED = 25.0F;
   private static final int MARQUEE_GAP = 24;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPosition = this.settings.createGroup("Position");
   private final Setting<Boolean> albumArt = this.sgGeneral
      .add(new BoolSetting.Builder().name("album-art").description("Show the album cover of the current song.").defaultValue(true).build());
   private final Setting<Boolean> equalizer = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("equalizer")
            .description("Bars that bounce while music plays. Just for looks, they do not follow the real audio.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> progressBar = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("progress-bar")
            .description("Show how far into the song you are, with back and skip buttons at its ends (click them with chat open).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> width = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("width")
            .description("The narrowest the card gets. It widens to fit a long title or artist, up to max-width.")
            .defaultValue(200)
            .min(150)
            .sliderRange(150, 360)
            .build()
      );
   private final Setting<Integer> maxWidth = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-width")
            .description("The widest the card gets. A title longer than this scrolls sideways instead; an artist is cut short.")
            .defaultValue(400)
            .min(150)
            .sliderRange(150, 600)
            .build()
      );
   private final Setting<SpotifyHud.LyricsMode> lyrics = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("lyrics"))
                  .description(
                     "Synced lyrics under the seek bar, from lrclib.net. One line: the current line. Three lines: the one before, the current one and the next. They are looked up once per song and kept on disk."
                  ))
               .defaultValue(SpotifyHud.LyricsMode.Off))
            .build()
      );
   private final CardSpot spot = new CardSpot(this, this.sgPosition, Corner.TopRight, 4, 4);
   private final Setting<Double> scale = this.sgPosition
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .build()
      );
   private SpotifyAPI api;
   private volatile Track track;
   private volatile SpotifyHud.PendingArt pendingArt;
   private DynamicTexture artTexture;
   private LyricsFinder finder;
   private String lyricSong;
   private int lyricIndex = -1;
   private int lyricShift;
   private long lyricChanged;
   private final float[] bars = new float[256];
   private final float[] barPhase = new float[256];
   private final float[] barSpeed = new float[256];
   private long lastFrameNanos;
   private String marqueeTitle = "";
   private long marqueeSince;
   private final int[] backBox = new int[4];
   private final int[] skipBox = new int[4];
   private final int[] artBox = new int[4];
   private boolean buttonsShown;
   private boolean artShown;
   private float lastScale = 1.0F;
   private MediaKey flashKey;
   private long flashAt;

   public SpotifyHud() {
      super(
         SpecterClientAddon.HUD,
         "spotify",
         "Shows what the Spotify desktop app is playing, on a purple card like Where Are You. With chat open, click the cover to pause or play, and the arrows by the seek bar to go back or skip."
      );
      this.spot.onClick((x, y) -> this.clicked(x / this.lastScale, y / this.lastScale));
      Random random = new Random();

      for (int i = 0; i < 256; i++) {
         this.bars[i] = 1.0F;
         this.barPhase[i] = random.nextFloat() * 6.2831855F;
         this.barSpeed[i] = 5.0F + random.nextFloat() * 8.0F;
      }
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
            SpotifyHud.this.track = changed;
            BufferedImage cover = changed == null ? null : changed.getCoverArt();
            SpotifyHud.this.pendingArt = new SpotifyHud.PendingArt(cover == null ? null : SpotifyHud.shrink(cover, 128));
         }

         public void onDisconnect(Exception exception) {
            SpotifyHud.this.track = null;
            SpotifyHud.this.pendingArt = new SpotifyHud.PendingArt(null);
         }
      });
      this.api = created;
      Thread thread = new Thread(() -> {
         try {
            created.initialize();
         } catch (Throwable var2x) {
            SpecterClientAddon.LOG.warn("Spotify reader failed to start", var2x);
         }
      }, "specter-spotify");
      thread.setDaemon(true);
      thread.start();
   }

   @Override
   public void onDeactivate() {
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

      this.track = null;
      this.pendingArt = null;
      if (this.artTexture != null) {
         this.mc.getTextureManager().release(ART_TEXTURE);
         this.artTexture = null;
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (!this.mc.gui.hud.isHidden() && !this.mc.debugEntries.isOverlayVisible()) {
         this.uploadPendingArt();
         GuiGraphicsExtractor ctx = event.graphics;
         Font tr = this.mc.font;
         SpotifyAPI spotify = this.api;
         Track current = this.track;
         boolean connected = spotify != null && spotify.isConnected();
         boolean playing = connected && current != null && spotify.isPlaying();
         int positionMs = connected && current != null ? position(spotify) : -1;
         boolean showArt = this.albumArt.get();
         SpotifyHud.Labels labels = labels(current, connected, playing);
         int infoX = showArt ? 51 : 7;
         int longest = Math.max(CardText.width(labels.status()), Math.max(CardText.width(labels.title()), CardText.width(labels.artist())));
         int boxWidth = Math.clamp((long)(infoX + longest + 7), this.width.get(), Math.max(this.width.get(), this.maxWidth.get()));
         int top = showArt ? 36 : 32;
         int y = 7 + top + 5;
         int eqBottom = y + 10;
         if (this.equalizer.get()) {
            y = eqBottom + 4;
         }

         int progressY = y;
         if (this.progressBar.get()) {
            y += 8;
         } else if (!this.equalizer.get()) {
            y -= 5;
         }

         int lyricRows = current == null ? 0 : this.lyrics.get().rows;
         int lyricsY = y + 3;
         if (lyricRows > 0) {
            y += 3 + lyricRows * 10;
         }

         int boxHeight = y + 7;
         float s = this.scale.get().floatValue();
         this.lastScale = s;
         this.spot.place(boxWidth * s, boxHeight * s);
         float x = this.spot.x();
         float cardY = this.spot.y();
         float mouseX = -1.0F;
         float mouseY = -1.0F;
         if (this.mc.gui.screen() instanceof ChatScreen) {
            mouseX = ((float)this.mc.mouseHandler.getScaledXPos(this.mc.getWindow()) - Math.round(x)) / s;
            mouseY = ((float)this.mc.mouseHandler.getScaledYPos(this.mc.getWindow()) - Math.round(cardY)) / s;
         }

         Matrix3x2fStack matrices = ctx.pose();
         matrices.pushMatrix();
         matrices.translate(Math.round(x), Math.round(cardY));
         matrices.scale(s, s);
         PurpleCard.card(ctx, 0, 0, boxWidth, boxHeight);
         this.artShown = showArt;
         if (showArt) {
            set(this.artBox, 7, 7, 36, 36);
            this.drawArt(ctx, 7, 7, connected && current != null, playing, inside(this.artBox, mouseX, mouseY));
         }

         int infoWidth = boxWidth - 7 - infoX;
         this.drawText(ctx, tr, labels, infoX, 7 + (top - 32) / 2, infoWidth);
         if (this.equalizer.get()) {
            this.drawEqualizer(ctx, 7, eqBottom, boxWidth - 14, playing, positionMs);
         }

         this.buttonsShown = this.progressBar.get();
         if (this.buttonsShown) {
            set(this.backBox, 7, progressY - 1, 9, 10);
            set(this.skipBox, boxWidth - 7 - 9, progressY - 1, 9, 10);
            this.drawButton(ctx, SpotifyHud.Icon.BACK, this.backBox, MediaKey.PREV, mouseX, mouseY);
            this.drawButton(ctx, SpotifyHud.Icon.SKIP, this.skipBox, MediaKey.NEXT, mouseX, mouseY);
            int barX = 19;
            this.drawProgress(ctx, tr, barX, progressY, boxWidth - 2 * barX, positionMs, current == null ? 0 : current.getLength());
         }

         if (lyricRows > 0) {
            this.drawLyrics(ctx, tr, current, lyricRows, positionMs, lyricsY, boxWidth);
         }

         matrices.popMatrix();
      }
   }

   private void drawLyrics(GuiGraphicsExtractor ctx, Font tr, Track current, int rows, int positionMs, int top, int boxWidth) {
      if (this.finder == null) {
         this.finder = new LyricsFinder();
      }

      Lyrics found = this.finder.lookup(new LyricsFinder.Query(current.getName(), current.getArtist(), Math.round(current.getLength() / 1000.0F)));
      int maxWidth = boxWidth - 14;
      if (found.status() != Lyrics.Status.SYNCED) {
         SpotifyHud.LyricRow[] shown = lyricRows(found, rows, positionMs);

         for (int i = 0; i < shown.length; i++) {
            String text = clip(tr, shown[i].text(), maxWidth);
            if (!text.isEmpty()) {
               CardText.draw(ctx, text, (boxWidth - CardText.width(text)) / 2.0F, (float)(top + i * 10), shown[i].color(), true);
            }
         }
      } else {
         Lrc lrc = found.synced();
         int index = positionMs < 0 ? -1 : lrc.indexAt(positionMs);
         String song = current.getName() + "\n" + current.getArtist();
         long now = System.currentTimeMillis();
         if (!song.equals(this.lyricSong)) {
            this.lyricSong = song;
            this.lyricIndex = index;
            this.lyricShift = 0;
         } else if (index != this.lyricIndex) {
            this.lyricShift = Integer.signum(index - this.lyricIndex);
            this.lyricIndex = index;
            this.lyricChanged = now;
         }

         float t = Math.min(1.0F, (float)(now - this.lyricChanged) / 260.0F);
         float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
         float lag = this.lyricShift * (1.0F - eased);
         float middle = top + rows / 2 * 10;

         for (int k = index - 2; k <= index + 2; k++) {
            float row = k - index + lag;
            float distance = Math.abs(row);
            float alpha = rows >= 3 ? Math.clamp(4.0F - 3.0F * distance, 0.0F, 1.0F) : Math.clamp(1.0F - 2.0F * distance, 0.0F, 1.0F);
            if (!(alpha < 0.04F)) {
               String line = k >= 0 && k < lrc.size() ? (lrc.line(k).gap() ? "..." : lrc.line(k).text()) : (k == index ? "..." : "");
               String text = clip(tr, line, maxWidth);
               if (!text.isEmpty()) {
                  int color = lerpColor(-1, -7374667, Math.min(1.0F, distance));
                  int argb = Math.round(alpha * (color >>> 24)) << 24 | color & 16777215;
                  CardText.draw(ctx, text, (boxWidth - CardText.width(text)) / 2.0F, middle + row * 10.0F, argb, true);
               }
            }
         }
      }
   }

   private static int lerpColor(int from, int to, float t) {
      int out = 0;

      for (int shift = 0; shift <= 24; shift += 8) {
         int a = from >>> shift & 0xFF;
         int b = to >>> shift & 0xFF;
         out |= Math.round(a + (b - a) * t) << shift;
      }

      return out;
   }

   private static SpotifyHud.LyricRow[] lyricRows(Lyrics found, int rows, int positionMs) {
      SpotifyHud.LyricRow[] out = new SpotifyHud.LyricRow[rows];
      Arrays.fill(out, new SpotifyHud.LyricRow("", -7374667));
      int middle = rows / 2;
      if (found.status() != Lyrics.Status.SYNCED) {
         out[middle] = new SpotifyHud.LyricRow(switch (found.status()) {
            case LOADING -> "Loading lyrics...";
            case UNSYNCED -> "Lyrics are not synced";
            case INSTRUMENTAL -> "Instrumental";
            case UNAVAILABLE -> "Lyrics unavailable";
            default -> "No lyrics found";
         }, -4149795);
         return out;
      } else {
         Lrc lrc = found.synced();
         int index = positionMs < 0 ? -1 : lrc.indexAt(positionMs);
         out[middle] = new SpotifyHud.LyricRow(index >= 0 && !lrc.line(index).gap() ? lrc.line(index).text() : "...", -1);
         if (rows >= 3) {
            if (index - 1 >= 0 && index >= 0) {
               out[0] = new SpotifyHud.LyricRow(lrc.line(index - 1).text(), -7374667);
            }

            if (index + 1 < lrc.size()) {
               out[2] = new SpotifyHud.LyricRow(lrc.line(index + 1).text(), -7374667);
            }
         }

         return out;
      }
   }

   private void drawArt(GuiGraphicsExtractor ctx, int x, int y, boolean hasTrack, boolean playing, boolean hovered) {
      PurpleCard.fillRounded(ctx, x - 1, y - 1, x + 36 + 1, y + 36 + 1, 4, 15, -1333837902, -1333837902);
      if (this.artTexture != null) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, ART_TEXTURE, x, y, 0.0F, 0.0F, 36, 36, 36, 36);
         PurpleCard.maskCorners(ctx, x, y, x + 36, y + 36, 3, -1333837902);
      } else {
         PurpleCard.fillRounded(ctx, x, y, x + 36, y + 36, 3, 15, -2145973978, -2145973978);
      }

      if (hasTrack) {
         if (!playing || hovered) {
            PurpleCard.fillRounded(ctx, x, y, x + 36, y + 36, 3, 15, 1879048192, 1879048192);
         }

         if (hovered) {
            Matrix3x2fStack matrices = ctx.pose();
            matrices.pushMatrix();
            matrices.translate(x + 18.0F - 6.0F, y + 18.0F - 7.0F);
            matrices.scale(2.0F, 2.0F);
            icon(ctx, playing ? SpotifyHud.Icon.PAUSE : SpotifyHud.Icon.PLAY, 0, 0, this.flashing(MediaKey.PLAY_PAUSE) ? -1065473 : -1);
            matrices.popMatrix();
         }
      }
   }

   private void drawButton(GuiGraphicsExtractor ctx, SpotifyHud.Icon icon, int[] box, MediaKey key, float mouseX, float mouseY) {
      int color = this.flashing(key) ? -1065473 : (inside(box, mouseX, mouseY) ? -1 : -4149795);
      icon(ctx, icon, box[0] + (box[2] - 6) / 2, box[1] + 1, color);
   }

   private static void icon(GuiGraphicsExtractor ctx, SpotifyHud.Icon icon, int x, int y, int color) {
      int shadow = color & 0xFF000000 | (color & 16579836) >> 2;
      iconShape(ctx, icon, x + 1, y + 1, shadow);
      iconShape(ctx, icon, x, y, color);
   }

   private static void iconShape(GuiGraphicsExtractor ctx, SpotifyHud.Icon icon, int x, int y, int color) {
      switch (icon) {
         case BACK:
            ctx.fill(x, y, x + 1, y + 7, color);
            triangle(ctx, x + 2, y, false, color);
            break;
         case SKIP:
            triangle(ctx, x, y, true, color);
            ctx.fill(x + 5, y, x + 6, y + 7, color);
            break;
         case PLAY:
            triangle(ctx, x + 1, y, true, color);
            break;
         case PAUSE:
            ctx.fill(x, y, x + 2, y + 7, color);
            ctx.fill(x + 4, y, x + 6, y + 7, color);
      }
   }

   private static void triangle(GuiGraphicsExtractor ctx, int x, int y, boolean right, int color) {
      for (int row = 0; row < 7; row++) {
         int length = 4 - Math.abs(row - 3);
         if (right) {
            ctx.fill(x, y + row, x + length, y + row + 1, color);
         } else {
            ctx.fill(x + 4 - length, y + row, x + 4, y + row + 1, color);
         }
      }
   }

   private boolean clicked(float x, float y) {
      SpotifyAPI spotify = this.api;
      if (spotify != null && spotify.isConnected()) {
         MediaKey key = null;
         if (this.artShown && inside(this.artBox, x, y)) {
            key = MediaKey.PLAY_PAUSE;
         } else if (this.buttonsShown && inside(this.backBox, x, y)) {
            key = MediaKey.PREV;
         } else if (this.buttonsShown && inside(this.skipBox, x, y)) {
            key = MediaKey.NEXT;
         }

         if (key == null) {
            return false;
         } else {
            this.flashKey = key;
            this.flashAt = System.currentTimeMillis();
            MediaKey pressed = key;
            Thread thread = new Thread(() -> {
               try {
                  spotify.pressMediaKey(pressed);
               } catch (Throwable var3x) {
                  SpecterClientAddon.LOG.warn("Spotify: could not send {}", pressed, var3x);
               }
            }, "specter-spotify-key");
            thread.setDaemon(true);
            thread.start();
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean flashing(MediaKey key) {
      return this.flashKey == key && System.currentTimeMillis() - this.flashAt < 180L;
   }

   private static void set(int[] box, int x, int y, int width, int height) {
      box[0] = x;
      box[1] = y;
      box[2] = width;
      box[3] = height;
   }

   private static boolean inside(int[] box, float x, float y) {
      return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
   }

   private static SpotifyHud.Labels labels(Track current, boolean connected, boolean playing) {
      return current == null
         ? new SpotifyHud.Labels("Nothing playing", -4149795, connected ? "Nothing queued" : "Start Spotify", connected ? "" : "The desktop app")
         : new SpotifyHud.Labels(
            playing ? "Now Playing" : "Paused",
            playing ? -1065473 : -1911561,
            current.getName() == null ? "" : current.getName(),
            current.getArtist() == null ? "" : current.getArtist()
         );
   }

   private void drawText(GuiGraphicsExtractor ctx, Font tr, SpotifyHud.Labels labels, int x, int y, int maxWidth) {
      CardText.draw(ctx, labels.status(), (float)x, (float)y, labels.statusColor(), true);
      this.drawTitle(ctx, tr, labels.title(), x, y + 12, maxWidth);
      CardText.draw(ctx, clip(tr, labels.artist(), maxWidth), (float)x, (float)(y + 24), -1911561, true);
   }

   private void drawTitle(GuiGraphicsExtractor ctx, Font tr, String title, int x, int y, int maxWidth) {
      long now = System.currentTimeMillis();
      if (!title.equals(this.marqueeTitle)) {
         this.marqueeTitle = title;
         this.marqueeSince = now;
      }

      int titleWidth = CardText.width(title);
      if (titleWidth <= maxWidth) {
         CardText.draw(ctx, title, (float)x, (float)y, -1, true);
      } else {
         int travel = titleWidth + 24;
         float offset = Math.max(0.0F, (float)(now - this.marqueeSince - 2000L) * 25.0F / 1000.0F);
         if (offset >= travel) {
            this.marqueeSince = now;
            offset = 0.0F;
         }

         ctx.enableScissor(x, y - 1, x + maxWidth, y + 9);
         CardText.draw(ctx, title, (float)(x - Math.round(offset)), (float)y, -1, true);
         CardText.draw(ctx, title, (float)(x - Math.round(offset) + travel), (float)y, -1, true);
         ctx.disableScissor();
      }
   }

   private void drawEqualizer(GuiGraphicsExtractor ctx, int x, int bottom, int innerWidth, boolean playing, int positionMs) {
      long nowNanos = System.nanoTime();
      float dt = this.lastFrameNanos == 0L ? 0.0F : Math.min(0.1F, (float)(nowNanos - this.lastFrameNanos) / 1.0E9F);
      this.lastFrameNanos = nowNanos;
      int unit = 3;
      int count = Math.clamp((long)(innerWidth / unit), 1, 256);
      int startX = x + (innerWidth - (count * unit - 1)) / 2;
      double ms = positionMs >= 0 ? positionMs : System.currentTimeMillis();
      float kick = (float)Math.exp(-4.0 * (ms % 500.0) / 500.0);
      float hat = (float)Math.exp(-6.0 * (ms % 250.0) / 250.0) * 0.6F;
      float seconds = (float)(ms / 1000.0);
      float ease = 1.0F - (float)Math.exp(-dt * 18.0F);

      for (int i = 0; i < count; i++) {
         float along = count == 1 ? 0.0F : (float)i / (count - 1);
         float target = 1.0F;
         if (playing) {
            float wobble = (float)Math.sin(seconds * this.barSpeed[i] + this.barPhase[i]) * 0.5F + 0.5F;
            float energy = (1.0F - along) * kick * 0.8F + along * hat + wobble * 0.45F;
            target = 1.0F + Math.min(1.0F, energy) * (1.0F - 0.35F * along) * 9.0F;
         }

         this.bars[i] = this.bars[i] + (target - this.bars[i]) * ease;
         int height = Math.clamp((long)Math.round(this.bars[i]), 1, 10);
         int barX = startX + i * unit;
         ctx.fillGradient(barX, bottom - height, barX + 2, bottom, PurpleCard.lerp(-10806072, -1065473, height / 10.0F), -10806072);
      }
   }

   private void drawProgress(GuiGraphicsExtractor ctx, Font tr, int x, int y, int innerWidth, int positionMs, int lengthMs) {
      String elapsed = positionMs >= 0 ? time(positionMs) : "-:--";
      String total = lengthMs > 0 ? time(lengthMs) : "-:--";
      int totalWidth = CardText.width(total);
      CardText.draw(ctx, elapsed, (float)x, (float)y, -4149795, true);
      CardText.draw(ctx, total, (float)(x + innerWidth - totalWidth), (float)y, -4149795, true);
      int barLeft = x + CardText.width(elapsed) + 6;
      int barRight = x + innerWidth - totalWidth - 6;
      if (barRight - barLeft >= 10) {
         int barTop = y + 2;
         int barBottom = y + 6;
         PurpleCard.fillRounded(ctx, barLeft, barTop, barRight, barBottom, 2, 15, -1333837902, -1333837902);
         if (positionMs >= 0 && lengthMs > 0) {
            float done = Math.clamp((float)positionMs / lengthMs, 0.0F, 1.0F);
            int fillRight = barLeft + Math.max(4, Math.round((barRight - barLeft) * done));
            PurpleCard.fillRounded(ctx, barLeft, barTop, fillRight, barBottom, 2, 15, -1065473, -4178720);
            ctx.fill(fillRight - 2, barTop - 1, fillRight, barBottom + 1, -1);
         }
      }
   }

   private void uploadPendingArt() {
      SpotifyHud.PendingArt pending = this.pendingArt;
      if (pending != null) {
         this.pendingArt = null;
         if (this.artTexture != null) {
            this.mc.getTextureManager().release(ART_TEXTURE);
            this.artTexture = null;
         }

         if (pending.image() != null) {
            BufferedImage image = pending.image();
            NativeImage pixels = new NativeImage(image.getWidth(), image.getHeight(), false);

            for (int py = 0; py < image.getHeight(); py++) {
               for (int px = 0; px < image.getWidth(); px++) {
                  pixels.setPixel(px, py, image.getRGB(px, py));
               }
            }

            this.artTexture = new DynamicTexture(() -> "specter-spotify-art", pixels);
            this.mc.getTextureManager().register(ART_TEXTURE, this.artTexture);
         }
      }
   }

   private static int position(SpotifyAPI spotify) {
      try {
         return spotify.hasPosition() ? spotify.getPosition() : -1;
      } catch (IllegalStateException var2) {
         return -1;
      }
   }

   private static BufferedImage shrink(BufferedImage source, int size) {
      BufferedImage image = source;
      int w = source.getWidth();

      for (int h = source.getHeight(); w / 2 >= size && h / 2 >= size; image = resize(image, w, h)) {
         w /= 2;
         h /= 2;
      }

      return resize(image, size, size);
   }

   private static BufferedImage resize(BufferedImage image, int w, int h) {
      BufferedImage out = new BufferedImage(w, h, 2);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image, 0, 0, w, h, null);
      g.dispose();
      return out;
   }

   private static String clip(Font tr, String text, int maxWidth) {
      if (CardText.width(text) <= maxWidth) {
         return text;
      } else {
         String out = text;

         while (!out.isEmpty() && CardText.width(out + "...") > maxWidth) {
            out = out.substring(0, out.length() - 1);
         }

         return out.stripTrailing() + "...";
      }
   }

   private static String time(int ms) {
      int seconds = Math.max(0, ms / 1000);
      return seconds >= 3600
         ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
         : String.format("%d:%02d", seconds / 60, seconds % 60);
   }

   private static enum Icon {
      BACK,
      SKIP,
      PLAY,
      PAUSE;
   }

   private record Labels(String status, int statusColor, String title, String artist) {
   }

   private record LyricRow(String text, int color) {
   }

   public static enum LyricsMode {
      Off("Off", 0),
      OneLine("One line", 1),
      ThreeLines("Three lines", 3);

      private final String label;
      private final int rows;

      private LyricsMode(String label, int rows) {
         this.label = label;
         this.rows = rows;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }

   private record PendingArt(BufferedImage image) {
   }
}
