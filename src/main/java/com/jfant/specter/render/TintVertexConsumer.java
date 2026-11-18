package com.jfant.specter.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class TintVertexConsumer implements VertexConsumer {
   private final VertexConsumer delegate;
   private final TintVertexConsumer.Tint tint;
   private float lastY;

   public TintVertexConsumer(VertexConsumer delegate, TintVertexConsumer.Tint tint) {
      this.delegate = delegate;
      this.tint = tint;
   }

   @Override
   public VertexConsumer addVertex(float x, float y, float z) {
      this.lastY = y;
      this.delegate.addVertex(x, y, z);
      return this;
   }

   @Override
   public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      int t = this.tint.rgb(this.lastY);
      float light = Math.min(1.0F, Math.max(red, Math.max(green, blue)) / 255.0F * 1.35F);
      this.delegate.setColor(mix(red, t >> 16 & 0xFF, light), mix(green, t >> 8 & 0xFF, light), mix(blue, t & 0xFF, light), alpha);
      return this;
   }

   private static int mix(int original, int tint, float light) {
      return Math.min(255, Math.round(original * 0.15F + tint * light * 0.85F));
   }

   @Override
   public VertexConsumer setColor(int argb) {
      return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
   }

   @Override
   public VertexConsumer setUv(float u, float v) {
      this.delegate.setUv(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv1(int u, int v) {
      this.delegate.setUv1(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv2(int u, int v) {
      this.delegate.setUv2(u, v);
      return this;
   }

   @Override
   public VertexConsumer setUv3(float u, float v) {
      this.delegate.setUv3(u, v);
      return this;
   }

   @Override
   public VertexConsumer setNormal(float x, float y, float z) {
      this.delegate.setNormal(x, y, z);
      return this;
   }

   @Override
   public VertexConsumer setLineWidth(float width) {
      this.delegate.setLineWidth(width);
      return this;
   }

   public interface Tint {
      int rgb(float var1);
   }
}
