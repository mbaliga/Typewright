// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/** On a phone nothing parses the feature language yet: fontTools is not bundled (see [ChaquopyBackend]), so no text is checked here. */
actual fun platformFeaChecker(): FeaChecker =
    UnavailableFeaChecker("This phone has no feature-file parser in this version, so nothing here is checked until the font is built.")
