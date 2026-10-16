package com.openmarket.common.test.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 서비스 패키지 규약(docs/architecture/conventions.md)을 검사하는 ArchUnit 규칙.
 *
 * <p>서비스 테스트에서 다음처럼 쓴다.
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.openmarket.member", importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = ArchTests.in(ArchitectureRules.class);
 * }
 * }</pre>
 *
 * <p>아직 없는 패키지(예: 리스너가 없는 서비스의 {@code ui.event})가 있어도 실패하지 않게 모든 규칙은 빈 대상을 허용한다.
 */
public final class ArchitectureRules {

    private static final String DOMAIN = "com.openmarket.*.command.domain..";
    private static final String APPLICATION = "com.openmarket.*.command.application..";
    private static final String COMMAND = "com.openmarket.*.command..";
    private static final String QUERY = "com.openmarket.*.query..";
    private static final String QUERY_APPLICATION = "com.openmarket.*.query.application..";
    private static final String QUERY_DAO = "com.openmarket.*.query.dao..";
    private static final String QUERY_DTO = "com.openmarket.*.query.dto..";
    private static final String INFRA = "com.openmarket.*.infra..";
    private static final String UI = "com.openmarket.*.ui..";
    private static final String UI_WEB = "com.openmarket.*.ui.web..";
    private static final String UI_EVENT = "com.openmarket.*.ui.event..";

    /** QueryDSL이 엔티티 옆에 생성하는 Q-타입. 조회 DAO만 쓸 수 있다. */
    private static final DescribedPredicate<JavaClass> QUERYDSL_GENERATED =
            new DescribedPredicate<>("QueryDSL Q-타입") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getAllRawSuperclasses().stream()
                            .anyMatch(
                                    superclass ->
                                            superclass.getPackageName().startsWith("com.querydsl"));
                }
            };

    private ArchitectureRules() {}

    // --- 의존 방향 ---

    @ArchTest
    public static final ArchRule domainDependsOnlyOnAllowedPackages =
            classes()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .and(not(QUERYDSL_GENERATED))
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "java..",
                            "jakarta.persistence..",
                            "org.jspecify..",
                            "com.openmarket.common.core..",
                            DOMAIN)
                    .because("도메인은 Spring과 다른 계층을 모른다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule applicationDoesNotDependOnOuterLayers =
            noClasses()
                    .that()
                    .resideInAPackage(APPLICATION)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(INFRA, UI, QUERY)
                    .because("유스케이스는 포트로만 바깥과 통신하고, 커맨드와 쿼리는 모델을 공유하지 않는다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule commandsDoNotExposeDomainTypes =
            noClasses()
                    .that()
                    .resideInAPackage(APPLICATION)
                    .and()
                    .haveSimpleNameEndingWith("Command")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(DOMAIN)
                    .because("Command에는 도메인 타입을 쓰지 않는다. 변환은 유스케이스가 한다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule queryDoesNotDependOnCommandModel =
            noClasses()
                    .that()
                    .resideInAPackage(QUERY)
                    .should()
                    .dependOnClassesThat(resideInAPackage(COMMAND).and(not(QUERYDSL_GENERATED)))
                    .because("조회는 커맨드 모델을 쓰지 않는다. DAO만 Q-타입을 쓸 수 있다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule onlyDaoUsesQueryTypes =
            noClasses()
                    .that()
                    .resideInAnyPackage(QUERY_APPLICATION, QUERY_DTO)
                    .should()
                    .dependOnClassesThat(resideInAPackage(COMMAND))
                    .because("Q-타입은 조회 DAO 안에서만 쓴다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule queryDtoDependsOnlyOnAllowedPackages =
            classes()
                    .that()
                    .resideInAPackage(QUERY_DTO)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "java..",
                            "org.jspecify..",
                            "com.fasterxml.jackson.annotation..",
                            "io.swagger.v3.oas.annotations..",
                            QUERY_DTO)
                    .because("View는 원시 타입, String, java.time, 조회용 enum만 쓴다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule uiDoesNotDependOnDomainOrInfra =
            noClasses()
                    .that()
                    .resideInAPackage(UI)
                    .should()
                    .dependOnClassesThat(resideInAnyPackage(DOMAIN, INFRA))
                    .because("진입점은 유스케이스와 조회 서비스만 부른다")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule infraDoesNotDependOnUi =
            noClasses()
                    .that()
                    .resideInAPackage(INFRA)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(UI)
                    .allowEmptyShould(true);

    // --- 이름 ---

    @ArchTest
    public static final ArchRule applicationClassNames =
            classes()
                    .that()
                    .resideInAPackage(APPLICATION)
                    .and()
                    .areTopLevelClasses()
                    .should()
                    .haveSimpleNameEndingWith("UseCase")
                    .orShould()
                    .haveSimpleNameEndingWith("Command")
                    .orShould()
                    .haveSimpleNameEndingWith("Port")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule queryServiceNames =
            classes()
                    .that()
                    .resideInAPackage(QUERY_APPLICATION)
                    .and()
                    .areTopLevelClasses()
                    .should()
                    .haveSimpleNameEndingWith("QueryService")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule queryDaoNames =
            classes()
                    .that()
                    .resideInAPackage(QUERY_DAO)
                    .and()
                    .areTopLevelClasses()
                    .should()
                    .haveSimpleNameEndingWith("QueryDao")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule viewNames =
            classes()
                    .that()
                    .resideInAPackage(QUERY_DTO)
                    .and()
                    .areTopLevelClasses()
                    .and()
                    .areNotEnums()
                    .should()
                    .haveSimpleNameEndingWith("View")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule webClassNames =
            classes()
                    .that()
                    .resideInAPackage(UI_WEB)
                    .and()
                    .areTopLevelClasses()
                    .should()
                    .haveSimpleNameEndingWith("Controller")
                    .orShould()
                    .haveSimpleNameEndingWith("Request")
                    .orShould()
                    .haveSimpleNameEndingWith("Response")
                    .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule listenerNames =
            classes()
                    .that()
                    .resideInAPackage(UI_EVENT)
                    .and()
                    .areTopLevelClasses()
                    .should()
                    .haveSimpleNameEndingWith("Listener")
                    .allowEmptyShould(true);
}
