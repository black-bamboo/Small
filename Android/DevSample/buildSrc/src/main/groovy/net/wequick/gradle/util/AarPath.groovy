/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.wequick.gradle.util

public class AarPath {

    private static final String CACHE_DIR = "build-cache"
    private static final String CACHE_INPUTS_FILE = "inputs"
    private static final String CACHE_FILE_PATH_KEY = "FILE_PATH"
    private static final int CACHE_FILE_PATH_INDEX = CACHE_FILE_PATH_KEY.length() + 1

    private static final String _ = File.separator
    private static final String MAVEN2_CACHE_PATH = 'extras' + _ + 'android' + _ + 'm2repository'
    private static final String GRADLE_CACHE_PATH = '.gradle'+ _ + 'caches'

    private File mInputFile
    private File mOutputDir

    public static class Module {
        String group
        String name
        String version
        String path
        String fileName

        public String getPath() {
            if (path == null) {
                path = "$group/$name/$version"
            }
            return path
        }

        public String getFileName() {
            if (fileName == null) {
                fileName = "$name-$version"
            }
            return fileName
        }
    }

    private Module mModule

    public AarPath(File path) {
        mOutputDir = path
        mInputFile = parseInputFile(path)
    }
    
    private static File parseInputFile(File outputDir) {
        // Find the build cache root which should be something as
        // `~/.android/build-cache` on Android Plugin 2.3.0+
        File cacheDir = outputDir
        while (cacheDir.parentFile != null && cacheDir.parentFile.name != CACHE_DIR) {
            cacheDir = cacheDir.parentFile
        }

        if (cacheDir.parentFile == null) {
            // Isn't using `buildCache`, just take the output as input
            return outputDir
        }

        File input = new File(cacheDir, CACHE_INPUTS_FILE)
        if (!input.exists()) {
            return null
        }

        String inputPath = null
        input.eachLine {
            if (inputPath == null && it.startsWith(CACHE_FILE_PATH_KEY)) {
                inputPath = it.substring(CACHE_FILE_PATH_INDEX)
            }
        }
        if (inputPath == null) return null

        return new File(inputPath)
    }

    private static Module parseInputModule(File inputFile) {
        Module module = new Module()
        if (inputFile == null) {
            return module
        }

        File temp
        File versionFile = inputFile
        String inputPath = inputFile.absolutePath
        String parentName = inputFile.parentFile.name
        if (parentName == 'jars') {
            // **/appcompat-v7/23.2.1/jars/classes.jar
            // => appcompat-v7-23.2.1.jar
            // TODO: handle this
        } else if (parentName == 'libs') {
            temp = inputFile.parentFile.parentFile
            module.version = 'unspecified'
            module.name = temp.name
            module.group = temp.parentFile.name

            def name = inputFile.name
            name = name.substring(0, name.lastIndexOf('.'))
            module.fileName = "$module.name-$name"
        } else if (parentName == 'default') {
            // Compat for android plugin 2.3.0
            // Sample/jni_plugin/intermediates/bundles/default/classes.jar
            temp = inputFile.parentFile.parentFile.parentFile.parentFile.parentFile
            module.version = 'unspecified'
            module.name = temp.name
            module.group = temp.parentFile.name

            module.fileName = "$module.name-default"
        } else {
            if (inputPath.contains('exploded-aar')) {
                // [BUILD_DIR]/intermediates/exploded-aar/com.android.support/support-v4/25.1.0
                //                                        ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^ ^^^^^^
                temp = versionFile
                module.version = temp.name; temp = temp.parentFile
                module.name = temp.name; temp = temp.parentFile
                module.group = temp.name
            } else if (inputPath.contains(MAVEN2_CACHE_PATH)) {
                // [SDK_HOME]/extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
                //                                        ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^ ^^^^^^
                temp = inputFile.parentFile
                module.version = temp.name; temp = temp.parentFile
                module.name = temp.name; temp = temp.parentFile
                module.group = temp.name; temp = temp.parentFile
                module.group = temp.name + '.' + module.group; temp = temp.parentFile
                module.group = temp.name + '.' + module.group
            } else if (inputPath.contains(GRADLE_CACHE_PATH)) {
                // ~/.gradle/caches/modules-2/files-2.1/net.wequick.small/small/1.1.0/hash/*.aar
                //                                      ^^^^^^^^^^^^^^^^^ ^^^^^ ^^^^^
                temp = inputFile.parentFile.parentFile
                module.version = temp.name; temp = temp.parentFile
                module.name = temp.name; temp = temp.parentFile
                module.group = temp.name

                def hash = inputFile.parentFile.name
                module.fileName = "$module.name-$module.version-$hash"
            }
        }

        if (module.group == null) {
            throw new RuntimeException("Failed to parse aar module from $inputFile")
        }

        if (module.version == null) {
            module.version = versionFile.name
        }
        module.name = versionFile.parentFile.name
        return module
    }

    public boolean explodedFromAar(Map aar) {
        if (mInputFile == null) return false

        String inputPath = mInputFile.absolutePath

        // ~/.gradle/caches/modules-2/files-2.1/net.wequick.small/small/1.1.0/hash/*.aar
        //                                      ^^^^^^^^^^^^^^^^^ ^^^^^
        def moduleAarDir = "$aar.group$File.separator$aar.name"
        if (inputPath.contains(moduleAarDir)) {
            return true
        }

        // [SDK_HOME]/extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
        //                                        ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^
        def sep = File.separator
        if (sep == '\\') {
            sep = '\\\\' // compat for windows
        }
        def repoGroup = aar.group.replaceAll('\\.', sep)
        def repoAarPath = "$repoGroup$File.separator$aar.name"
        return inputPath.contains(repoAarPath)
    }

    public File getInputFile() {
        return mInputFile
    }

    public File getOutputDir() {
        return  mOutputDir
    }

    public Module getModule() {
        if (mModule == null) {
            mModule = parseInputModule(mInputFile)
        }
        return mModule
    }
}