package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.ModuleMetadata;
import com.skyrealms.platform.api.module.ModuleRegistry;
import com.skyrealms.platform.api.module.SkyModule;
import com.skyrealms.platform.api.service.ServiceRegistry;
import com.skyrealms.platform.common.event.DefaultEventBus;
import com.skyrealms.platform.common.service.ConcurrentServiceRegistry;
import com.skyrealms.platform.testing.DummyTestModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultModuleLifecycleManagerTest {

    private ModuleRegistry registry;
    private DependencyResolver resolver;
    private ServiceRegistry services;
    private DefaultEventBus eventBus;
    private Path tempDir;
    private DefaultModuleLifecycleManager manager;

    @BeforeEach
    void setUp() throws IOException {
        registry = new DefaultModuleRegistry();
        resolver = new DirectedGraphDependencyResolver();
        services = new ConcurrentServiceRegistry();
        eventBus = new DefaultEventBus();
        tempDir = Files.createTempDirectory("skyrealms-lifecycle-test");
        manager = new DefaultModuleLifecycleManager(
                registry,
                resolver,
                services,
                eventBus,
                tempDir,
                LoggerFactory.getLogger("TestLifecycle")
        );
    }

    @AfterEach
    void tearDown() {
        eventBus.close();
    }

    @Test
    @DisplayName("Should enable modules in topological dependency order and disable in reverse")
    void shouldEnableInOrderAndDisableInReverse() {
        DummyTestModule core = DummyTestModule.of("core");
        DummyTestModule player = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("player"))
                .dependsOn(ModuleId.of("core"))
                .build());
        DummyTestModule islands = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("islands"))
                .dependsOn(ModuleId.of("player"))
                .build());

        registry.register(islands);
        registry.register(core);
        registry.register(player);

        manager.enableAll();

        assertThat(manager.enabledModules()).containsExactly(core, player, islands);
        assertThat(core.enableCount()).isEqualTo(1);
        assertThat(player.enableCount()).isEqualTo(1);
        assertThat(islands.enableCount()).isEqualTo(1);

        manager.disableAll();

        assertThat(core.disableCount()).isEqualTo(1);
        assertThat(player.disableCount()).isEqualTo(1);
        assertThat(islands.disableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should isolate failure: fail dependent module but allow independent module to enable")
    void shouldIsolateModuleFailure() {
        DummyTestModule core = DummyTestModule.of("core");
        DummyTestModule database = DummyTestModule.of("database");
        database.setFailOnEnable(true); // Fails during onEnable

        // Player depends on database, so Player should be prevented from enabling
        DummyTestModule player = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("player"))
                .dependsOn(ModuleId.of("database"))
                .build());

        // Chat depends only on core, so Chat should enable fine
        DummyTestModule chat = new DummyTestModule(ModuleMetadata.builder(ModuleId.of("chat"))
                .dependsOn(ModuleId.of("core"))
                .build());

        registry.register(core);
        registry.register(database);
        registry.register(player);
        registry.register(chat);

        manager.enableAll();

        assertThat(manager.enabledModules()).contains(core, chat);
        assertThat(manager.failedModules()).contains(database, player);
        assertThat(core.enableCount()).isEqualTo(1);
        assertThat(chat.enableCount()).isEqualTo(1);
        assertThat(player.enableCount()).isEqualTo(0);
    }
}
