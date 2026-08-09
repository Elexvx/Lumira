package com.lumira.saas.modules.activity.integration;

import com.lumira.api.event.EventCatalogSourceSnapshot;
import com.lumira.api.event.EventCatalogSourceSnapshotPort;
import com.lumira.saas.modules.activity.repository.ActivityRepository;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import java.util.List;

/** Activity-owned rebuild adapter exposed only through the common catalog port. */
public class ActivityCatalogSourceSnapshotAdapter implements EventCatalogSourceSnapshotPort {

    private final ActivityRepository activityRepository;

    public ActivityCatalogSourceSnapshotAdapter(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public String sourceType() {
        return "ACTIVITY";
    }

    @Override
    public List<EventCatalogSourceSnapshot> loadCatalogSnapshots(long offset, int limit) {
        return activityRepository.search(null, null, null, null, offset, limit).records().stream()
                .map(this::toSnapshot)
                .toList();
    }

    private EventCatalogSourceSnapshot toSnapshot(ActivityVO.Activity activity) {
        return new EventCatalogSourceSnapshot(
                sourceType(),
                activity.getId(),
                activity.getCode(),
                activity.getLocale(),
                activity.getTitle(),
                activity.getSubtitle(),
                activity.getDescription(),
                activity.getStatus(),
                null,
                null,
                activity.getActivityDate(),
                null,
                activity.getActivityTime(),
                activity.getLocation(),
                activity.getImageUrl(),
                activity.getTags(),
                activity.getCtaLabel(),
                activity.getCtaHref(),
                Boolean.TRUE.equals(activity.getFeatured()),
                activity.getSort() == null ? 100 : activity.getSort(),
                activity.getUpdatedAt()
        );
    }
}
