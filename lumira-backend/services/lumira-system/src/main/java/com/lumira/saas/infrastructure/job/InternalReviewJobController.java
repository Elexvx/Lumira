package com.lumira.saas.infrastructure.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.saas.modules.review.app.ReviewAppService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs/reviews")
@ConditionalOnLumiraControlPlaneEnabled
public class InternalReviewJobController {

    private final ObjectProvider<ReviewAppService> reviewAppServiceProvider;
    private final String jobInternalToken;

    public InternalReviewJobController(
            ObjectProvider<ReviewAppService> reviewAppServiceProvider,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobInternalToken
    ) {
        this.reviewAppServiceProvider = reviewAppServiceProvider;
        this.jobInternalToken = jobInternalToken;
    }

    @PostMapping("/assignments/expire")
    public ApiResponse<Integer> expireAssignments(
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        ReviewAppService reviewAppService = reviewAppServiceProvider.getIfAvailable();
        if (reviewAppService == null) {
            throw new BizException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Review service is unavailable"
            );
        }
        return ApiResponse.success(reviewAppService.expireDueAssignments(), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobInternalToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
