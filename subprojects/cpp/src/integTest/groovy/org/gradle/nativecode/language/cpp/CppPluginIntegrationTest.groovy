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

import org.gradle.nativecode.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativecode.language.cpp.fixtures.app.CppHelloWorldApp

class CppPluginIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    static final HELLO_WORLD = "Hello, World!"
    static final HELLO_WORLD_FRENCH = "Bonjour, Monde!"

    def "can explicitly configure tool chain in path"() {
        def helloWorld = new CppHelloWorldApp()

        given:
        buildFile << """
            apply plugin: "cpp-exe"

            toolChains {
                test(${toolChain.getImplementationClass()})
            }
"""
        settingsFile << "rootProject.name = 'test'"

        helloWorld.writeSources(file("src/main"))

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/test")
        executable.assertExists()
        executable.exec().out == helloWorld.englishOutput
    }

    def "build and execute program with non-conventional source layout"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {
                    cpp {
                        source {
                            srcDirs "src"
                            include "**/*.cpp"
                        }
                        exportedHeaders {
                            srcDirs "src/hello", "include"
                        }
                    }
                    c {
                        source {
                            srcDirs "src", "include"
                            include "**/*.c"
                        }
                        exportedHeaders {
                            srcDirs "src/hello", "include"
                        }
                    }
                }
            }
            executables {
                main {
                    source sources.main
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src", "hello", "hello.cpp") << """
            #include <iostream>

            void hello () {
              std::cout << "${HELLO_WORLD}";
            }
        """

        and:
        file("src", "hello", "french", "bonjour.c") << """
            #include <stdio.h>
            #include "otherProject/bonjour.h"

            void bonjour() {
                printf("${HELLO_WORLD_FRENCH}");
            }
        """

        and:
        file("src", "hello", "hello.h") << """
            void hello();
        """

        and:
        file("src", "app", "main", "main.cpp") << """
            #include "hello.h"
            extern "C" {
                #include "otherProject/bonjour.h"
            }

            int main () {
              hello();
              bonjour();
              return 0;
            }
        """

        and:
        file("include", "otherProject", "bonjour.h") << """
            void bonjour();
        """

        and:
        file("include", "otherProject", "bonjour.cpp") << """
            THIS FILE WON'T BE COMPILED
        """

        when:
        run "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == HELLO_WORLD + HELLO_WORLD_FRENCH
    }
}
