update site_page p
left join site_page existing
  on existing.tenant_id = p.tenant_id
 and existing.site_id = p.site_id
 and existing.slug = concat('/', p.slug)
 and existing.deleted = p.deleted
set p.slug = concat('/', p.slug)
where p.deleted = 0
  and p.slug <> ''
  and p.slug not like '/%'
  and existing.id is null;

update site_content c
left join site_content existing
  on existing.tenant_id = c.tenant_id
 and existing.site_id = c.site_id
 and existing.slug = concat('/', c.slug)
 and existing.deleted = c.deleted
set c.slug = concat('/', c.slug)
where c.deleted = 0
  and c.slug <> ''
  and c.slug not like '/%'
  and existing.id is null;
