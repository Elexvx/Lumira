package com.legendary.invention.saas.modules.system.module;

import com.legendary.invention.saas.modules.system.module.vo.PlatformModuleVO;

import java.util.List;
import java.util.Optional;

public class StaticPlatformModuleRegistry implements PlatformModuleRegistry {

    @Override
    public List<PlatformModuleVO> listModules() {
        return PlatformModuleCatalog.listModules();
    }

    @Override
    public Optional<PlatformModuleVO> findModule(String moduleCode) {
        return PlatformModuleCatalog.findModule(moduleCode);
    }
}
