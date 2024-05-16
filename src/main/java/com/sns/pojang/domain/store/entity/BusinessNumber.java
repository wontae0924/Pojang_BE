package com.sns.pojang.domain.store.entity;

import com.sns.pojang.global.config.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessNumber extends BaseTimeEntity {
    //PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String businessNumber;

    //삭제여부
    @Column(nullable = false)
    private String deleteYn = "N";

    public BusinessNumber(String businessNumber){
        this.businessNumber = businessNumber;
    }
}
