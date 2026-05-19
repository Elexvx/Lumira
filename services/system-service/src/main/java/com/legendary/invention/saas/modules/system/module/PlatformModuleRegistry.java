package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;

import java.util.List;
import java.util.Optional;

public interface PlatformModuleRegistry {

    List<PlatformModuleVO> listModules();

    Optional<PlatformModuleVO> findModule(String moduleCode);
}
