/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativecode.language.cpp

import org.gradle.nativecode.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.nativecode.language.cpp.fixtures.app.HelloWorldApp

class CLanguageIntegrationTest extends AbstractLanguageIntegrationTest {

    HelloWorldApp helloWorldApp = new CHelloWorldApp()

    def "build fails when compilation fails"() {
        given:
        buildFile << """
             apply plugin: "cpp"
             sources {
                 main {}
             }
             executables {
                 main {
                     source sources.main
                 }
             }
         """

        and:
        file("src/main/c/broken.c") << """
        #include <stdio.h>

        'broken
"""

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':compileMainExecutableMainC'.");
        failure.assertHasCause("C compiler failed; see the error output for details.")
    }
}

