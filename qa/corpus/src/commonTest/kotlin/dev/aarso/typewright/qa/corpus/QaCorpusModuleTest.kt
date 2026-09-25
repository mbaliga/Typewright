// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals

class QaCorpusModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("qa/corpus", QaCorpusModule.NAME)
    }
}
