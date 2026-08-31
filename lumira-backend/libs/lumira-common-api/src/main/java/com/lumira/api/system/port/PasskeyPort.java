package com.lumira.api.system.port;

import com.lumira.api.system.PasskeyCredentialAssertionDTO;
import com.lumira.api.system.PasskeyCredentialDescriptorDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import java.util.List;

public interface PasskeyPort {
    PasskeySettingsDTO passkeySettings();
    PasskeyCredentialAssertionDTO passkeyCredentialAssertion(String credentialId);
    List<PasskeyCredentialDescriptorDTO> passkeyCredentialDescriptors(Long userId, String userUuid);
    List<PasskeyCredentialDTO> passkeyCredentials(Long userId, String userUuid);
    PasskeyCredentialDTO savePasskeyCredential(PasskeyCredentialSaveRequestDTO request);
    Boolean updatePasskeyCredentialUsage(PasskeyCredentialUsageRequestDTO request);
    PasskeyCredentialDTO renamePasskeyCredential(Long id, Long userId, String userUuid, String label);
    Boolean deletePasskeyCredential(Long id, Long userId, String userUuid);
}
