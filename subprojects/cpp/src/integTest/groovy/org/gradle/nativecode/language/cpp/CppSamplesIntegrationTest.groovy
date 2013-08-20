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
import org.gradle.integtests.fixtures.Sample
import org.gradle.nativecode.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class CppSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule public final Sample c = new Sample(temporaryFolder, 'cpp/c')
    @Rule public final Sample asm = new Sample(temporaryFolder, 'cpp/c-with-assembler')
    @Rule public final Sample cpp = new Sample(temporaryFolder, 'cpp/cpp')
    @Rule public final Sample cppExe = new Sample(temporaryFolder, 'cpp/cpp-exe')
    @Rule public final Sample cppLib = new Sample(temporaryFolder, 'cpp/cpp-lib')
    @Rule public final Sample multiProject = new Sample(temporaryFolder, 'cpp/multi-project')
    @Rule public final Sample variants = new Sample(temporaryFolder, 'cpp/variants')
    @Rule public final Sample dependencies = new Sample(temporaryFolder, 'cpp/dependencies')

    def "asm"() {
        given:
        sample asm

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":assembleMainExecutableMainAsm", ":compileMainExecutableMainC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("cpp/c-with-assembler/build/install/mainExecutable").exec().out == "5 + 7 = 12\n"
    }

    def "c"() {
        given:
        sample c
        
        when:
        run "installMainExecutable"
        
        then:
        executedAndNotSkipped ":compileHelloSharedLibraryLibC", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableExeC", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("cpp/c/build/install/mainExecutable").exec().out == "Hello world!"
    }

    def "cpp"() {
        given:
        sample cpp

        when:
        run "installMainExecutable"

        then:
        executedAndNotSkipped ":compileHelloSharedLibraryLibCpp", ":linkHelloSharedLibrary", ":helloSharedLibrary",
                              ":compileMainExecutableExeCpp", ":linkMainExecutable", ":mainExecutable"

        and:
        installation("cpp/cpp/build/install/mainExecutable").exec().out == "Hello world!\n"
    }

    def "exe"() {
        given:
        sample cppExe

        when:
        run "installMain"

        then:
        executedAndNotSkipped ":compileMainExecutableMainCpp", ":linkMainExecutable", ":stripMainExecutable", ":mainExecutable"

        and:
        executable("cpp/cpp-exe/build/binaries/mainExecutable/sampleExe").exec().out == "Hello, World!\n"
        installation("cpp/cpp-exe/build/install/mainExecutable").exec().out == "Hello, World!\n"
    }

    def "lib"() {
        given:
        sample cppLib
        
        when:
        run "mainSharedLibrary"
        
        then:
        executedAndNotSkipped ":compileMainSharedLibraryMainCpp", ":linkMainSharedLibrary", ":mainSharedLibrary"
        
        and:
        sharedLibrary("cpp/cpp-lib/build/binaries/mainSharedLibrary/sampleLib").assertExists()
        
        when:
        sample cppLib
        run "mainStaticLibrary"
        
        then:
        executedAndNotSkipped ":compileMainStaticLibraryMainCpp", ":createMainStaticLibrary", ":mainStaticLibrary"
        
        and:
        staticLibrary("cpp/cpp-lib/build/binaries/mainStaticLibrary/sampleLib").assertExists()
    }

    def "variants"() {
        when:
        sample variants
        run "installEnglishMainExecutable"

        then:
        executedAndNotSkipped ":compileEnglishHelloSharedLibraryLibCpp", ":linkEnglishHelloSharedLibrary", ":englishHelloSharedLibrary"
        executedAndNotSkipped ":compileEnglishMainExecutableExeCpp", ":linkEnglishMainExecutable", ":englishMainExecutable"

        and:
        executable("cpp/variants/build/binaries/mainExecutable/english/main").assertExists()
        sharedLibrary("cpp/variants/build/binaries/helloSharedLibrary/english/hello").assertExists()

        and:
        installation("cpp/variants/build/install/mainExecutable/english").exec().out == "Hello world!\n"

        when:
        sample variants
        run "installFrenchMainExecutable"

        then:
        executedAndNotSkipped ":compileFrenchHelloSharedLibraryLibCpp", ":linkFrenchHelloSharedLibrary", ":frenchHelloSharedLibrary"
        executedAndNotSkipped ":compileFrenchMainExecutableExeCpp", ":linkFrenchMainExecutable", ":frenchMainExecutable"

        and:
        executable("cpp/variants/build/binaries/mainExecutable/french/main").assertExists()
        sharedLibrary("cpp/variants/build/binaries/helloSharedLibrary/french/hello").assertExists()

        and:
        installation("cpp/variants/build/install/mainExecutable/french").exec().out == "Bonjour monde!\n"
    }

    def multiProject() {
        given:
        sample multiProject

        when:
        run "installMainExecutable"

        then:
        ":exe:mainExecutable" in executedTasks

        and:
        sharedLibrary("cpp/multi-project/lib/build/binaries/mainSharedLibrary/lib").assertExists()
        executable("cpp/multi-project/exe/build/binaries/mainExecutable/exe").assertExists()
        installation("cpp/multi-project/exe/build/install/mainExecutable").exec().out == "Hello, World!\n"
    }

    // Does not work on windows, due to GRADLE-2118
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "dependencies"() {
        when:
        sample dependencies
        run ":lib:uploadArchives"

        then:
        sharedLibrary("cpp/dependencies/lib/build/binaries/mainSharedLibrary/lib").assertExists()
        file("cpp/dependencies/lib/build/repo/some-org/some-lib/1.0/some-lib-1.0-so.so").isFile()

        when:
        sample dependencies
        run ":exe:uploadArchives"

        then:
        ":exe:mainCppExtractHeaders" in nonSkippedTasks
        ":exe:mainExecutable" in nonSkippedTasks

        and:
        executable("cpp/dependencies/exe/build/binaries/mainExecutable/exe").assertExists()
        file("cpp/dependencies/exe/build/repo/dependencies/exe/1.0/exe-1.0.exe").exists()
    }

}