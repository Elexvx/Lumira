package com.lumira.saas.modules.eventcatalog.infrastructure;

import com.lumira.api.event.EventCatalogItem;
import com.lumira.api.event.EventCatalogSourceSnapshot;
import com.lumira.saas.modules.eventcatalog.repository.EventCatalogRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcEventCatalogRepository implements EventCatalogRepository {

    private static final RowMapper<EventCatalogItem> ITEM_ROW_MAPPER = new EventCatalogItemRowMapper();
    private static final String SELECT_COLUMNS = """
            select id, source_type, source_id, source_uuid, locale, title, subtitle, summary, status,
                   registration_start, registration_end, event_start, event_end, event_time, location,
                   image_url, tags, cta_label, cta_href, featured, sort, version, updated_at
            """;
    private static final String UPSERT = """
            insert into event_catalog_item (
                source_type, source_id, source_uuid, locale, title, subtitle, summary, status,
                registration_start, registration_end, event_start, event_end, event_time, location,
                image_url, tags, cta_label, cta_href, featured, sort, version, last_event_id,
                source_updated_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
            on duplicate key update
                source_uuid = if(values(last_event_id) > last_event_id, values(source_uuid), source_uuid),
                locale = if(values(last_event_id) > last_event_id, values(locale), locale),
                title = if(values(last_event_id) > last_event_id, values(title), title),
                subtitle = if(values(last_event_id) > last_event_id, values(subtitle), subtitle),
                summary = if(values(last_event_id) > last_event_id, values(summary), summary),
                status = if(values(last_event_id) > last_event_id, values(status), status),
                registration_start = if(values(last_event_id) > last_event_id, values(registration_start), registration_start),
                registration_end = if(values(last_event_id) > last_event_id, values(registration_end), registration_end),
                event_start = if(values(last_event_id) > last_event_id, values(event_start), event_start),
                event_end = if(values(last_event_id) > last_event_id, values(event_end), event_end),
                event_time = if(values(last_event_id) > last_event_id, values(event_time), event_time),
                location = if(values(last_event_id) > last_event_id, values(location), location),
                image_url = if(values(last_event_id) > last_event_id, values(image_url), image_url),
                tags = if(values(last_event_id) > last_event_id, values(tags), tags),
                cta_label = if(values(last_event_id) > last_event_id, values(cta_label), cta_label),
                cta_href = if(values(last_event_id) > last_event_id, values(cta_href), cta_href),
                featured = if(values(last_event_id) > last_event_id, values(featured), featured),
                sort = if(values(last_event_id) > last_event_id, values(sort), sort),
                version = greatest(version, values(version)),
                last_event_id = greatest(last_event_id, values(last_event_id)),
                source_updated_at = if(values(last_event_id) > last_event_id, values(source_updated_at), source_updated_at),
                updated_at = if(values(last_event_id) > last_event_id, current_timestamp, updated_at)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcEventCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void apply(CatalogWrite write) {
        jdbcTemplate.update(
                UPSERT,
                write.sourceType(),
                write.sourceId(),
                write.sourceUuid(),
                write.locale(),
                write.title(),
                write.subtitle(),
                write.summary(),
                write.status(),
                write.registrationStart(),
                write.registrationEnd(),
                write.eventStart(),
                write.eventEnd(),
                write.eventTime(),
                write.location(),
                write.imageUrl(),
                write.tags(),
                write.ctaLabel(),
                write.ctaHref(),
                write.featured() ? 1 : 0,
                write.sort(),
                write.outboxSequence(),
                write.outboxSequence(),
                write.sourceUpdatedAt()
        );
    }

    @Override
    public void replaceSource(String sourceType, List<EventCatalogSourceSnapshot> snapshots, long watermark) {
        jdbcTemplate.update("delete from event_catalog_item where source_type = ?", sourceType);
        for (EventCatalogSourceSnapshot snapshot : snapshots) {
            apply(new CatalogWrite(
                    snapshot.sourceType(),
                    snapshot.sourceId(),
                    snapshot.sourceUuid(),
                    snapshot.locale(),
                    snapshot.title(),
                    snapshot.subtitle(),
                    snapshot.summary(),
                    snapshot.status(),
                    snapshot.registrationStart(),
                    snapshot.registrationEnd(),
                    snapshot.eventStart(),
                    snapshot.eventEnd(),
                    snapshot.eventTime(),
                    snapshot.location(),
                    snapshot.imageUrl(),
                    snapshot.tags(),
                    snapshot.ctaLabel(),
                    snapshot.ctaHref(),
                    snapshot.featured(),
                    snapshot.sort(),
                    watermark,
                    snapshot.sourceUpdatedAt()
            ));
        }
    }

    @Override
    public PageData findPublished(CatalogSearch search) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from event_catalog_item where status = 'published'");
        if (StringUtils.hasText(search.keyword())) {
            String pattern = "%" + search.keyword().trim() + "%";
            where.append(" and (title like ? or subtitle like ? or summary like ? or source_uuid like ?)");
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (StringUtils.hasText(search.sourceType())) {
            where.append(" and source_type = ?");
            args.add(search.sourceType().trim());
        }
        if (StringUtils.hasText(search.locale())) {
            where.append(" and find_in_set(?, replace(locale, ' ', '')) > 0");
            args.add(search.locale().trim());
        }
        if (search.featured() != null) {
            where.append(" and featured = ?");
            args.add(search.featured() ? 1 : 0);
        }
        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(search.offset());
        pageArgs.add(search.limit());
        List<EventCatalogItem> records = jdbcTemplate.query(
                SELECT_COLUMNS + where + " order by featured desc, sort asc, event_start asc, id desc limit ?, ?",
                ITEM_ROW_MAPPER,
                pageArgs.toArray()
        );
        return new PageData(records, total == null ? 0L : total);
    }

    private static final class EventCatalogItemRowMapper implements RowMapper<EventCatalogItem> {
        @Override
        public EventCatalogItem mapRow(ResultSet row, int rowNum) throws SQLException {
            return new EventCatalogItem(
                    row.getLong("id"),
                    row.getString("source_type"),
                    row.getLong("source_id"),
                    row.getString("source_uuid"),
                    row.getString("locale"),
                    row.getString("title"),
                    row.getString("subtitle"),
                    row.getString("summary"),
                    row.getString("status"),
                    row.getString("registration_start"),
                    row.getString("registration_end"),
                    row.getString("event_start"),
                    row.getString("event_end"),
                    row.getString("event_time"),
                    row.getString("location"),
                    row.getString("image_url"),
                    row.getString("tags"),
                    row.getString("cta_label"),
                    row.getString("cta_href"),
                    row.getInt("featured") != 0,
                    row.getInt("sort"),
                    row.getLong("version"),
                    row.getObject("updated_at", LocalDateTime.class)
            );
        }
    }
}
