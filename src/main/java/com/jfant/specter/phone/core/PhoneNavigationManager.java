package com.jfant.specter.phone.core;

import org.jetbrains.annotations.Nullable;

/**
 * What is on the phone screen (home, an app, the app switcher) and the transition between them. Back goes to the
 * app's own back first, then home; Home always goes home; the switcher lists running apps.
 */
public final class PhoneNavigationManager {
   public enum Layer {
      HOME,
      APP,
      SWITCHER
   }

   private static final double OPEN_SECONDS = 0.24;
   private static final double CLOSE_SECONDS = 0.18;

   private final PhoneApplicationManager apps;
   private Layer layer = Layer.HOME;
   @Nullable
   private PhoneApplicationManager.Running current;
   // transition
   private Layer from = Layer.HOME;
   @Nullable
   private PhoneApplicationManager.Running fromApp;
   private double since = -10;
   private double length = OPEN_SECONDS;
   private float originX = 90;
   private float originY = 196;

   public PhoneNavigationManager(PhoneApplicationManager apps) {
      this.apps = apps;
   }

   public Layer layer() {
      return this.layer;
   }

   @Nullable
   public PhoneApplicationManager.Running current() {
      return this.layer == Layer.APP ? this.current : null;
   }

   /** The app shown (or last shown) on the APP layer, even while switching away. */
   @Nullable
   public PhoneApplicationManager.Running lastApp() {
      return this.current;
   }

   @Nullable
   public PhoneApplicationManager.Running fromApp() {
      return this.fromApp;
   }

   public Layer from() {
      return this.from;
   }

   /** 0..1 progress of the running transition (1 = settled). */
   public float transition(double now) {
      return (float)Math.clamp((now - this.since) / this.length, 0.0, 1.0);
   }

   public float originX() {
      return this.originX;
   }

   public float originY() {
      return this.originY;
   }

   public void open(PhoneApplicationManager.Running app, @Nullable String route, float fromX, float fromY, double now) {
      if (this.current != null && this.current != app && this.layer == Layer.APP) this.apps.toBack(this.current);
      this.begin(now, OPEN_SECONDS);
      this.originX = fromX;
      this.originY = fromY;
      this.current = app;
      this.layer = Layer.APP;
      this.apps.toFront(app, route);
   }

   public void home(double now) {
      if (this.layer == Layer.HOME) return;
      if (this.layer == Layer.APP && this.current != null) this.apps.toBack(this.current);
      this.begin(now, CLOSE_SECONDS);
      this.layer = Layer.HOME;
   }

   public void switcher(double now) {
      if (this.layer == Layer.SWITCHER) {
         this.home(now);
         return;
      }
      if (this.layer == Layer.APP && this.current != null) this.apps.toBack(this.current);
      this.begin(now, OPEN_SECONDS);
      this.layer = Layer.SWITCHER;
   }

   /** The phone was put away: the app in front goes to the back (it reopens where it was next time). */
   public void suspend() {
      if (this.layer == Layer.APP && this.current != null) this.apps.toBack(this.current);
   }

   /** The phone came out again. */
   public void resume() {
      if (this.layer == Layer.APP && this.current != null) {
         if (this.apps.running(this.current.info().id()) == this.current) this.apps.toFront(this.current, null);
         else this.layer = Layer.HOME;
      }
   }

   /** An app was killed (switcher or crash restart): leave it if it is on screen. */
   public void forget(String id) {
      if (this.current != null && this.current.info().id().equals(id)) {
         this.current = null;
         if (this.layer == Layer.APP) this.layer = Layer.HOME;
      }
      if (this.fromApp != null && this.fromApp.info().id().equals(id)) this.fromApp = null;
   }

   private void begin(double now, double seconds) {
      this.from = this.layer;
      this.fromApp = this.current;
      this.since = now;
      this.length = seconds;
   }
}
