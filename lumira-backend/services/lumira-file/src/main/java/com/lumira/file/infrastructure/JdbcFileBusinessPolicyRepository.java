package com.lumira.file.infrastructure;

import com.lumira.api.dictionary.DictionaryItemLookupPort;
import com.lumira.file.repository.FileBusinessPolicyRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFileBusinessPolicyRepository implements FileBusinessPolicyRepository {
    private final DictionaryItemLookupPort dictionaryItemLookupPort;

    public JdbcFileBusinessPolicyRepository(DictionaryItemLookupPort dictionaryItemLookupPort) {
        this.dictionaryItemLookupPort = dictionaryItemLookupPort;
    }

    @Override
    public List<Item> findEnabledItems(String dictionaryCode) {
        return dictionaryItemLookupPort.enabledItems(dictionaryCode).stream()
                .map(item -> new Item(item.label(), item.value(), item.remark(), item.sortNo()))
                .toList();
    }
}
