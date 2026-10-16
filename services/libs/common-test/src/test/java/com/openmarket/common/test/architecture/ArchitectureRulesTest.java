package com.openmarket.common.test.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 규칙마다 규약을 지키는 픽스처(archgood)에서는 통과하고, 위반 픽스처(archbad)에서는 실패하는지 확인한다. 규칙을 추가하면 archbad에 그 규칙의 위반 예시도
 * 추가해야 이 테스트가 통과한다.
 */
class ArchitectureRulesTest {

    private static final JavaClasses GOOD =
            new ClassFileImporter().importPackages("com.openmarket.archgood");
    private static final JavaClasses BAD =
            new ClassFileImporter().importPackages("com.openmarket.archbad");

    static Stream<Arguments> rules() {
        return Arrays.stream(ArchitectureRules.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ArchTest.class))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(field -> Arguments.of(field.getName(), read(field)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("규약을 지키는 코드는 통과한다")
    void passesOnConformingCode(String name, ArchRule rule) {
        assertThatCode(() -> rule.check(GOOD)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("규약을 어긴 코드는 실패한다")
    void failsOnViolation(String name, ArchRule rule) {
        assertThatThrownBy(() -> rule.check(BAD)).isInstanceOf(AssertionError.class);
    }

    private static ArchRule read(Field field) {
        try {
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
