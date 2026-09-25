// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals

class QaCorpusModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("qa/corpus", QaCorpusModule.NAME)
    }
}
