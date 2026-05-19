package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.dto.SystemDTO;
import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleValidationVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformModuleDefinitionValidatorTest {

    private final PlatformModuleDefinitionValidator validator = new PlatformModuleDefinitionValidator();

    @Test
    void shouldValidateHealthyDraftModule() {
        SystemDTO.ModuleValidationRequest request = request("conference", "SCENE", "PLANNED", List.of("system", "file", "message"));

        PlatformModuleValidationVO result = validator.validate(request, PlatformModuleCatalog.listModules());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void shouldReportDuplicateAndMissingDependency() {
        SystemDTO.ModuleValidationRequest request = request("journal", "SCENE", "PLANNED", List.of("missing-module"));

        PlatformModuleValidationVO result = validator.validate(request, PlatformModuleCatalog.listModules());

        assertThat(result.isValid()).isFalse();
        assertThat(result.isDuplicateModuleCode()).isTrue();
        assertThat(result.getMissingDependencies()).contains("missing-module");
        assertThat(result.getIssues()).anySatisfy(issue -> assertThat(issue).contains("模块编码已存在"));
    }

    @Test
    void shouldReportCycleDependency() {
        SystemDTO.ModuleValidationRequest request = request("system", "FOUNDATION", "ENABLED", List.of("journal"));
        request.setOverwriteExisting(true);

        PlatformModuleValidationVO result = validator.validate(request, PlatformModuleCatalog.listModules());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getCyclePath()).contains("system", "journal");
        assertThat(result.getIssues()).anySatisfy(issue -> assertThat(issue).contains("循环依赖"));
    }

    @Test
    void shouldWarnInactiveDependency() {
        SystemDTO.ModuleValidationRequest request = request("conference", "SCENE", "PLANNED", List.of("form"));

        PlatformModuleValidationVO result = validator.validate(request, PlatformModuleCatalog.listModules());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getInactiveDependencies()).contains("form");
        assertThat(result.getWarnings()).anySatisfy(warning -> assertThat(warning).contains("依赖模块当前未启用"));
    }

    private static SystemDTO.ModuleValidationRequest request(String moduleCode, String moduleType, String status, List<String> dependencies) {
        SystemDTO.ModuleValidationRequest request = new SystemDTO.ModuleValidationRequest();
        request.setModuleCode(moduleCode);
        request.setModuleName(moduleCode);
        request.setModuleType(moduleType);
        request.setLifecycleStatus(status);
        request.setSourceType("DATABASE");
        request.setOwnerService("system-service");
        request.setDependencies(dependencies);
        return request;
    }
}
