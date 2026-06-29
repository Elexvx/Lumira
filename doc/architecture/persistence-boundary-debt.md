# 鎸佷箙鍖栬竟鐣屽巻鍙插€哄姟

鏈枃浠惰褰曞綋鍓嶄粛淇濈暀鐨勭洿鎺?SQL 鍐欏簱鍊哄姟銆傚畠浠笉鏄柊瑙勮寖鐨勪緥澶栨ā鏉匡紝鍙槸涓轰簡閬垮厤鏈樁娈靛ぇ瑙勬ā鎵板姩 System銆両AM銆丄I 绛夊巻鍙叉ā鍧椼€傚悗缁不鐞嗗簲鎸夋ā鍧楁媶鍒嗭紝閫愭杩佺Щ鍒?Repository銆丮apper銆丏AO 鎴?Persistence Adapter銆?
Team 妯″潡涓嶅厑璁歌繘鍏ユ湰鍊哄姟娓呭崟銆傛湰闃舵宸茬粡瑕佹眰 `TeamAppService` 鍜?`TeamInviteService` 娓呴櫎鐩存帴鍐欏簱 SQL锛屽苟鐢?Team repository 灞傛壙鎺ユ寔涔呭寲銆?
## 褰撳墠鍊哄姟娓呭崟

| 妯″潡/绫?| 鍊哄姟 | 鏈樁娈靛鐞?|
| --- | --- | --- |
| `SystemManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 System repository/persistence adapter |
| `SystemUserManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 User repository/persistence adapter |
| `SystemRoleManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Role repository/persistence adapter |
| `SystemDepartmentAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Department repository/persistence adapter |
| `AccountActivationService` | direct SQL | historical debt; migrate to Account activation repository/persistence adapter |
| `IamUserService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 IAM repository/persistence adapter |
| `AiAssistantEmployeeResolver` | direct SQL | historical debt; migrate to AI assistant employee repository/persistence adapter |
| `DefaultDelegationGrantEvaluator` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 IAM delegation repository/persistence adapter |
| `AiToolPolicyService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI policy repository/persistence adapter |
| `AiConversationService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI conversation repository/persistence adapter |
| `AiToolOrchestrationService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI orchestration repository/persistence adapter |
| `AiKnowledgeBaseAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 knowledge-base repository/persistence adapter |
| `AiNativeToolRuntimeService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁敼涓鸿皟鐢ㄥ簲鐢ㄦ湇鍔°€両nternal API銆丏omain Event 鎴?Outbox |
| `AiEmployeeRuntimeService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI employee runtime repository/persistence adapter 鎴栨嫢鏈夋柟 Internal API |
| `AiIamQueryFacade` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI IAM query port 鎴?IAM Internal API |
| `AiLlmServiceConfigProvider` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI LLM config repository/persistence adapter |
| `AiManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI management repository/persistence adapter |
| `AiOwnerMetricsService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI metrics repository/persistence adapter |
| `AiPlatformQueryFacade` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Platform query port 鎴?Internal API |
| `AiReadQueryService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI read repository/persistence adapter |
| `AiSkillPermissionChecker` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI permission repository/persistence adapter 鎴栨巿鏉?Internal API |
| `AiToolRegistry` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 AI tool registry repository/persistence adapter |
| `ActivityManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Activity repository/persistence adapter |
| `OperationAuditService` | direct SQL | historical debt; migrate to Audit repository/persistence adapter |
| `CertificateAppService` | direct SQL | historical debt; migrate to Certificate repository/persistence adapter |
| `CompetitionManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Competition repository/persistence adapter |
| `CompetitionRegistrationAppService` | direct SQL | MVP registration/material/payment orchestration debt; migrate to Competition registration repository/persistence adapter |
| `ExpertApprovalEventConsumer` | direct SQL | historical debt; migrate to Expert approval repository/persistence adapter |
| `ExpertManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Expert repository/persistence adapter |
| `FileManagementAppService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 File repository/persistence adapter |
| `OnlineSessionManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Online Session repository/persistence adapter |
| `ProjectManagementAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Project repository/persistence adapter |
| `DictRuntimeService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Dict repository/persistence adapter |
| `SensitiveWordService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Sensitive Word repository/persistence adapter |
| `SensitiveWordDictionaryCache` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Sensitive Word repository/persistence adapter |
| `SensitiveWordPluginStateService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Sensitive Word plugin state repository/persistence adapter |
| `SystemPlatformSettingsAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Platform Settings repository/persistence adapter |
| `SystemProfileSettingsAppService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Profile Settings repository/persistence adapter |
| `TeamInternalApiService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Team repository/persistence adapter锛涗笉寰楁墿灞曞埌 `TeamAppService` 鎴?`TeamInviteService` |
| `TeamPermissionService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Team repository/persistence adapter锛涗笉寰楁墿灞曞埌 `TeamAppService` 鎴?`TeamInviteService` |
| `WorkflowAppService` | direct SQL | historical debt; migrate to Workflow repository/persistence adapter |
| `WorkflowSchemaBootstrap` | direct SQL | historical debt; migrate to Workflow schema repository/persistence adapter |
| `WorkOrderFeedbackService` | direct SQL | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Work Order repository/persistence adapter |
| `WorkOrderFeedbackPluginStateService` | direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Work Order plugin state repository/persistence adapter |
| `InternalSystemController` | direct SQL / direct persistence dependency | 璁板綍鍊哄姟锛屽悗缁縼绉诲埌 Internal API application service 鎴?repository/persistence adapter |

## 娌荤悊鍘熷垯

- 涓嶄负浜嗏€滅湅璧锋潵骞插噣鈥濅竴娆℃€ч噸鏋勬墍鏈夊巻鍙叉ā鍧椼€?- 鏂板涓氬姟鍐欏叆榛樿涓嶅緱鍔犲叆鏈竻鍗曘€?- 鏂板 AppService 绂佹鐩存帴缂栧啓 `insert`銆乣update`銆乣delete` SQL锛屼篃涓嶅緱鐩存帴璋冪敤 `jdbcTemplate.update`銆乣jdbcTemplate.batchUpdate` 鎴?`MyBatisQueryOperations.update`锛涘啓鍏ュ繀椤婚€氳繃 Repository銆丮apper銆丏AO銆丳ersistence Adapter銆両nternal API銆丏omain Event 鎴?Outbox 绛夋嫢鏈夋柟杈圭晫銆?- 鍘嗗彶鍊哄姟杩佺Щ鏃跺繀椤昏ˉ鍏呮ā鍧楃骇娴嬭瘯鍜屾灦鏋勬祴璇曘€?- AI Tool Runtime 鍜?Job Handler 鐨勪笟鍔¤〃鍐欏叆搴斾紭鍏堟敼涓鸿皟鐢ㄤ笟鍔℃嫢鏈夋柟銆?- 姣忔浠庡€哄姟娓呭崟绉婚櫎涓€椤癸紝閮藉簲鍚屾椂鍒犻櫎瀵瑰簲鏋舵瀯娴嬭瘯 allowlist 椤广€?
