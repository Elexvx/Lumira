package com.lumira.saas.modules.system.assembly;

import com.lumira.api.system.PlatformSettingDefaultsPort;
import com.lumira.saas.modules.system.integration.PlatformSettingDefaultsPortAdapter;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformSettingDefaultsPortAdapterTest {

    @Test
    void exposesOnlyEnabledDefaultsThroughSystemOwnedRepository() {
        SystemPlatformSettingsRepository repository = mock(SystemPlatformSettingsRepository.class);
        Map<String, String> defaults = Map.of("certificate.public.organizer", "Lumira");
        when(repository.findSettingDefaults("CERTIFICATE")).thenReturn(defaults);

        PlatformSettingDefaultsPort port = new PlatformSettingDefaultsPortAdapter(repository);

        assertThat(port.findEnabledDefaults("CERTIFICATE")).isEqualTo(defaults);
        verify(repository).findSettingDefaults("CERTIFICATE");
    }

    @Test
    void ignoresBlankGroupsWithoutReadingSystemPersistence() {
        SystemPlatformSettingsRepository repository = mock(SystemPlatformSettingsRepository.class);

        assertThat(new PlatformSettingDefaultsPortAdapter(repository).findEnabledDefaults(" ")).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void platformAssemblyRegistersTheOwnerAdapter() {
        Import imports = SystemPlatformControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imports).isNotNull();
        assertThat(Arrays.asList(imports.value())).contains(PlatformSettingDefaultsPortAdapter.class);
    }
}
