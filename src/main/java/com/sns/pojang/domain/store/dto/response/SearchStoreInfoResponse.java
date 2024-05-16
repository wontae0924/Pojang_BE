package com.sns.pojang.domain.store.dto.response;

import com.sns.pojang.domain.store.entity.Store;
import lombok.Builder;
import lombok.Getter;

import java.text.DecimalFormat;

@Getter
@Builder
public class SearchStoreInfoResponse {
    private Long id;
    private String name;
    // 찜수
    private int likes;
    // 평점
    private String avgRating;
    private String imageUrl;
    private String sido;
    private String sigungu;
    private String bname;
    private String addressDetail;
    private String roadAddress;
    private String address;
    private String businessNumber;
    private String category;
    private String storeNumber;
    private String introduction;
    private String operationTime;
    private String status;

    public static SearchStoreInfoResponse from(Store store, int likes, double avgRating, String s3Url) {
        DecimalFormat df = new DecimalFormat("#.#");
        String sido = store.getAddress().getSido();
        String sigungu = store.getAddress().getSigungu();
        String bname = store.getAddress().getBname();
        String addressDetail = store.getAddress().getAddressDetail();
        String roadAddress = store.getAddress().getRoadAddress();
        return SearchStoreInfoResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .likes(likes)
                .avgRating(df.format(avgRating))
                .imageUrl(s3Url)
                .sido(sido)
                .sigungu(sigungu)
                .bname(bname)
                .roadAddress(roadAddress)
                .addressDetail(addressDetail)
                .address(roadAddress + " " + addressDetail)
                .businessNumber(store.getBusinessNumber())
                .category(store.getCategory())
                .storeNumber(store.getStoreNumber())
                .introduction(store.getIntroduction())
                .operationTime(store.getOperationTime())
                .status(store.getStatus().toString())
                .build();
    }
}
