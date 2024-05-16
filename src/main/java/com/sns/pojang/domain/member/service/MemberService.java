package com.sns.pojang.domain.member.service;


import com.sns.pojang.domain.favorite.entity.Favorite;
import com.sns.pojang.domain.favorite.exception.FavoriteNotFoundException;
import com.sns.pojang.domain.favorite.repository.FavoriteRepository;
import com.sns.pojang.domain.member.dto.request.*;
import com.sns.pojang.domain.member.dto.response.*;
import com.sns.pojang.domain.member.entity.Member;
import com.sns.pojang.domain.member.entity.Role;
import com.sns.pojang.domain.member.exception.*;
import com.sns.pojang.domain.member.repository.MemberRepository;
import com.sns.pojang.domain.member.utils.SmsCertificationUtil;
import com.sns.pojang.domain.order.dto.response.OrderResponse;
import com.sns.pojang.domain.order.entity.Order;
import com.sns.pojang.domain.order.repository.OrderRepository;
import com.sns.pojang.domain.review.dto.response.ReviewResponse;
import com.sns.pojang.domain.review.entity.Review;
import com.sns.pojang.domain.review.repository.ReviewRepository;
import com.sns.pojang.global.config.s3.S3Service;
import com.sns.pojang.global.config.security.jwt.JwtProvider;
import com.sns.pojang.global.error.exception.EntityNotFoundException;
import com.sns.pojang.global.utils.CertificationGenerator;
import com.sns.pojang.global.utils.CertificationNumberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.sns.pojang.global.error.ErrorCode.MEMBER_NOT_FOUND;
import static com.sns.pojang.global.error.ErrorCode.MEMBER_ORDER_MISMATCH;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CertificationNumberRepository certificationNumberRepository;
    private final CertificationGenerator certificationGenerator;
    private final SmsCertificationUtil smsCertificationUtil;

    @Transactional
    public CreateMemberResponse createUser(CreateMemberRequest createMemberRequest) throws EmailDuplicateException, NicknameDuplicateException{
        if(memberRepository.findByNickname(createMemberRequest.getNickname()).isPresent()) {
            throw new NicknameDuplicateException();
        }
        if (memberRepository.findByEmail(createMemberRequest.getEmail()).isPresent()) {
            throw new EmailDuplicateException();
        }
        if(memberRepository.findByPhoneNumber(createMemberRequest.getPhoneNumber()).isPresent()) {
            throw new PhoneNumberDuplicateException();
        }
        Member newMember = createMemberRequest.toEntity(passwordEncoder, Role.ROLE_USER);

        return CreateMemberResponse.from(memberRepository.save(newMember));
    }

    @Transactional
    public CreateMemberResponse createOwner(CreateMemberRequest createMemberRequest) throws EmailDuplicateException, NicknameDuplicateException {
        if(memberRepository.findByNickname(createMemberRequest.getNickname()).isPresent()) {
            throw new NicknameDuplicateException();
        }
        if (memberRepository.findByEmail(createMemberRequest.getEmail()).isPresent()){
            throw new EmailDuplicateException();
        }
        if(memberRepository.findByPhoneNumber(createMemberRequest.getPhoneNumber()).isPresent()) {
            throw new PhoneNumberDuplicateException();
        }
        Member newMember = createMemberRequest.toEntity(passwordEncoder, Role.ROLE_OWNER);

        return CreateMemberResponse.from(memberRepository.save(newMember));
    }

    @Transactional
    public LoginMemberResponse login(LoginMemberRequest loginMemberRequest) {
        // Email 존재 여부 Check
        Member findMember = memberRepository.findByEmail(loginMemberRequest.getEmail())
                .orElseThrow(EmailNotFoundException::new);

        // 계정 삭제 여부 Check
        if(findMember.getDeleteYn().equals("Y")) {
            throw new MemberNotFoundException();
        }

        // Password 일치 여부 Check
        if (!passwordEncoder.matches(loginMemberRequest.getPassword(), findMember.getPassword())){
            throw new PasswordNotMatchException();
        }

        String token = jwtProvider.createToken(findMember.getEmail(),
                findMember.getRole().toString());

        return LoginMemberResponse.builder()
                .id(findMember.getId())
                .token(token)
                .build();
    }

    public FindMyInfoResponse findMyInfo() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(MemberNotFoundException::new);
        return FindMyInfoResponse.from(member);
    }

    @Transactional
    public UpdateMyInfoResponse updateMyInfo(UpdateMyInfoRequest updateMyInfoRequest) throws NicknameDuplicateException{
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException(MEMBER_NOT_FOUND));

        if (!member.getNickname().equals(updateMyInfoRequest.getNickname()) &&
                (memberRepository.findByNickname(updateMyInfoRequest.getNickname()).isPresent())){
               throw new NicknameDuplicateException();
        }
        if (!member.getPhoneNumber().equals(updateMyInfoRequest.getPhoneNumber()) &&
                (memberRepository.findByPhoneNumber(updateMyInfoRequest.getPhoneNumber()).isPresent())){
                throw new PhoneNumberDuplicateException();
        }
        member.updateMyInfo(updateMyInfoRequest.getNickname(),
                updateMyInfoRequest.getPhoneNumber(),
                updateMyInfoRequest.getSido(),
                updateMyInfoRequest.getSigungu(),
                updateMyInfoRequest.getBname(),
                updateMyInfoRequest.getRoadAddress());

        return UpdateMyInfoResponse.from(member);
    }

    public FindAddressResponse findMyAddress() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(MemberNotFoundException::new);
        return FindAddressResponse.from(member);
    }

    public FindAddressResponse updateMyAddress(UpdateAddressRequest updateAddressRequest) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(MemberNotFoundException::new);
        member.updateAddress(updateAddressRequest.getSido(), updateAddressRequest.getSigungu(), updateAddressRequest.getBname(), updateAddressRequest.getRoadAddress());
        return FindAddressResponse.from(member);
    }


    public void withdraw() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(MemberNotFoundException::new);
        member.withdraw();
    }

    // 회원탈퇴 30일 이후 회원정보 삭제 - 재가입 방지
    @Scheduled(cron = "0 0/1 * * * *") // 매분마다 실행
    public void memberSchedule() {
        List<Member> members = memberRepository.findByDeleteYn("Y");
        for(Member m : members) {
            // 회원탈퇴 후 3분 뒤 삭제
            if(m.getUpdatedTime().plusMinutes(3).isBefore(LocalDateTime.now())) {
                memberRepository.delete(m);
            }
        }
    }

    public SmsCertificationResponse sendSms(SendCertificationRequest sendCertificationRequest) throws NoSuchAlgorithmException {
        String to = sendCertificationRequest.getPhoneNumber();
        String certificationNumber = certificationGenerator.createCertificationNumber();
        smsCertificationUtil.sendSms(to, certificationNumber);
        certificationNumberRepository.saveCertificationNumber(to,certificationNumber);

        return new SmsCertificationResponse(to, certificationNumber);
    }

    public List<FindFavoritesResponse> findFavorites() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(MemberNotFoundException::new);
        List<Favorite> favorites = favoriteRepository.findByMemberAndFavoriteYn(member, "Y");
        if(favorites.isEmpty()) {
            throw new FavoriteNotFoundException();
        }
        List<FindFavoritesResponse> findFavoritesResponses= new ArrayList<>();
        for(Favorite favorite : favorites) {
            FindFavoritesResponse favoritesResponse = FindFavoritesResponse.from(favorite, favorite.getStore().getImageUrl());
            findFavoritesResponses.add(favoritesResponse);
        }
        return findFavoritesResponses;
    }

    public List<ReviewResponse> findReviews() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(MemberNotFoundException::new);
        List<Review> reviews = reviewRepository.findByMemberAndDeleteYn(member, "N");
        List<ReviewResponse> reviewResponses= new ArrayList<>();
        for(Review review : reviews) {
            ReviewResponse reviewResponse = ReviewResponse.from(review, review.getImageUrl());
            reviewResponses.add(reviewResponse);
        }
        return reviewResponses;
    }
  
    public void validateEmail(ValidateEmailRequest validateEmailRequest) {
        String email = validateEmailRequest.getEmail();
        if (memberRepository.findByEmail(email).isPresent()){
            throw new EmailDuplicateException();
        }
    }

    public void validateNickname(ValidateNicknameRequest validateNicknameRequest) {
        String nickname = validateNicknameRequest.getNickname();
        if (memberRepository.findByNickname(nickname).isPresent()){
            throw new NicknameDuplicateException();
        }
    }

    public List<OrderResponse> getMyOrders(Pageable pageable) {
        Member findMember = findMember();
        Page<Order> myOrders = orderRepository.findByMemberOrderByCreatedTimeDesc(findMember, pageable);

        List<OrderResponse> orderResponses = new ArrayList<>();
        for (Order order : myOrders){
            orderResponses.add(OrderResponse.from(order, order.getStore().getImageUrl()));
        }

        return orderResponses;
    }

    private void validateOrder(Member member, Order order){
        if (!order.getMember().equals(member)){
            throw new AccessDeniedException(MEMBER_ORDER_MISMATCH.getMessage());
        }
    }

    private Member findMember(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(MemberNotFoundException::new);
    }

    public FindEmailResponse findEmail(FindEmailRequest findEmailRequest) {
        Member member = memberRepository.findByPhoneNumber(findEmailRequest.getPhoneNumber()).orElseThrow(MemberNotFoundException::new);
        return FindEmailResponse.from(member);
    }
}
