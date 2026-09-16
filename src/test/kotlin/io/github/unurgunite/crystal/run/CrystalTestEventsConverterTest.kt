package io.github.unurgunite.crystal.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrystalTestEventsConverterTest {
    @Test
    fun testIsDuplicatedName_simple() {
        assertTrue(CrystalTestEventsConverter.isDuplicatedName("adds  adds"))
    }

    @Test
    fun testIsDuplicatedName_multiWord() {
        assertTrue(CrystalTestEventsConverter.isDuplicatedName("adds correctly  adds correctly"))
    }

    @Test
    fun testIsDuplicatedName_extraSpaces() {
        assertTrue(CrystalTestEventsConverter.isDuplicatedName("test name here   test name here"))
    }

    @Test
    fun testIsDuplicatedName_suiteName() {
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("Math"))
    }

    @Test
    fun testIsDuplicatedName_suiteWithSpaces() {
        // Suite names like "#kurz" are NOT duplicated
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("#kurz"))
    }

    @Test
    fun testIsDuplicatedName_shortString() {
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("ab"))
    }

    @Test
    fun testIsDuplicatedName_singleWord() {
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("Apfel"))
    }

    @Test
    fun testIndexer_simpleSpecFile() {
        val specContent =
            """
            require "spec"

            describe "Calculator" do
              it "adds numbers" do
                expect(1 + 1).to eq(2)
              end

              it "subtracts numbers" do
                expect(5 - 3).to eq(2)
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            assertNotNull(locations["Calculator adds numbers"])
            assertNotNull(locations["Calculator subtracts numbers"])
            assertEquals(tempFile.absolutePath, locations["Calculator adds numbers"]?.first()?.file)
            assertEquals(4, locations["Calculator adds numbers"]?.first()?.line) // 1-based
            assertEquals(8, locations["Calculator subtracts numbers"]?.first()?.line)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_nestedContextBlocks() {
        val specContent =
            """
            require "spec"

            describe "User" do
              context "when admin" do
                it "can delete" do
                  expect(true).to be_true
                end
              end

              context "when guest" do
                it "cannot delete" do
                  expect(false).to be_false
                end
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            assertNotNull(locations["User when admin can delete"])
            assertNotNull(locations["User when guest cannot delete"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_singleQuotes() {
        val specContent =
            """
            require 'spec'

            describe 'Math' do
              it 'works' do
                expect(1).to eq(1)
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(1, locations.size)
            assertNotNull(locations["Math works"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_emptyFile() {
        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText("")
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(0, locations.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_nonExistentFile() {
        val indexer = CrystalSpecFileIndexer("/nonexistent/path/spec.cr")
        val locations = indexer.buildIndex()

        assertEquals(0, locations.size)
    }

    @Test
    fun testIndexer_directoryWithMultipleFiles() {
        val tempDir = Files.createTempDirectory("spec_dir").toFile()
        try {
            val spec1 = File(tempDir, "math_spec.cr")
            spec1.writeText(
                """
                describe "Math" do
                  it "adds" do
                    expect(1 + 1).to eq(2)
                  end
                end
                """.trimIndent(),
            )

            val spec2 = File(tempDir, "string_spec.cr")
            spec2.writeText(
                """
                describe "String" do
                  it "concatenates" do
                    expect("hello" + " world").to eq("hello world")
                  end
                end
                """.trimIndent(),
            )

            // Also create a non-spec file that should be ignored
            val helper = File(tempDir, "helper.cr")
            helper.writeText("require \"spec\"")

            CrystalSpecFileIndexer.clearCache()
            val locations = CrystalSpecFileIndexer.getTestLocationsForDirectory(tempDir.absolutePath)

            assertEquals(2, locations.size)
            assertNotNull(locations["Math adds"])
            assertNotNull(locations["String concatenates"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testIndexer_directoryWithSubdirectories() {
        val tempDir = Files.createTempDirectory("spec_dir").toFile()
        val subDir = File(tempDir, "models")
        subDir.mkdirs()
        try {
            val spec1 = File(tempDir, "app_spec.cr")
            spec1.writeText(
                """
                describe "App" do
                  it "works" do
                    expect(true).to be_true
                  end
                end
                """.trimIndent(),
            )

            val spec2 = File(subDir, "user_spec.cr")
            spec2.writeText(
                """
                describe "User" do
                  it "validates" do
                    expect(true).to be_true
                  end
                end
                """.trimIndent(),
            )

            CrystalSpecFileIndexer.clearCache()
            val locations = CrystalSpecFileIndexer.getTestLocationsForDirectory(tempDir.absolutePath)

            assertEquals(2, locations.size)
            assertNotNull(locations["App works"])
            assertNotNull(locations["User validates"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testIndexer_multipleTestsInSingleDescribe() {
        val specContent =
            """
            describe Asdf do
              it "works" do
                false.should eq(true)
              end

              it "works2" do
                true.should eq(true)
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            assertNotNull(locations["Asdf works"])
            assertNotNull(locations["Asdf works2"])
            assertEquals(2, locations["Asdf works"]?.first()?.line) // 1-based
            assertEquals(6, locations["Asdf works2"]?.first()?.line)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_multipleDescribeBlocks() {
        val specContent =
            """
            describe Asdf do
              it "works" do
                false.should eq(true)
              end

              it "sorks" do
                true.should eq(true)
              end
            end

            describe Cba do
              it "xorks" do
                false.should eq(true)
              end

              it "grks" do
                true.should eq(true)
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(4, locations.size)
            assertNotNull(locations["Asdf works"])
            assertNotNull(locations["Asdf sorks"])
            assertNotNull(locations["Cba xorks"])
            assertNotNull(locations["Cba grks"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_skipsComments() {
        val specContent =
            """
            describe "Real" do
              # describe "Fake" do
              #   it "fake test" do
              #   end
              # end
              it "works" do
                expect(true).to be_true
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(1, locations.size)
            assertNotNull(locations["Real works"])
            // Should NOT have "Real Fake fake test"
            assertNull(locations["Real Fake fake test"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_parenthesizedItBlock() {
        val specContent =
            """
            describe "Math" do
              it("adds numbers") do
                expect(1 + 1).to eq(2)
              end

              it 'works' do
                expect(true).to be_true
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            assertNotNull(locations["Math adds numbers"])
            assertNotNull(locations["Math works"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_duplicateTestNames() {
        val specContent =
            """
            describe "Foo" do
              it "works" do
                expect(true).to be_true
              end
            end

            describe "Bar" do
              it "works" do
                expect(false).to be_false
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            // Both "Foo works" and "Bar works" should exist
            assertNotNull(locations["Foo works"])
            assertNotNull(locations["Bar works"])
            assertEquals(2, locations["Foo works"]?.first()?.line)
            assertEquals(8, locations["Bar works"]?.first()?.line)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_cacheInvalidation() {
        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            // First version: only "works"
            tempFile.writeText(
                """
                describe "Test" do
                  it "works" do
                    expect(true).to be_true
                  end
                end
                """.trimIndent(),
            )

            CrystalSpecFileIndexer.clearCache()
            val locations1 = CrystalSpecFileIndexer.getTestLocations(tempFile.absolutePath)
            assertEquals(1, locations1.size)
            assertNotNull(locations1["Test works"])

            // Second version: add "works2"
            // Ensure filesystem timestamp differs (1-second resolution)
            Thread.sleep(1100)
            tempFile.writeText(
                """
                describe "Test" do
                  it "works" do
                    expect(true).to be_true
                  end
                  it "works2" do
                    expect(true).to be_true
                  end
                end
                """.trimIndent(),
            )

            val locations2 = CrystalSpecFileIndexer.getTestLocations(tempFile.absolutePath)
            assertEquals(2, locations2.size)
            assertNotNull(locations2["Test works"])
            assertNotNull(locations2["Test works2"])
        } finally {
            CrystalSpecFileIndexer.clearCache()
            tempFile.delete()
        }
    }

    @Test
    fun testLocator_urlParsing() {
        // Test the URL format that CrystalTestLocator expects
        val protocol = CrystalTestLocator.PROTOCOL
        val filePath = "/path/to/spec.cr"
        val line = 42
        val url = "$protocol://$filePath:$line"

        // Verify URL format
        assertEquals("crystal_spec:///path/to/spec.cr:42", url)

        // Verify parsing logic (same as CrystalTestLocator.getLocation)
        val path = url.removePrefix("$protocol://")
        val lastColon = path.lastIndexOf(':')
        val parsedFilePath = path.substring(0, lastColon)
        val parsedLine = path.substring(lastColon + 1).toIntOrNull() ?: 0

        assertEquals(filePath, parsedFilePath)
        assertEquals(line, parsedLine)
    }
}
