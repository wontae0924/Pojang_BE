package com.sns.pojang.domain.order.service;

import com.sns.pojang.domain.member.entity.Member;
import com.sns.pojang.domain.member.exception.MemberNotFoundException;
import com.sns.pojang.domain.member.repository.MemberRepository;
import com.sns.pojang.domain.menu.entity.Menu;
import com.sns.pojang.domain.menu.entity.MenuOption;
import com.sns.pojang.domain.menu.exception.MenuNotFoundException;
import com.sns.pojang.domain.menu.exception.MenuOptionNotFoundException;
import com.sns.pojang.domain.menu.repository.MenuOptionRepository;
import com.sns.pojang.domain.menu.repository.MenuRepository;
import com.sns.pojang.domain.notification.domain.NotificationType;
import com.sns.pojang.domain.notification.service.NotificationService;
import com.sns.pojang.domain.order.dto.request.OrderRequest;
import com.sns.pojang.domain.order.dto.request.SelectedMenuRequest;
import com.sns.pojang.domain.order.dto.request.SelectedOptionRequest;
import com.sns.pojang.domain.order.dto.response.CountResponse;
import com.sns.pojang.domain.order.dto.response.CreateOrderResponse;
import com.sns.pojang.domain.order.dto.response.OrderResponse;
import com.sns.pojang.domain.order.entity.Order;
import com.sns.pojang.domain.order.entity.OrderMenu;
import com.sns.pojang.domain.order.entity.OrderMenuOption;
import com.sns.pojang.domain.order.entity.OrderStatus;
import com.sns.pojang.domain.order.exception.*;
import com.sns.pojang.domain.order.repository.OrderRepository;
import com.sns.pojang.domain.store.entity.Store;
import com.sns.pojang.domain.store.exception.StoreNotFoundException;
import com.sns.pojang.domain.store.repository.StoreRepository;
import com.sns.pojang.global.error.exception.InvalidValueException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

