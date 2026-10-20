package com.blackchapel.recipai.core

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckTest {
    private class FakeGitHub(val code: Int = 200, val body: String = "", val fail: IOException? = null) : HttpTransport {
        var lastUrl: String? = null
        var lastHeaders: Map<String, String> = emptyMap()

        override fun <T> execute(request: HttpRequest, handle: (HttpResponse) -> T): T {
            lastUrl = request.url
            lastHeaders = request.headers
            fail?.let { throw it }
            return handle(HttpResponse(code, emptyMap(), ByteArrayInputStream(body.toByteArray())))
        }
    }

    private fun release(tag: String, url: String = "https://github.com/blackchapel/recipai/releases/tag/$tag", extra: String = "") =
        """{"tag_name":"$tag","name":"$tag","html_url":"$url","draft":false,"prerelease":false,"assets":[{"name":"x"}]$extra}"""

    private fun check(installed: String, github: FakeGitHub) = UpdateChecker(github, "o/r").check(installed)

    @Test
    fun parsesReleaseAndTestBuildNames() {
        assertEquals(AppVersion(0, 1, 3), AppVersion.parse("0.1.3"))
        assertEquals(AppVersion(0, 1, 3), AppVersion.parse("v0.1.3"))
        assertEquals(AppVersion(0, 1, 0), AppVersion.parse("0.1"))
        assertEquals(AppVersion(0, 1, 0), AppVersion.parse("0.1-feature-app-scaffold.4296394"))
        assertEquals(AppVersion(12, 0, 40), AppVersion.parse(" 12.0.40 "))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1"))
        assertNull(AppVersion.parse("1.2.3.4"))
    }

    @Test
    fun ordersByNumberNotByText() {
        assertTrue(AppVersion(0, 1, 10) > AppVersion(0, 1, 9))
        assertTrue(AppVersion(0, 2, 0) > AppVersion(0, 1, 99))
        assertTrue(AppVersion(1, 0, 0) > AppVersion(0, 99, 99))
    }

    @Test
    fun aNewerReleaseIsAvailableWithItsPage() {
        val github = FakeGitHub(body = release("v0.2.0"))
        val result = check("0.1.4", github)
        assertEquals(UpdateResult.Available("0.2.0", "https://github.com/blackchapel/recipai/releases/tag/v0.2.0"), result)
        assertEquals("https://api.github.com/repos/o/r/releases/latest", github.lastUrl)
        assertEquals("Recipai-update-check", github.lastHeaders["User-Agent"])
    }

    @Test
    fun theSameOrAnOlderReleaseIsUpToDate() {
        assertEquals(UpdateResult.UpToDate, check("0.1.4", FakeGitHub(body = release("v0.1.4"))))
        assertEquals(UpdateResult.UpToDate, check("0.1.4", FakeGitHub(body = release("v0.1.3"))))
    }

    @Test
    fun aTestBuildIsToldAboutALaterReleaseOnly() {
        val test = "0.1-feature-app-scaffold.4296394"
        assertEquals(UpdateResult.UpToDate, check(test, FakeGitHub(body = release("v0.1.0"))))
        assertTrue(check(test, FakeGitHub(body = release("v0.1.1"))) is UpdateResult.Available)
    }

    @Test
    fun noReleaseYetIsUpToDate() {
        assertEquals(UpdateResult.UpToDate, check("0.1.0", FakeGitHub(code = 404, body = """{"message":"Not Found"}""")))
    }

    @Test
    fun draftsAndPrereleasesAreIgnored() {
        val pre = """{"tag_name":"v9.0.0","html_url":"https://github.com/o/r/x","draft":false,"prerelease":true}"""
        assertEquals(UpdateResult.UpToDate, check("0.1.0", FakeGitHub(body = pre)))
    }

    @Test
    fun aForeignReleaseAddressFallsBackToTheReleasesPage() {
        val result = check("0.1.0", FakeGitHub(body = release("v0.2.0", url = "https://evil.example.com/get")))
        assertEquals(UpdateResult.Available("0.2.0", "https://github.com/o/r/releases/latest"), result)
    }

    @Test
    fun failuresAreNamed() {
        assertEquals(UpdateResult.Failed(UpdateFailure.OFFLINE), check("0.1.0", FakeGitHub(fail = IOException("no route"))))
        assertEquals(UpdateResult.Failed(UpdateFailure.RATE_LIMITED), check("0.1.0", FakeGitHub(code = 403)))
        assertEquals(UpdateResult.Failed(UpdateFailure.FAILED), check("0.1.0", FakeGitHub(code = 500)))
        assertEquals(UpdateResult.Failed(UpdateFailure.FAILED), check("0.1.0", FakeGitHub(body = "<html>")))
        assertEquals(UpdateResult.Failed(UpdateFailure.FAILED), check("0.1.0", FakeGitHub(body = release("nightly"))))
        assertEquals(UpdateResult.Failed(UpdateFailure.FAILED), check("weird", FakeGitHub(body = release("v1.0.0"))))
    }
}
