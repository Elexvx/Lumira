-- Read-only cutover evidence for the competition registration/review workflow.
-- Run before keeping SAAS_WORKFLOW_LEGACY_STAGE_REVIEW_ENABLED=false.

select
    count(*) as legacy_published_result_count
from competition_stage_review_result legacy_result
where legacy_result.deleted = 0
  and legacy_result.published_at is not null;

select
    count(*) as new_published_result_count
from competition_review_publication publication
join competition_review_batch batch_record
  on batch_record.id = publication.batch_id
 and batch_record.deleted = 0
where publication.deleted = 0
  and publication.status = 'PUBLISHED';

select
    legacy_result.competition_id,
    legacy_result.stage_id,
    count(*) as legacy_result_count,
    sum(case when publication.id is null then 1 else 0 end) as missing_new_publication_count
from competition_stage_review_result legacy_result
left join competition_review_batch batch_record
  on batch_record.competition_id = legacy_result.competition_id
 and batch_record.stage_id = legacy_result.stage_id
 and batch_record.deleted = 0
left join competition_review_candidate candidate
  on candidate.batch_id = batch_record.id
 and candidate.registration_id = legacy_result.registration_id
 and candidate.deleted = 0
left join competition_review_publication publication
  on publication.batch_id = batch_record.id
 and publication.status = 'PUBLISHED'
 and publication.deleted = 0
where legacy_result.deleted = 0
  and legacy_result.published_at is not null
group by legacy_result.competition_id, legacy_result.stage_id
having missing_new_publication_count > 0
order by legacy_result.competition_id, legacy_result.stage_id;

select
    dispatch_status,
    count(*) as event_count
from platform_event_outbox
where deleted = 0
  and event_type in (
      'COMPETITION_REVIEW_RESULTS_PUBLISHED',
      'COMPETITION_REVIEW_RESULT_PUBLISHED'
  )
group by dispatch_status
order by dispatch_status;
