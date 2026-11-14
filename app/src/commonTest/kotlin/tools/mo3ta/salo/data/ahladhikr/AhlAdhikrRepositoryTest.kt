package tools.mo3ta.salo.data.ahladhikr

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import tools.mo3ta.salo.domain.AhlAdhikrChallenge
import tools.mo3ta.salo.domain.AhlAdhikrMilestone
import tools.mo3ta.salo.domain.AhlAdhikrSnapshot
import tools.mo3ta.salo.domain.AhlAdhikrSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SAMPLE_JSON = """
{
  "generatedAt": "2026-11-14T12:00:00Z",
  "date": "2026-11-14",
  "source": "firebase-rtdb",
  "futureAddition": { "ignored": true },
  "challenges": {
    "salawat": {
      "30_days": [
        {"id": "ABC123", "name": "أحمد", "streak": 45},
        {"id": "DEF456", "streak": 31}
      ],
      "60_days": [{"id": "ABC123", "name": "أحمد", "streak": 45}],
      "90_days": []
    },
    "dhikr": {"30_days": [], "60_days": [], "90_days": []}
  }
}
"""

private fun repositoryWith(
    fetch: suspend () -> AhlAdhikrSnapshot,
    settings: MapSettings = MapSettings(),
): AhlAdhikrRepository = AhlAdhikrRepository(
    object : AhlAdhikrRemote {
        override suspend fun fetchSnapshot(): AhlAdhikrSnapshot = fetch()
    },
    AhlAdhikrStore(settings),
)

class AhlAdhikrRepositoryTest {

    @Test
    fun remoteSuccess_parsesAndCaches() = runTest {
        val settings = MapSettings()
        val repo = repositoryWith({ parse(SAMPLE_JSON) }, settings)
        val result = repo.load()
        assertEquals(AhlAdhikrSource.REMOTE, result.source)
        assertEquals("2026-11-14", result.snapshot?.date)
        val list = result.snapshot?.listFor(AhlAdhikrChallenge.SALAWAT, AhlAdhikrMilestone.D30)
        assertEquals(2, list?.size)
        assertEquals("أحمد", list?.first()?.name)
        assertEquals(31, list?.last()?.streak)
        // Cache holds the parsed snapshot JSON for the next failure.
        assertTrue(settings.getStringOrNull("ahl_adhikr_cache_json")?.contains("2026-11-14") == true)
    }

    @Test
    fun unknownFields_areIgnored() = runTest {
        val result = repositoryWith({ parse(SAMPLE_JSON) }).load()
        assertEquals(AhlAdhikrSource.REMOTE, result.source)
    }

    @Test
    fun missingSection_yieldsEmptyListNotCrash() = runTest {
        val result = repositoryWith({ parse(SAMPLE_JSON) }).load()
        // "quran" has no section at all — listFor must return empty, not throw.
        assertTrue(result.snapshot?.listFor(AhlAdhikrChallenge.QURAN, AhlAdhikrMilestone.D30)?.isEmpty() == true)
    }

    @Test
    fun fetchFailure_fallsBackToCache() = runTest {
        val settings = MapSettings()
        repositoryWith({ parse(SAMPLE_JSON) }, settings).load()

        val broken = repositoryWith({ throw AhlAdhikrException("network down") }, settings)
        val result = broken.load()
        assertEquals(AhlAdhikrSource.CACHE, result.source)
        assertEquals("2026-11-14", result.snapshot?.date)
    }

    @Test
    fun fetchFailureWithNoCache_isEmptyState() = runTest {
        val repo = repositoryWith({ throw AhlAdhikrException("network down") })
        val result = repo.load()
        assertEquals(AhlAdhikrSource.EMPTY, result.source)
        assertEquals(null, result.snapshot)
    }

    @Test
    fun malformedBody_countsAsFailure_notCrash() = runTest {
        val repo = repositoryWith({ parse("<html>404 page</html>") })
        val result = repo.load()
        assertEquals(AhlAdhikrSource.EMPTY, result.source)
    }

    @Test
    fun timeout_countsAsFailure() = runTest {
        // runTest's virtual clock makes the 15s repository timeout elapse instantly.
        val repo = repositoryWith({ delay(30_000); throw AssertionError("must not reach here") })
        val result = repo.load()
        assertEquals(AhlAdhikrSource.EMPTY, result.source)
    }

    @Test
    fun corruptCache_clearsItselfAndYieldsEmpty() = runTest {
        val settings = MapSettings()
        settings.putString("ahl_adhikr_cache_json", "not json at all")
        val repo = repositoryWith({ throw AhlAdhikrException("down") }, settings)
        val result = repo.load()
        assertEquals(AhlAdhikrSource.EMPTY, result.source)
        assertEquals(null, settings.getStringOrNull("ahl_adhikr_cache_json"))
    }

    @Test
    fun dtoParsing_rejectsGarbage() {
        assertFailsWith<SerializationException> {
            AHL_ADHIKR_JSON.decodeFromString<AhlAdhikrSnapshotDto>("{{{")
        }
    }

    @Test
    fun dtoParsing_dropsInvalidEntries() {
        val snapshot = parse(
            """
            {"generatedAt":"t","date":"d","challenges":{"ghars":{
              "30_days":[
                {"id":"","streak":40},
                {"id":"OK1","streak":0},
                {"id":"OK2","streak":-3},
                {"id":"OK3","name":"خالد","streak":33}
              ]
            }}}
            """.trimIndent(),
        )
        val list = snapshot.listFor(AhlAdhikrChallenge.GHARS, AhlAdhikrMilestone.D30)
        assertEquals(listOf("OK3"), list.map { it.id })
    }

    // Helpers -------------------------------------------------------------

    private fun parse(text: String): AhlAdhikrSnapshot =
        AHL_ADHIKR_JSON.decodeFromString<AhlAdhikrSnapshotDto>(text).toDomain()
}
