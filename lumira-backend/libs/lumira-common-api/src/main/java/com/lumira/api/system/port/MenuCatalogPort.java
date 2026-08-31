package com.lumira.api.system.port;

import com.lumira.api.system.MenuNodeDTO;
import java.util.List;

public interface MenuCatalogPort {
    List<MenuNodeDTO> builtinMenus();
}
