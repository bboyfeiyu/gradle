/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativecode.language.cpp.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.junit.runner.RunWith
/**
 * Runs a test separately for each installed tool chain.
 */
@RunWith(SingleToolChainTestRunner.class)
abstract class AbstractInstalledToolChainIntegrationSpec extends AbstractIntegrationSpec {
    static AvailableToolChains.InstalledToolChain toolChain

    def NativeInstallationFixture installation(Object installDir) {
        return new NativeInstallationFixture(file(installDir))
    }

    def ExecutableFixture executable(Object path) {
        return toolChain.executable(file(path))
    }

    def TestFile objectFile(Object path) {
        if (toolChain.visualCpp) {
            return file("${path}.obj")
        }
        return file("${path}.o")
    }

    def SharedLibraryFixture sharedLibrary(Object path) {
        return toolChain.sharedLibrary(file(path))
    }

    def NativeBinaryFixture staticLibrary(Object path) {
        return toolChain.staticLibrary(file(path))
    }
}
