package com.m3.ai.model

/**
 * A model file the app knows how to use. The app never downloads models (ADR 0005); the owner
 * provides the file and [ModelStore] only accepts it if its size and SHA-256 match exactly.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val sourceUrl: String,
    /** False for text-only models: asking for a vision backend they don't have fails on every answer. */
    val vision: Boolean = true,
    /** Multi-token prediction on the GPU. Qwen3-4B's file fails to load with it. */
    val speculativeDecoding: Boolean = true,
    /** Added to each chat prompt. */
    val promptSuffix: String = "",
)

object ModelCatalog {
    // Hashes are the Git LFS object ids published by Hugging Face for these exact files
    // (checked 2026-10-18). Both repos are Apache-2.0 and ungated.
    val GEMMA_4_E4B = ModelSpec(
        id = "gemma-4-e4b",
        displayName = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        sourceUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
    )

    val GEMMA_4_E2B = ModelSpec(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        sourceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
    )

    /**
     * Apache-2.0, checked against Hugging Face 2026-11-01 (the repo's `qwen3_4b_mixed_int4.litertlm`). Without
     * `/no_think` it can spend the whole answer budget thinking and return nothing (phone eval).
     */
    val QWEN_3_4B = ModelSpec(
        id = "qwen3-4b",
        displayName = "Qwen3-4B",
        fileName = "Qwen3-4B-int4.litertlm",
        sizeBytes = 2_272_935_936L,
        sha256 = "4877b236f09c0b519958612afeb3c6fc6575c7ce56080d0d3a5406150e0ff9ce",
        sourceUrl = "https://huggingface.co/litert-community/Qwen3-4B",
        vision = false,
        speculativeDecoding = false,
        promptSuffix = " /no_think",
    )

    val all: List<ModelSpec> = listOf(QWEN_3_4B, GEMMA_4_E4B, GEMMA_4_E2B)

    /** Matches an owner-picked file by its exact byte size; the hash is checked while copying. */
    fun forSize(sizeBytes: Long): ModelSpec? = all.firstOrNull { it.sizeBytes == sizeBytes }
}
