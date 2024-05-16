package com.sns.pojang.domain.member.entity;

import com.sns.pojang.domain.favorite.entity.Favorite;
import com.sns.pojang.domain.order.entity.Order;
import com.sns.pojang.domain.store.entity.Store;
import com.sns.pojang.global.config.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    //PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //닉네임
    @Column(unique = true, nullable = false)
    private String nickname;

    //이메일
    @Column(unique = true, nullable = false)
    private String email;

    //주소
    @Embedded
    @Column(nullable = false)
    private Address address;

    //비밀번호
    @Column(nullable = false)
    private String password;

    //휴대폰번호
    @Column(unique = true, nullable = false)
    private String phoneNumber;

    //권한
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;
  
    //삭제여부
    @Column(nullable = false)
    private String deleteYn = "N";

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Store> stores = new ArrayList<>();

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    @Builder
    public Member(String email, String password, String nickname, String phoneNumber, Address address, Role role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.role = role;
    }

//    회원 탈퇴
    public void withdraw() {
        this.deleteYn = "Y";
    }

//    마이페이지 수정
    public void updateMyInfo(String nickname, String phoneNumber, String sido, String sigungu, String bname, String roadAddress) {
        Address fullAddress = Address.builder()
                .sido(sido)
                .sigungu(sigungu)
                .bname(bname)
                .roadAddress(roadAddress)
                .build();
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.address = fullAddress;
    }

//    주소 수정
    public void updateAddress(String sido, String sigungu, String bname, String roadAddress) {
        Address fullAddress = Address.builder()
                .sido(sido)
                .sigungu(sigungu)
                .bname(bname)
                .roadAddress(roadAddress)
                .build();
        this.address = fullAddress;
    }
}

