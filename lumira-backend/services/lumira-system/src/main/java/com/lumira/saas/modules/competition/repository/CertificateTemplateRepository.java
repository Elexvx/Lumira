package com.lumira.saas.modules.competition.repository;

import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CertificateTemplateRepository {
    TemplatePage findTemplates(String keyword, String status, long offset, long limit);
    Long insertTemplate(String code, String name, String sceneType, String description, Long userId, String userUuid);
    int insertInitialVersion(Long templateId, TemplateDefaults defaults, Long userId, String userUuid);
    CertificateVO.Template findTemplate(Long id);
    int updateTemplate(Long id, String expectedCode, String expectedStatus, String code, String name,
                       String sceneType, String description, Long userId, String userUuid, LocalDateTime updatedAt);
    int archiveTemplate(Long id, String expectedCode, String expectedStatus, Long userId, String userUuid, LocalDateTime updatedAt);
    List<CertificateVO.TemplateVersion> findVersions(Long templateId);
    CertificateVO.TemplateVersion findVersion(Long versionId);
    CertificateVO.TemplateVersion findLatestVersion(Long templateId);
    int updateCanvas(Long versionId, Long templateId, Integer version, TemplateCanvas canvas,
                     Long userId, String userUuid, LocalDateTime updatedAt);
    int updateBackground(Long versionId, Long templateId, Integer version, Long fileId, String url,
                         Long userId, String userUuid, LocalDateTime updatedAt);
    int publishVersion(CertificateVO.TemplateVersion draft, Long userId, String userUuid, LocalDateTime updatedAt);
    int publishTemplate(CertificateVO.Template template, Integer version, Long userId, String userUuid, LocalDateTime updatedAt);
    int insertDraftVersion(Long templateId, Integer version, CertificateVO.TemplateVersion source, Long userId, String userUuid);
    Map<String, String> findDefaultDefinitions(String groupCode);

    record TemplatePage(List<CertificateVO.Template> records, long total) {}
    record TemplateDefaults(int width, int height, String orientation, String unit, int dpi,
                            String canvasJson, String variableSchemaJson) {}
    record TemplateCanvas(int width, int height, String orientation, String unit, int dpi,
                          String canvasJson, String variableSchemaJson) {}
}