import static com.sns.pojang.global.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final NotificationService notificationService;

    // 메뉴 주문
    @Transactional
    public CreateOrderResponse createOrder(Long storeId, OrderRequest orderRequest) {
        Member findMember = findMember();
        Store findStore = findStore(storeId);

        // 총 주문 금액 검증
        validateTotalPrice(orderRequest.getSelectedMenus(), orderRequest.getTotalPrice());
        Order order = orderRequest.toEntity(findStore);
        order.attachMember(findMember);

        for (SelectedMenuRequest selectedMenu : orderRequest.getSelectedMenus()){
            Menu findMenu = findMenu(selectedMenu.getMenuId());
            validateMenu(findStore, findMenu);
            OrderMenu orderMenu = OrderMenu.builder()
                    .quantity(selectedMenu.getQuantity())
                    .menu(findMenu)
                    .build();
            // 메뉴 옵션이 있으면 아래 코드 실행
            if (selectedMenu.getSelectedMenuOptions() != null){
                for (SelectedOptionRequest selectedOptionRequest : selectedMenu.getSelectedMenuOptions()){
                    MenuOption findMenuOption = findMenuOption(selectedOptionRequest.getId());
                    OrderMenuOption orderMenuOption = OrderMenuOption.builder()
                            .menuOption(findMenuOption)
                            .build();
                    orderMenuOption.attachOrderMenu(orderMenu);
                }
            }
            orderMenu.attachOrder(order);
        }
        Order newOrder = orderRepository.save(order);
        String relatedUrl = "/api/stores/" + order.getStore().getId() + "/orders/" + order.getId();
        notificationService.send(findStore.getMember(), newOrder,
                NotificationType.USER_ORDER, "새로운 주문 요청입니다.", relatedUrl);

        return CreateOrderResponse.from(newOrder);
    }

    // 고객의 주문 취소
    @Transactional
    public OrderResponse cancelMemberOrder(Long storeId, Long orderId){
        Member findMember = findMember();
        Order findOrder = findOrder(orderId);
        Store findStore = findStore(storeId);
        validateOrder(findMember, findOrder);
        validateStore(findStore, findOrder);

        if (findOrder.getOrderStatus() == OrderStatus.CANCELED){
            throw new OrderAlreadyCanceledException();
        }
        if (findOrder.getOrderStatus() == OrderStatus.CONFIRM){
            throw new OrderAlreadyConfirmedException();
        }
        findOrder.updateOrderStatus(OrderStatus.CANCELED);
        return OrderResponse.from(findOrder);
    }

    // 가게의 주문 취소
    @Transactional
    public OrderResponse cancelStoreOrder(Long storeId, Long orderId) {
        Member findMember = findMember();
        Order findOrder = findOrder(orderId);
        Store findStore = findStore(storeId);
        validateOwner(findMember, findStore);

        if (findOrder.getOrderStatus() == OrderStatus.CANCELED){
            throw new OrderAlreadyCanceledException();
        }
        if (findOrder.getOrderStatus() == OrderStatus.CONFIRM){
            throw new InvalidValueException(CANNOT_CANCEL_ORDER);
        }
        if (findOrder.getOrderStatus() == OrderStatus.ORDERED){
            throw new InvalidValueException(CANNOT_CANCEL_ORDER);
        }

        findOrder.updateOrderStatus(OrderStatus.CANCELED);
        return OrderResponse.from(findOrder);
    }

    // 주문 접수
    @Transactional
    public OrderResponse acceptOrder(Long storeId, Long orderId) {
        Member findMember = findMember();
        Order findOrder = findOrder(orderId);
        Store findStore = findStore(storeId);
        validateOwner(findMember, findStore);

        log.info("검증 완료");

        if (findOrder.getOrderStatus() == OrderStatus.CANCELED){
            throw new InvalidValueException(CANNOT_ACCEPT_ORDER);
        }
        if (findOrder.getOrderStatus() == OrderStatus.CONFIRM){
            throw new InvalidValueException(CANNOT_ACCEPT_ORDER);
        }
        if (findOrder.getOrderStatus() == OrderStatus.ORDERED){
            throw new OrderAlreadyOrderedException();
        }

        log.info("예외 통과");
        findOrder.updateOrderStatus(OrderStatus.ORDERED);
        log.info("상태 변경 완료");
        return OrderResponse.from(findOrder);
    }

    // 주문 확정
    @Transactional
    public OrderResponse confirmOrder(Long storeId, Long orderId) {
        Member findMember = findMember();
        Order findOrder = findOrder(orderId);
        Store findStore = findStore(storeId);
        validateOwner(findMember, findStore);

        if (findOrder.getOrderStatus() == OrderStatus.CANCELED){
            throw new InvalidValueException(CANNOT_CONFIRM_ORDER);
        }
        if (findOrder.getOrderStatus() == OrderStatus.CONFIRM){
            throw new OrderAlreadyConfirmedException();
        }
        if (findOrder.getOrderStatus() == OrderStatus.PENDING){
            throw new InvalidValueException(CANNOT_CONFIRM_ORDER);
        }
        findOrder.updateOrderStatus(OrderStatus.CONFIRM);
        return OrderResponse.from(findOrder);
    }

    // 주문 상세 조회
    @Transactional
    public OrderResponse getOrderDetail(Long storeId, Long orderId) {
        Member findMember = findMember();
        Order findOrder = findOrder(orderId);
        Store findStore = findStore(storeId);
        validateOrder(findMember, findOrder);
        validateStore(findStore, findOrder);

        return OrderResponse.from(findOrder);
    }

    @Transactional
    public List<OrderResponse> getStoreOrders(Long storeId, Pageable pageable) {
        Member findMember = findMember();
        Store findStore = findStore(storeId);
        validateOwner(findMember, findStore);
        Page<Order> orders = orderRepository.findByStoreOrderByCreatedTimeDesc(findStore, pageable);

        return orders.stream().map(OrderResponse::from).collect(Collectors.toList());
    }



    // 프론트에서 받아온 총 주문 금액 검증 (프론트는 중간에 연산이 조작 되기 쉬움)
    private void validateTotalPrice(List<SelectedMenuRequest> selectedMenus, int totalPrice){
        int calculatedTotalPrice = 0;
        for (SelectedMenuRequest menuRequest : selectedMenus){
            Menu menu = findMenu(menuRequest.getMenuId());
            int menuOptionTotal = 0;
            if (menuRequest.getSelectedMenuOptions() != null){
                for (SelectedOptionRequest selectedOptionRequest : menuRequest.getSelectedMenuOptions()){
                    MenuOption menuOption = findMenuOption(selectedOptionRequest.getId());
                    log.info("옵션 금액: " + menuOption.getPrice());
                    menuOptionTotal += menuOption.getPrice() * menuRequest.getQuantity();
                }
            }
            calculatedTotalPrice += menu.getPrice() * menuRequest.getQuantity() + menuOptionTotal;
        }
        log.info("고객이 주문한 금액: " + totalPrice);
        log.info("서버에서 계산한 금액: " + calculatedTotalPrice);
        if (calculatedTotalPrice != totalPrice){
            throw new InvalidTotalPriceException();
        }
    }

    private void validateOrder(Member member, Order order){
        if (!order.getMember().equals(member)){
            throw new AccessDeniedException(MEMBER_ORDER_MISMATCH.getMessage());
        }
    }

    private void validateStore(Store store, Order order){
        if (!order.getStore().equals(store)){
            throw new AccessDeniedException(STORE_ORDER_MISMATCH.getMessage());
        }
    }

    // 가게 등록한 Owner인지 검증
    private void validateOwner(Member member, Store store){
        if (!store.getMember().equals(member)){
            throw new AccessDeniedException(store.getName() + "의 사장님이 아닙니다.");
        }
    }

    // menu의 store와 입력된 store의 일치 여부 확인
    private void validateMenu(Store store, Menu menu){
        if (!menu.getStore().equals(store)){
            throw new AccessDeniedException(STORE_MENU_MISMATCH.getMessage());
        }
    }

    private Member findMember(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(MemberNotFoundException::new);
    }

    private Store findStore(Long storeId){
        return storeRepository.findById(storeId)
                .orElseThrow(StoreNotFoundException::new);
    }

    private Order findOrder(Long orderId){
        return orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
    }

    private Menu findMenu(Long menuId){
        return menuRepository.findById(menuId)
                .orElseThrow(MenuNotFoundException::new);
    }

    private MenuOption findMenuOption(Long menuOptionId){
        return menuOptionRepository.findById(menuOptionId)
                .orElseThrow(MenuOptionNotFoundException::new);
    }

    public CountResponse getCount(Long storeId) {
        Store store = storeRepository.findById(storeId).orElseThrow(StoreNotFoundException::new);
        List<Order> orders = orderRepository.findByStoreAndOrderStatus(store, OrderStatus.CONFIRM);
        return CountResponse.from(store, orders.size());
    }
}
