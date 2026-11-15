package fr.epure.launcher.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateManifestTest {

    private val hash = "a".repeat(64)

    private fun json(launcherCode: Long = 7, moduleCode: Long = 2, url: String = "https://exemple.fr/Epure.apk", sha: String = hash) = """
        {
          "launcher": {"versionCode": $launcherCode, "versionName": "0.5.0", "url": "$url", "sha256": "$sha", "size": 3000, "notes": "Musique"},
          "module": {"versionCode": $moduleCode, "versionName": "1.1", "url": "https://exemple.fr/Module.apk", "sha256": "$hash"},
          "champFutur": true
        }
    """.trimIndent()

    @Test
    fun `fichier de versions lu, champs inconnus ignores`() {
        val manifest = UpdateManifests.parse(json())!!
        assertEquals(7L, manifest.launcher!!.versionCode)
        assertEquals("Musique", manifest.launcher!!.notes)
        assertEquals("1.1", manifest.module!!.versionName)
    }

    @Test
    fun `fichier refuse s il est illisible ou douteux`() {
        assertNull(UpdateManifests.parse("pas du json"))
        // Adresse non chiffrée, ou empreinte mal formée : on n'installe rien.
        assertNull(UpdateManifests.parse(json(url = "http://exemple.fr/Epure.apk")))
        assertNull(UpdateManifests.parse(json(sha = "1234")))
        assertNull(UpdateManifests.parse(json(sha = "z".repeat(64))))
    }

    @Test
    fun `seules les versions plus recentes sont proposees`() {
        val manifest = UpdateManifests.parse(json(launcherCode = 7, moduleCode = 2))!!
        // Launcher en 6, module en 1 : les deux sont proposés.
        assertEquals(
            listOf(UpdateTarget.LAUNCHER, UpdateTarget.MODULE),
            UpdateManifests.available(manifest, launcherVersion = 6, moduleVersion = 1).map { it.target },
        )
        // Déjà à jour.
        assertTrue(UpdateManifests.available(manifest, launcherVersion = 7, moduleVersion = 2).isEmpty())
        // Version installée plus récente que celle publiée : rien.
        assertTrue(UpdateManifests.available(manifest, launcherVersion = 9, moduleVersion = 5).isEmpty())
        // Module absent : pas proposé comme mise à jour.
        assertEquals(
            listOf(UpdateTarget.LAUNCHER),
            UpdateManifests.available(manifest, launcherVersion = 6, moduleVersion = null).map { it.target },
        )
    }

    @Test
    fun `empreinte SHA-256 d un fichier`() {
        val file = File.createTempFile("apk", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateManifests.sha256(file))
    }
}
