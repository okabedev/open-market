import java.io.File
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * convention 플러그인을 실제 픽스처 프로젝트에 적용해 빌드한다.
 * Java 25에서 QueryDSL Q-타입 생성(R2), 포매터와 NullAway 동작(R3)을 회귀 테스트로 남긴다.
 */
class ConventionPluginsTest {

    @TempDir
    lateinit var dir: Path

    private fun fixture(): File {
        val root = dir.toFile()
        File(requireNotNull(javaClass.getResource("/fixture")).toURI()).copyRecursively(root)
        val buildLogicDir = System.getProperty("buildLogicDir")
        val catalogPath = System.getProperty("catalogPath")
        root.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { includeBuild("$buildLogicDir") }
            dependencyResolutionManagement {
                repositories { mavenCentral() }
                versionCatalogs { create("libs") { from(files("$catalogPath")) } }
            }
            rootProject.name = "fixture"
            """.trimIndent(),
        )
        return root
    }

    private fun gradle(root: File, vararg tasks: String): GradleRunner =
        GradleRunner.create().withProjectDir(root).withArguments(*tasks, "--stacktrace").forwardOutput()

    private fun writeSource(root: File, name: String, content: String) {
        root.resolve("src/main/java/com/openmarket/fixture/$name").writeText(content)
    }

    @Test
    fun `Java 25 문법을 포맷하고 컴파일하며 Q-타입을 생성한다`() {
        val root = fixture()

        val result = gradle(root, "spotlessApply", "build").build()

        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        // 같은 빌드에서 spotlessApply가 먼저 돌면 spotlessCheck는 그 결과를 재사용해 UP_TO_DATE가 된다.
        assertThat(result.task(":spotlessCheck")?.outcome).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(root.resolve("build/generated/sources/annotationProcessor/java/main/com/openmarket/fixture/QSampleEntity.java"))
            .exists()
    }

    @Test
    fun `NullAway가 null 반환을 컴파일 오류로 막는다`() {
        val root = fixture()
        writeSource(
            root,
            "NullViolation.java",
            """
            package com.openmarket.fixture;

            public class NullViolation {
                public String name() {
                    return null;
                }
            }
            """.trimIndent() + "\n",
        )

        val result = gradle(root, "compileJava").buildAndFail()

        assertThat(result.output).contains("[NullAway]")
    }

    @Test
    fun `포맷이 맞지 않으면 spotlessCheck가 실패한다`() {
        val root = fixture()
        writeSource(root, "Ugly.java", "package com.openmarket.fixture; public class Ugly{int  x ;}\n")

        val result = gradle(root, "spotlessCheck").buildAndFail()

        assertThat(result.task(":spotlessJavaCheck")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }
}
