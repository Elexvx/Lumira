package com.lumira.saas.modules.competition.dto;

import com.lumira.saas.modules.competition.controller.CompetitionV2Controller;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitionUpsertRequestValidationTest {

    private final Validator validator;

    CompetitionUpsertRequestValidationTest() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Test
    void formalCreateRequiresTheFourFormalFields() {
        Set<?> violations = validator.validate(
                new CompetitionDTO.CompetitionUpsertRequest(),
                CompetitionDTO.CompetitionUpsertRequest.Create.class
        );

        assertThat(violations)
                .extracting(violation -> ((jakarta.validation.ConstraintViolation<?>) violation).getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "category", "competitionStart", "location");
    }

    @Test
    void updateAndDraftAllowIncompleteRequestsButStillCheckSuppliedShapes() {
        CompetitionDTO.CompetitionUpsertRequest incomplete = new CompetitionDTO.CompetitionUpsertRequest();

        assertThat(validator.validate(incomplete, CompetitionDTO.CompetitionUpsertRequest.Update.class)).isEmpty();
        assertThat(validator.validate(incomplete, CompetitionDTO.CompetitionUpsertRequest.Draft.class)).isEmpty();

        CompetitionDTO.CompetitionUpsertRequest oversizeDraft = new CompetitionDTO.CompetitionUpsertRequest();
        oversizeDraft.setTitle("x".repeat(129));
        assertThat(validator.validate(oversizeDraft, CompetitionDTO.CompetitionUpsertRequest.Draft.class))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("title");
    }

    @Test
    void controllerRoutesUseTheIntendedValidationGroups() throws Exception {
        var createParameter = CompetitionV2Controller.class
                .getMethod("createCompetition", CompetitionDTO.CompetitionUpsertRequest.class)
                .getParameters()[0];
        var updateParameter = CompetitionV2Controller.class
                .getMethod("updateCompetition", Long.class, CompetitionDTO.CompetitionUpsertRequest.class)
                .getParameters()[1];
        var createDraftParameter = CompetitionV2Controller.class
                .getMethod("createCompetitionDraft", CompetitionDTO.CompetitionUpsertRequest.class)
                .getParameters()[0];
        var updateDraftParameter = CompetitionV2Controller.class
                .getMethod("updateCompetitionDraft", Long.class, CompetitionDTO.CompetitionUpsertRequest.class)
                .getParameters()[1];

        assertThat(createParameter.getAnnotation(Validated.class).value())
                .containsExactly(CompetitionDTO.CompetitionUpsertRequest.Create.class);
        assertThat(updateParameter.getAnnotation(Validated.class).value())
                .containsExactly(CompetitionDTO.CompetitionUpsertRequest.Update.class);
        assertThat(createDraftParameter.getAnnotation(Validated.class).value())
                .containsExactly(CompetitionDTO.CompetitionUpsertRequest.Draft.class);
        assertThat(updateDraftParameter.getAnnotation(Validated.class).value())
                .containsExactly(CompetitionDTO.CompetitionUpsertRequest.Draft.class);
    }
}
