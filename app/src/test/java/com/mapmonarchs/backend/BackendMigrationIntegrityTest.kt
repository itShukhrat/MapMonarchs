package com.mapmonarchs.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Repo-integrity guard for the deployed backend.
 *
 * The Postgres h3 extension is NOT available on the hosted project (verified:
 * `create extension h3` -> 0A000, control file absent; see
 * supabase/migrations/README.md). So the applied SQL must never depend on it:
 * the H3 cell is computed in the Edge Function (h3-js) and passed in as text.
 *
 * This fails if anyone reintroduces a Postgres-h3 dependency into schema.sql or a
 * migration, or if the Edge Function goes missing -- turning "should work" into a
 * checked fact.
 */
class BackendMigrationIntegrityTest {

    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { File(it, "supabase/migrations").isDirectory }
        ?: error("could not locate supabase/migrations from ${System.getProperty("user.dir")}")

    private val migrations = File(repoRoot, "supabase/migrations")

    /** Executable SQL only -- header comments document the H3 rejection on purpose. */
    private fun executableSql(f: File): String =
        f.readLines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").lowercase()

    @Test
    fun `both deployed migrations and the runnable schema are present`() {
        assertTrue("foundation migration missing",
            File(migrations, "20260722120001_foundation.sql").isFile)
        assertTrue("capture migration missing",
            File(migrations, "20260722120002_capture_apply.sql").isFile)
        assertTrue("runnable schema.sql missing", File(repoRoot, "supabase/schema.sql").isFile)
    }

    @Test
    fun `applied SQL never depends on the unavailable postgres h3 extension`() {
        val files = migrations.listFiles { f -> f.extension == "sql" }!!.toList() +
            File(repoRoot, "supabase/schema.sql")
        files.forEach { sql ->
            val executable = executableSql(sql)
            assertFalse("${sql.name} enables the h3 extension, which is not available on this host",
                Regex("""create\s+extension[^;]*\bh3""").containsMatchIn(executable))
            assertFalse("${sql.name} calls a postgres h3 function; the cell must come from the Edge Function",
                executable.contains("h3_latlng_to_cell"))
        }
    }

    @Test
    fun `the edge function that computes h3 exists and uses the real h3 library`() {
        val fn = File(repoRoot, "supabase/functions/attempt_capture/index.ts")
        assertTrue("Edge Function missing", fn.isFile)
        assertTrue("Edge Function does not import the real H3 library", fn.readText().contains("h3-js"))
    }
}
