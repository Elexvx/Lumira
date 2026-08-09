package com.lumira.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.dictionary.DictionaryItemLookupPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcFileBusinessPolicyRepositoryTest {

    @Test
    void findsPolicyItemsThroughTheDictionaryOwnerContract() {
        DictionaryItemLookupPort dictionaryItemLookupPort = mock(DictionaryItemLookupPort.class);
        when(dictionaryItemLookupPort.enabledItems("file_visibility")).thenReturn(List.of(
                new DictionaryItemLookupPort.DictionaryItem("Public image", "PUBLIC_IMAGE", "Visible after scan", 10)
        ));

        List<com.lumira.file.repository.FileBusinessPolicyRepository.Item> items =
                new JdbcFileBusinessPolicyRepository(dictionaryItemLookupPort).findEnabledItems("file_visibility");

        assertThat(items).containsExactly(new com.lumira.file.repository.FileBusinessPolicyRepository.Item(
                "Public image",
                "PUBLIC_IMAGE",
                "Visible after scan",
                10
        ));
        verify(dictionaryItemLookupPort).enabledItems("file_visibility");
    }
}
