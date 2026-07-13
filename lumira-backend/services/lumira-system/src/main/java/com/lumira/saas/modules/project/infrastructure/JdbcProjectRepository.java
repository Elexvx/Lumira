package com.lumira.saas.modules.project.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.repository.ProjectRepository;
import com.lumira.saas.modules.project.vo.ProjectVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private static final String SELECT = """
            select id, code, locale, title, category, description,
                   image_url as imageUrl, owner_name as ownerName, rating, sort, status,
                   tags, cta_label as ctaLabel, cta_href as ctaHref, featured,
                   created_at as createdAt, updated_at as updatedAt
            """;
    private final MyBatisQueryOperations database;

    public JdbcProjectRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<String> findEnabledDictValues(String dictCode) {
        return database.queryForList("""
                select i.item_value as itemValue
                from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id and i.deleted = 0 and i.status = 'ENABLED'
                where t.dict_code = ? and t.deleted = 0 and t.status = 'ENABLED'
                order by i.sort_no asc, i.id asc
                """, dictCode).stream()
                .map(row -> row.get("itemValue"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    @Override
    public PageData search(String keyword, String category, String ownerName, String rating, String status,
                           String locale, Boolean featured, long offset, long limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_project where deleted = 0");
        if (StringUtils.hasText(keyword)) {
            where.append(" and (title like ? or code like ? or description like ? or tags like ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
        }
        if (StringUtils.hasText(category)) { where.append(" and category = ?"); args.add(category); }
        if (StringUtils.hasText(ownerName)) { where.append(" and owner_name like ?"); args.add("%" + ownerName.trim() + "%"); }
        if (StringUtils.hasText(rating)) { where.append(" and rating = ?"); args.add(rating); }
        if (StringUtils.hasText(status)) { where.append(" and status = ?"); args.add(status); }
        if (StringUtils.hasText(locale)) { where.append(" and locale = ?"); args.add(locale); }
        if (featured != null) { where.append(" and featured = ?"); args.add(featured ? 1 : 0); }
        Long total = database.queryForObject("select count(1)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(offset);
        pageArgs.add(limit);
        List<ProjectVO.Project> records = database.query(
                SELECT + where + " order by sort asc, featured desc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ProjectVO.Project.class), pageArgs.toArray());
        return new PageData(records, total == null ? 0L : total);
    }

    @Override
    public Optional<ProjectVO.Project> findById(Long id) {
        return database.query(SELECT + " from aiadc_project where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ProjectVO.Project.class), id).stream().findFirst();
    }

    @Override
    public Long create(ProjectDTO.ProjectUpsertRequest p, Long userId, String uuid) {
        int inserted = database.update("""
                insert into aiadc_project (
                    code, locale, title, category, description, image_url, owner_name, rating, sort, status,
                    tags, cta_label, cta_href, featured, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, p.getCode(), p.getLocale(), p.getTitle(), p.getCategory(), p.getDescription(), p.getImageUrl(),
                p.getOwnerName(), p.getRating(), p.getSort(), p.getStatus(), p.getTags(), p.getCtaLabel(), p.getCtaHref(),
                Boolean.TRUE.equals(p.getFeatured()) ? 1 : 0, userId, uuid, userId, uuid);
        if (inserted <= 0) return null;
        return database.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public int update(Long id, ProjectVO.Project e, ProjectDTO.ProjectUpsertRequest p, Long userId, String uuid) {
        return database.update("""
                update aiadc_project
                set code = ?, locale = ?, title = ?, category = ?, description = ?, image_url = ?,
                    owner_name = ?, rating = ?, sort = ?, status = ?, tags = ?, cta_label = ?,
                    cta_href = ?, featured = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and locale = ? and status = ? and deleted = 0
                """, p.getCode(), p.getLocale(), p.getTitle(), p.getCategory(), p.getDescription(), p.getImageUrl(),
                p.getOwnerName(), p.getRating(), p.getSort(), p.getStatus(), p.getTags(), p.getCtaLabel(), p.getCtaHref(),
                Boolean.TRUE.equals(p.getFeatured()) ? 1 : 0, userId, uuid, LocalDateTime.now(), id,
                e.getCode(), e.getLocale(), e.getStatus());
    }

    @Override
    public int delete(Long id, ProjectVO.Project e, Long userId, String uuid) {
        return database.update("""
                update aiadc_project
                set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and code = ? and locale = ? and status = ? and deleted = 0
                """, userId, uuid, LocalDateTime.now(), id, e.getCode(), e.getLocale(), e.getStatus());
    }
}
