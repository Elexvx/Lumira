package com.lumira.file.vo;

import com.lumira.common.vo.PageResponse;

public final class FileVO {

    private FileVO() {
    }

    public static class FileObjectPageResponse extends PageResponse<com.lumira.api.file.FileObjectDTO> {
        private Boolean hasMore;
        private Boolean totalCapped;

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Boolean getTotalCapped() {
            return totalCapped;
        }

        public void setTotalCapped(Boolean totalCapped) {
            this.totalCapped = totalCapped;
        }
    }

    public static class StorageSpacePageResponse extends PageResponse<com.lumira.api.file.StorageSpaceDTO> {
        private Boolean hasMore;
        private Boolean totalCapped;

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Boolean getTotalCapped() {
            return totalCapped;
        }

        public void setTotalCapped(Boolean totalCapped) {
            this.totalCapped = totalCapped;
        }
    }
}
