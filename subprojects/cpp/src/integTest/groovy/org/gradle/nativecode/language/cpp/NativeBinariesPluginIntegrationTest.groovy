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
package org.gradle.nativecode.language.cpp
import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec
import org.gradle.nativecode.language.cpp.fixtures.app.CppCallingCHelloWorldApp

class NativeBinariesPluginIntegrationTest extends AbstractBinariesIntegrationSpec {
    def helloWorldApp = new CppCallingCHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "skips building executable binary with no source"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
        """

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").assertDoesNotExist()
    }

    def "assemble executable from component with multiple language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {
                    source sources.main.cpp
                    source sources.main.c
                }
            }
        """

        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "assemble executable binary directly from language source sets"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {}
            }
            binaries.all {
                source sources.main.cpp
                source sources.main.c
            }
        """

        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "assemble executable binary directly from functional source set"() {
        given:
        useMixedSources()

        when:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {}
            }
            executables {
                main {}
            }
            binaries.all {
                source sources.main
            }
        """
        
        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    def "ignores java sources added to binary"() {
        given:
        useMixedSources()
        file("src/main/java/HelloWorld.java") << """
    This would not compile
"""

        when:
        buildFile << """
            apply plugin: "cpp"
            apply plugin: "java"
            sources {
                main {}
            }
            executables {
                main {
                    source sources.main.cpp
                    source sources.main.c
                    source sources.main.java
                }
            }
         """

        then:
        succeeds "mainExecutable"

        and:
        executable("build/binaries/mainExecutable/main").exec().out == helloWorldApp.englishOutput
    }

    private def useMixedSources() {
        helloWorldApp.writeSources(file("src/main"))
    }

    def "build fails when link executable fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """

        and:
        file("src", "main", "cpp", "helloworld.cpp") << """
            int thing() { return 0; }
        """

        expect:
        fails "mainExecutable"
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.");
        failure.assertHasCause("Link failed; see the error output for details.")
    }

    def "build fails when link library fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-lib"
            binaries.all {
                linkerArgs "--not-an-option"
            }
        """

        and:
        file("src/main/cpp/hello.cpp") << """
            #include "test.h"
            void hello() {
                test();
            }
"""
        // Header file available, but no implementation to link
        file("src/main/cpp/test.h") << """
            int test();
"""

        when:
        fails "mainSharedLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainSharedLibrary'.");
        failure.assertHasCause("Link failed; see the error output for details.")
    }

    def "build fails when create static library fails"() {
        given:
        buildFile << """
            apply plugin: "cpp-lib"
            binaries.withType(StaticLibraryBinary) {
                staticLibArgs "not_a_file"
            }
        """

        and:
        file("src/main/cpp/hello.cpp") << """
            void hello() {
            }
"""

        when:
        fails "mainStaticLibrary"

        then:
        failure.assertHasDescription("Execution failed for task ':createMainStaticLibrary'.");
        failure.assertHasCause("Create static library failed; see the error output for details.")
    }
}
