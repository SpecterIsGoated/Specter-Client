package com.jfant.specter.lyrics;

import com.jfant.specter.SpecterClientAddon;
import de.labystudio.spotifyapi.SpotifyAPI;
import de.labystudio.spotifyapi.platform.windows.api.jna.WindowsMediaControl;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SpotifyClock implements AutoCloseable {
   private static final long SAMPLE_MS = 25L;
   private final SpotifyAPI api;
   private final WindowsMediaControl control;
   private final ScheduledExecutorService sampler;
   private long lastRaw = -9223372036854775808L;
   private volatile long anchorMs = -1L;
   private volatile long anchorNanos;
   private volatile boolean playing;
   private volatile boolean settled;

   private SpotifyClock(SpotifyAPI api, WindowsMediaControl control) {
      this.api = api;
      this.control = control;
      this.sampler = Executors.newSingleThreadScheduledExecutor(task -> {
         Thread thread = new Thread(task, "specter-spotify-clock");
         thread.setDaemon(true);
         return thread;
      });
      this.sampler.scheduleWithFixedDelay(this::sample, 0L, 25L, TimeUnit.MILLISECONDS);
   }

   public static SpotifyClock attach(SpotifyAPI api) {
      try {
         Class<?> windows = Class.forName("de.labystudio.spotifyapi.platform.windows.WinSpotifyAPI");
         if (!windows.isInstance(api)) {
            return null;
         } else {
            Field field = windows.getDeclaredField("mediaControl");
            field.setAccessible(true);
            return field.get(null) instanceof WindowsMediaControl media ? new SpotifyClock(api, media) : null;
         }
      } catch (LinkageError | ReflectiveOperationException var5) {
         SpecterClientAddon.LOG.debug("Spotify clock unavailable: {}", var5.toString());
         return null;
      }
   }

   private void sample() {
      long raw;
      boolean nowPlaying;
      try {
         synchronized (this.api) {
            if (!this.control.isSpotifyAvailable()) {
               return;
            }

            raw = this.control.getPlaybackPosition();
            nowPlaying = this.control.isPlaying() == 1;
         }
      } catch (Throwable var7) {
         return;
      }

      long now = System.nanoTime();
      if (raw != this.lastRaw) {
         if (this.lastRaw != -9223372036854775808L) {
            this.settled = true;
         }

         this.lastRaw = raw;
         this.anchorMs = raw;
         this.anchorNanos = now;
      } else if (nowPlaying != this.playing && this.anchorMs >= 0L) {
         this.anchorMs = this.position(now);
         this.anchorNanos = now;
      }

      this.playing = nowPlaying;
   }

   public long positionMs() {
      return this.position(System.nanoTime());
   }

   private long position(long nanos) {
      long anchor = this.anchorMs;
      if (anchor < 0L) {
         return -1L;
      } else {
         return this.playing ? anchor + (nanos - this.anchorNanos) / 1000000L : anchor;
      }
   }

   public boolean settled() {
      return this.settled;
   }

   @Override
   public void close() {
      this.sampler.shutdownNow();
   }
}
