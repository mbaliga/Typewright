// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/** The browser has no feature-file parser to run, and the text is not sent anywhere to be checked, so nothing here is checked. */
actual fun platformFeaChecker(): FeaChecker =
    UnavailableFeaChecker(
        "The browser has no feature-file parser, and your text is not sent anywhere to be checked, so nothing here is checked.",
    )
