package com.lumira.saas.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.dictionary.DictionaryItemLookupPort;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemDictionaryItemLookupAdapterTest {

    @Test
    void enabledItemsMapsSystemOwnedDictionaryMetadataToTheSharedContract() {
        DictRuntimeService dictRuntimeService = mock(DictRuntimeService.class);
        SystemVO.DictItemVO source = new SystemVO.DictItemVO();
        source.setItemLabel("Public image");
        source.setItemValue("PUBLIC_IMAGE");
        source.setRemark("Visible after scan");
        source.setSortNo(null);
        when(dictRuntimeService.listEnabledItems("file_visibility")).thenReturn(List.of(source));

        List<DictionaryItemLookupPort.DictionaryItem> items = new SystemDictionaryItemLookupAdapter(dictRuntimeService)
                .enabledItems("file_visibility");

        assertThat(items).containsExactly(new DictionaryItemLookupPort.DictionaryItem(
                "Public image",
                "PUBLIC_IMAGE",
                "Visible after scan",
                0
        ));
        verify(dictRuntimeService).listEnabledItems("file_visibility");
    }
}
