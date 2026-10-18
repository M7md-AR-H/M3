# LiteRT-LM calls back into Kotlin from native code (JNI) and ships no R8 rules of its own,
# so keep its classes and members unrenamed.
-keep class com.google.ai.edge.litertlm.** { *; }
