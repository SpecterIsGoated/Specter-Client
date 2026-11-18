package com.jfant.specter.core.utils.misc;

public interface ICopyable<T extends ICopyable<T>> {
   T set(T var1);

   T copy();
}
