package com.m3.ai.model

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val bytes = ByteArray(3_000_000) { (it % 251).toByte() }
    private val spec = ModelSpec(
        id = "test",
        displayName = "Test",
        fileName = "test.litertlm",
        sizeBytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
        sourceUrl = "https://example.invalid",
    )

    private fun store(free: Long = Long.MAX_VALUE) = ModelStore(temp.root) { free }

    private fun leftovers() = temp.root.listFiles().orEmpty().map(File::getName).filter { it.endsWith(".part") }

    @Test
    fun `missing until imported, ready after a matching import`() = runTest {
        val store = store()
        assertEquals(ModelStatus.Missing, store.status(spec))

        val result = store.import(spec, ByteArrayInputStream(bytes))

        assertEquals(ModelCheckResult.Ok, result)
        assertEquals(ModelStatus.Ready, store.status(spec))
        assertTrue(store.fileFor(spec).readBytes().contentEquals(bytes))
    }

    @Test
    fun `wrong hash is rejected and leaves nothing behind`() = runTest {
        val store = store()
        val tampered = bytes.copyOf().also { it[1234] = (it[1234] + 1).toByte() }

        val result = store.import(spec, ByteArrayInputStream(tampered))

        assertEquals(ModelCheckResult.WrongHash, result)
        assertEquals(ModelStatus.Missing, store.status(spec))
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun `too short or too long files are rejected`() = runTest {
        val store = store()

        val short = store.import(spec, ByteArrayInputStream(bytes.copyOf(bytes.size - 1)))
        val long = store.import(spec, ByteArrayInputStream(bytes + byteArrayOf(0)))

        assertTrue(short is ModelCheckResult.WrongSize)
        assertTrue(long is ModelCheckResult.WrongSize)
        assertFalse(store.fileFor(spec).exists())
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun `refuses to start without enough free space`() = runTest {
        val result = store(free = 10).import(spec, ByteArrayInputStream(bytes))

        assertTrue(result is ModelCheckResult.NotEnoughSpace)
        assertFalse(store().fileFor(spec).exists())
    }

    @Test
    fun `a leftover partial copy is cleared before importing`() = runTest {
        val stale = File(temp.root, spec.fileName + ".part").apply { writeBytes(ByteArray(10)) }
        var spaceCheckedWithStale = true
        val store = ModelStore(temp.root) { spaceCheckedWithStale = stale.exists(); Long.MAX_VALUE }

        assertEquals(ModelCheckResult.Ok, store.import(spec, ByteArrayInputStream(bytes)))
        assertFalse(spaceCheckedWithStale)
        assertEquals(emptyList<String>(), leftovers())
    }

    @Test
    fun `a file copied in directly is unverified until checked`() = runTest {
        val store = store()
        store.fileFor(spec).writeBytes(bytes)
        assertEquals(ModelStatus.Unverified, store.status(spec))

        assertEquals(ModelCheckResult.Ok, store.verify(spec))
        assertEquals(ModelStatus.Ready, store.status(spec))
    }

    @Test
    fun `changing a verified file makes it unverified again`() = runTest {
        val store = store()
        store.import(spec, ByteArrayInputStream(bytes))
        val file = store.fileFor(spec)

        file.writeBytes(bytes.copyOf().also { it[0] = 99 })
        file.setLastModified(file.lastModified() + 5_000)

        assertEquals(ModelStatus.Unverified, store.status(spec))
        assertEquals(ModelCheckResult.WrongHash, store.verify(spec))
        assertEquals(ModelStatus.Unverified, store.status(spec))
    }

    @Test
    fun `delete removes the model`() = runTest {
        val store = store()
        store.import(spec, ByteArrayInputStream(bytes))

        store.delete(spec)

        assertEquals(ModelStatus.Missing, store.status(spec))
        assertEquals(emptyList<String>(), temp.root.list().orEmpty().toList())
    }

    @Test
    fun `catalog models are matched by exact size`() {
        assertEquals(ModelCatalog.GEMMA_4_E4B, ModelCatalog.forSize(3_659_530_240L))
        assertEquals(ModelCatalog.GEMMA_4_E2B, ModelCatalog.forSize(2_588_147_712L))
        assertEquals(ModelCatalog.QWEN_3_4B, ModelCatalog.forSize(2_272_935_936L))
        assertEquals(null, ModelCatalog.forSize(123L))
    }
}
