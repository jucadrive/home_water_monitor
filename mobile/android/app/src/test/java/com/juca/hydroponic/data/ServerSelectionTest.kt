package com.juca.hydroponic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSelectionTest {
    @Test fun newInstallUsesBundledServer() {
        assertEquals("https://fixture.invalid", ServerSelection.origin("https://fixture.invalid", null))
    }
    @Test fun upgradeKeepsPendingRequestAtOriginalServer() {
        assertEquals("https://previous.invalid", ServerSelection.origin("https://fixture.invalid", "https://previous.invalid"))
    }
}
