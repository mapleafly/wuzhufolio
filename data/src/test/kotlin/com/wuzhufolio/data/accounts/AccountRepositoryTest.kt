package com.wuzhufolio.data.accounts

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.domain.accounts.UsernameTakenException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M003 + AccountRepository（T2.1 数据层）：迁移建表、唯一用户名、CRUD、先插后包原子性。 */
class AccountRepositoryTest {

    private val db: WzDatabase = WzDatabase(
        Files.createTempDirectory("wuzhufolio-accounts").resolve("accounts.db"),
        randomDbKey(),
    )
    private val gate = DbGate(db)
    private val repo = AccountRepository(gate)

    init {
        db.migrateToLatest()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `migration creates accounts table and starts empty`() {
        assertEquals(3, db.schemaVersion())
        assertTrue(!repo.existsAny())
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun `createWrapped inserts then wraps with real id and roundtrips`() {
        var seenId = -1
        val created = repo.createWrapped("alice", "hash", "salt", "params") { id ->
            seenId = id
            "v1wrapped-" + id
        }
        assertTrue(created.id > 0)
        assertEquals(seenId, created.id)
        assertEquals("v1wrapped-" + created.id, created.wrappedDek)
        val byName = repo.findByUsername("alice")
        assertNotNull(byName)
        assertEquals(created.id, byName.id)
        val byId = repo.findById(created.id)
        assertNotNull(byId)
        assertTrue(repo.existsAny())
        assertEquals(1, repo.list().size)
    }

    @Test
    fun `duplicate username is rejected with typed exception`() {
        repo.createWrapped("bob", "hash", "salt", "params") { "w1" }
        assertFailsWith<UsernameTakenException> {
            repo.createWrapped("bob", "hash", "salt", "params") { "w2" }
        }
    }

    @Test
    fun `updateCredentials rewrites only credential fields`() {
        val created = repo.createWrapped("carol", "hash", "salt", "params") { "w1" }
        repo.updateCredentials(created.id, "newhash", "newsalt", "newparams", "newwrapped")
        val updated = repo.findById(created.id)!!
        assertEquals("newhash", updated.passwordHash)
        assertEquals("newparams", updated.kdfParams)
        assertEquals("newwrapped", updated.wrappedDek)
        assertEquals("carol", updated.username)
        assertNull(repo.findByUsername("nobody"))
    }
}
