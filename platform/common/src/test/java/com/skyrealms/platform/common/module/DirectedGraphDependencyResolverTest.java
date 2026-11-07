package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.ModuleMetadata;
import com.skyrealms.platform.api.module.SkyModule;
import com.skyrealms.platform.testing.DummyTestModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectedGraphDependencyResolverTest {

    private DependencyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DirectedGraphDependencyResolver();
    }

    @Test
    @DisplayName("Should resolve independent modules in any valid order")
    void shouldResolveIndependentModules() {
        SkyModule m1 = DummyTestModule.of("module-a");
        SkyModule m2 = DummyTestModule.of("module-b");

        List<SkyModule> order = resolver.resolve(List.of(m1, m2));

        assertThat(order).containsExactlyInAnyOrder(m1, m2);
    }

    @Test
    @DisplayName("Should resolve linear dependency chain: C -> B -> A")
    void shouldResolveLinearChain() {
        // A has no dependencies
        SkyModule a = DummyTestModule.of("module-a");

        // B depends on A
        SkyModule b = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-b"))
                .dependsOn(ModuleId.of("module-a"))
                .build());

        // C depends on B
        SkyModule c = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-c"))
                .dependsOn(ModuleId.of("module-b"))
                .build());

        List<SkyModule> order = resolver.resolve(List.of(c, a, b));

        // A must enable first, then B, then C
        assertThat(order).containsExactly(a, b, c);
    }

    @Test
    @DisplayName("Should resolve diamond dependency graph: D depends on B,C; B,C depend on A")
    void shouldResolveDiamondDependency() {
        SkyModule a = DummyTestModule.of("module-a");

        SkyModule b = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-b"))
                .dependsOn(ModuleId.of("module-a"))
                .build());

        SkyModule c = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-c"))
                .dependsOn(ModuleId.of("module-a"))
                .build());

        SkyModule d = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-d"))
                .dependsOn(ModuleId.of("module-b"))
                .dependsOn(ModuleId.of("module-c"))
                .build());

        List<SkyModule> order = resolver.resolve(List.of(d, c, b, a));

        assertThat(order.indexOf(a)).isLessThan(order.indexOf(b));
        assertThat(order.indexOf(a)).isLessThan(order.indexOf(c));
        assertThat(order.indexOf(b)).isLessThan(order.indexOf(d));
        assertThat(order.indexOf(c)).isLessThan(order.indexOf(d));
    }

    @Test
    @DisplayName("Should throw MissingDependencyException when required dependency is absent")
    void shouldThrowMissingDependencyException() {
        SkyModule b = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-b"))
                .dependsOn(ModuleId.of("module-a"))
                .build());

        assertThatThrownBy(() -> resolver.resolve(List.of(b)))
                .isInstanceOf(MissingDependencyException.class)
                .hasMessageContaining("module-a");
    }

    @Test
    @DisplayName("Should throw CyclicDependencyException on circular dependency A -> B -> A")
    void shouldDetectCircularDependency() {
        SkyModule a = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-a"))
                .dependsOn(ModuleId.of("module-b"))
                .build());

        SkyModule b = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-b"))
                .dependsOn(ModuleId.of("module-a"))
                .build());

        assertThatThrownBy(() -> resolver.resolve(List.of(a, b)))
                .isInstanceOf(CyclicDependencyException.class)
                .hasMessageContaining("Cyclic dependency detected");
    }

    @Test
    @DisplayName("Should gracefully skip absent optional dependency")
    void shouldSkipAbsentOptionalDependency() {
        SkyModule a = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-a"))
                .optionallyDependsOn(ModuleId.of("absent-opt"))
                .build());

        List<SkyModule> order = resolver.resolve(List.of(a));
        assertThat(order).containsExactly(a);
    }

    @Test
    @DisplayName("Should respect present optional dependency ordering")
    void shouldRespectPresentOptionalDependency() {
        SkyModule opt = DummyTestModule.of("module-opt");
        SkyModule a = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("module-a"))
                .optionallyDependsOn(ModuleId.of("module-opt"))
                .build());

        List<SkyModule> order = resolver.resolve(List.of(a, opt));
        assertThat(order).containsExactly(opt, a);
    }
}
