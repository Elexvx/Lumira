package com.lumira.file.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.file.entity.FileObjectEntity;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.repository.FileObjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisFileObjectRepository implements FileObjectRepository {
    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("createdAt", "created_at"), Map.entry("updatedAt", "updated_at"),
            Map.entry("originalFileName", "original_filename"), Map.entry("fileExtension", "file_extension"),
            Map.entry("fileSizeBytes", "file_size"), Map.entry("uploadedByName", "uploaded_by_name"),
            Map.entry("category", "category"), Map.entry("previewMode", "preview_mode"), Map.entry("id", "id"));
    private final FileObjectMapper mapper;

    public MyBatisFileObjectRepository(FileObjectMapper mapper) { this.mapper = mapper; }

    @Override public void insert(FileObjectEntity entity) { mapper.insert(entity); }
    @Override public FileObjectEntity findById(Long id) { return mapper.selectFreshById(id); }

    @Override
    public int softDelete(Long id, Long userId, String uuid, boolean requireOwner) {
        UpdateWrapper<FileObjectEntity> update = new UpdateWrapper<FileObjectEntity>()
                .set("deleted", 1).set("updated_by", userId).set("updated_by_uuid", uuid)
                .set("updated_at", LocalDateTime.now()).eq("id", id).eq("deleted", 0);
        if (requireOwner) update.eq("uploaded_by", userId).eq("uploaded_by_uuid", uuid);
        return mapper.update(null, update);
    }

    @Override
    public boolean existsInBucket(String storageKey) {
        return mapper.selectOne(new QueryWrapper<FileObjectEntity>().select("1")
                .eq("bucket", storageKey).eq("deleted", 0).last("limit 1")) != null;
    }

    @Override
    public List<FileObjectEntity> search(Query criteria, Access access, long offset, long limit) {
        QueryWrapper<FileObjectEntity> query = buildQuery(criteria, access);
        String sortColumn = criteria.sortField() == null
                ? "created_at"
                : SORT_COLUMNS.getOrDefault(criteria.sortField(), "created_at");
        query.orderBy(true, criteria.ascending(), sortColumn).last("limit " + limit + " offset " + offset);
        return mapper.selectList(query);
    }

    @Override
    public long countCandidates(Query criteria, Access access, long limit) {
        if (limit <= 0) return 0L;
        List<FileObjectEntity> rows = mapper.selectList(buildQuery(criteria, access).select("id").last("limit " + limit));
        return rows == null ? 0L : rows.size();
    }

    @Override
    public FileObjectEntity findVisibleById(Long id, Access access) {
        QueryWrapper<FileObjectEntity> query = buildQuery(emptyQuery(), access).eq("id", id).last("limit 1");
        return mapper.selectOne(query);
    }

    private QueryWrapper<FileObjectEntity> buildQuery(Query c, Access access) {
        QueryWrapper<FileObjectEntity> q = new QueryWrapper<FileObjectEntity>().eq("deleted", 0);
        applyAccess(q, access);
        if (StringUtils.hasText(c.keyword())) {
            String keyword = c.keyword().trim();
            q.and(n -> {
                n.like("original_filename", keyword).or().like("category", keyword).or().like("tags", keyword);
                if (c.includeRemarkInKeyword()) n.or().like("remark", keyword);
            });
        }
        if (StringUtils.hasText(c.category())) q.eq("category", c.category());
        if (StringUtils.hasText(c.fileExtension())) q.eq("file_extension", c.fileExtension());
        if (StringUtils.hasText(c.previewMode())) q.eq("preview_mode", c.previewMode());
        if (StringUtils.hasText(c.bucket())) q.eq("bucket", c.bucket());
        if (StringUtils.hasText(c.contentTypePrefix())) q.likeRight("content_type", c.contentTypePrefix());
        if (StringUtils.hasText(c.status())) q.eq("status", c.status());
        return q;
    }

    private void applyAccess(QueryWrapper<FileObjectEntity> q, Access a) {
        if (a.downloadCenter()) {
            q.and(n -> n.eq("visibility_scope", "DOWNLOAD_CENTER").or().eq("bucket", "download_center"));
        } else {
            q.and(n -> n.isNull("visibility_scope").or().ne("visibility_scope", "DOWNLOAD_CENTER"))
                    .ne("bucket", "download_center");
        }
        if (a.all()) return;
        if (a.ownerUserId() != null) {
            q.eq("uploaded_by", a.ownerUserId()).eq("uploaded_by_uuid", a.ownerUserUuid());
            return;
        }
        q.and(n -> {
            boolean hasDepartments = a.departmentIds() != null && !a.departmentIds().isEmpty();
            if (hasDepartments) n.in("department_id", a.departmentIds());
            if (a.userIds() != null && !a.userIds().isEmpty()) {
                if (hasDepartments) n.or();
                n.in("uploaded_by", a.userIds());
            }
        });
    }

    private Query emptyQuery() {
        return new Query(null, false, null, null, null, null, null, null, null, false);
    }
}
