package app.embeddingspace.explorer

import androidx.sqlite.db.SupportSQLiteDatabase
import app.embeddingspace.explorer.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/** The database may only change through real migrations, and a migration must not touch the user's items. */
class MigrationTest {
    /** A stand-in database that remembers every statement it was asked to run. */
    private fun recorder(sink: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") sink.add(args[0] as String)
            null
        } as SupportSQLiteDatabase

    private fun run(m: androidx.room.migration.Migration): List<String> {
        val sql = ArrayList<String>()
        m.migrate(recorder(sql))
        return sql
    }

    @Test fun migrationOneToTwoOnlyDropsTheCollectionTables() {
        val sql = run(Migrations.MIGRATION_1_2)
        assertEquals(2, sql.size)
        assertTrue(sql.all { it.startsWith("DROP TABLE IF EXISTS") })
        assertTrue(sql.any { it.contains("collections") } && sql.any { it.contains("collection_items") })
    }

    @Test fun migrationNeverTouchesItemsClustersOrThePendingQueue() {
        val sql = run(Migrations.MIGRATION_1_2).joinToString(" ")
        for (table in listOf("`items`", "`clusters`", "`pending`")) assertFalse(table, sql.contains(table))
    }

    @Test fun migrationsRunFromTheShippedVersion() {
        assertEquals(1, Migrations.MIGRATION_1_2.startVersion)
        assertEquals(2, Migrations.MIGRATION_1_2.endVersion)
    }

    @Test fun everyVersionStepHasAMigration() {
        val steps = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals((1 until DB_VERSION).map { it to it + 1 }, steps.sortedBy { it.first })
    }

    @Test fun aMigrationIsNeverASilentWipe() {
        val source = File("src/main/kotlin/app/embeddingspace/explorer/data/db/Db.kt").readText()
        assertFalse(source.contains("fallbackToDestructiveMigration"))
        assertTrue(source.contains("addMigrations"))
    }

    @Test fun theSchemaIsExportedForEveryVersion() {
        val dir = File("schemas/app.embeddingspace.explorer.data.db.ExplorerDb")
        assertTrue("schema folder ${dir.absolutePath}", dir.isDirectory)
        assertTrue(File(dir, "$DB_VERSION.json").isFile)
    }

    private companion object {
        const val DB_VERSION = 2
    }
}
