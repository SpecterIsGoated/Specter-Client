package com.jfant.specter.phone.apps.travel;

import com.jfant.specter.phone.ui.Glyph;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * A DonutTravel destination: a whole look for the world. The {@code look} recipe switches on and tunes Specter's
 * own visual modules (sky colours, stars, time of day, colour grade, fog, glow) through a {@link TravelContext}, which
 * remembers every change so "Return home" puts all of it back. {@code ambience} adds client-side particles.
 *
 * <p>Add one with {@code Destinations.register(new Destination(...))}; DonutTravel lists it without other changes.
 */
public record Destination(
   String id,
   String name,
   String tagline,
   String description,
   Glyph glyph,
   int top,
   int bottom,
   Consumer<TravelContext> look,
   @Nullable AmbientParticles.Kind ambience
) {
}
