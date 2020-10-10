package kitchenpos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kitchenpos.domain.Menu;
import kitchenpos.domain.MenuGroup;
import kitchenpos.domain.Order;
import kitchenpos.domain.OrderLineItem;
import kitchenpos.domain.OrderStatus;
import kitchenpos.domain.OrderTable;
import kitchenpos.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

// Todo 여기 할 차례
class OrderAcceptanceTest extends AcceptanceTest {

    private OrderTable tableA;
    private Menu 치킨_세트;
    private Menu 맥주;

    @BeforeEach
    void setUp() {
        // 영업준비
        tableA = createTable(0, true);

        MenuGroup 세트메뉴_그룹 = createMenuGroup("세트 메뉴");
        MenuGroup 음료수_그룹 = createMenuGroup("음료수");

        List<Product> 치킨세트_구성상품들 = new ArrayList<>();

        치킨세트_구성상품들.add(createProduct("후라이드 치킨", 10_000));
        치킨세트_구성상품들.add(createProduct("감자 튀김", 4_000));
        치킨세트_구성상품들.add(createProduct("매운 치즈 떡볶이", 5_000));

        치킨_세트 = createMenu("치킨 세트", 치킨세트_구성상품들, 16_000L, 세트메뉴_그룹.getId());

        Product beerProduct = createProduct("맥주 500CC", 4_000);
        맥주 = createMenu("맥주 500cc", Collections.singletonList(beerProduct),
            beerProduct.getPrice().longValue(), 음료수_그룹.getId());

        // tableA 에 손님 입장
        changeEmptyToFalse(tableA);
    }

    /**
     * Feature: 주문을 관리한다.
     *
     * Given 메뉴, empty=false 인 테이블이 존재한다.
     * When 테이블에서 메뉴들을 주문한다.
     * Then 주문이 들어간다. 주문 직후의 주문 상태는 '요리중' 이다.
     *
     * Given 어떤 테이블에서 치킨세트와 맥주를 주문했다.
     * When 해당 주문이 완수되어, 주문 상태를 '식사중' 으로 바꾼다.
     * Then 주문 상태가 '식사중' 으로 바뀐다.
     *
     * Given 식사중인 테이블이 있다.
     * When 식사가 완료되어, 주문 상태를 '완료' 로 바꾼다.
     * Then 주문 상태가 '완료' 로 바뀐다.
     */
    @Test
    @DisplayName("한 테이블의 주문을 관리한다.")
    void manageOrderOfOneTable() {
        // 손님이 주문한 것을 등록한다.
        List<OrderLineItemForTest> orderLineItems = new ArrayList<>();

        orderLineItems.add(new OrderLineItemForTest(치킨_세트.getId(), 1));
        orderLineItems.add(new OrderLineItemForTest(맥주.getId(), 4));

        Order order = requestOrder(tableA, orderLineItems);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COOKING.toString());
        assertThat(order.getOrderTableId()).isEqualTo(tableA.getId());

        assertThatOrderHistoryIsSameToOrderLineItems(orderLineItems, order);

        // 주문이 완수되어, 주문 상태를 '식사중' 으로 바꾼다.
        order = changeOrderStatusTo(OrderStatus.MEAL, order);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.MEAL.toString());

        // 식사가 완료되어, 주문 상태를 '완료' 로 바꾼다.
        order = changeOrderStatusTo(OrderStatus.COMPLETION, order);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETION.toString());
    }

    private Order requestOrder(OrderTable orderTable, List<OrderLineItemForTest> orderLineItems) {
        Map<String, Object> body = new HashMap<>();

        body.put("orderTableId", orderTable.getId());

        List<Map> orderLineItemsForRequest = new ArrayList<>();

        orderLineItems.forEach(orderLineItem -> {
            Map<String, Object> orderLineItemForRequest = new HashMap<>();

            orderLineItemForRequest.put("menuId", orderLineItem.getMenuId());
            orderLineItemForRequest.put("quantity", orderLineItem.getQuantity());

            orderLineItemsForRequest.add(Collections.unmodifiableMap(orderLineItemForRequest));
        });

        body.put("orderLineItems", orderLineItemsForRequest);

        return
            given()
                .body(body)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .accept(MediaType.APPLICATION_JSON_VALUE)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(HttpStatus.CREATED.value())
                .extract().as(Order.class);
    }

    private void assertThatOrderHistoryIsSameToOrderLineItems(
        List<OrderLineItemForTest> orderLineItems, Order order) {
        for (OrderLineItemForTest orderLineItem : orderLineItems) {
            assertThatOrderLineItemIsIncludedInOrder(orderLineItem, order);
        }
    }

    private void assertThatOrderLineItemIsIncludedInOrder(OrderLineItemForTest orderLineItem,
        Order order) {
        List<OrderLineItem> collect = order.getOrderLineItems().stream()
            .filter(responseOrderLineItem ->
                responseOrderLineItem.getMenuId().equals(orderLineItem.getMenuId()))
            .collect(Collectors.toList());

        assertThat(collect).hasSize(1);
        assertThat(collect.get(0).getOrderId()).isNotNull();
        assertThat(collect.get(0).getMenuId()).isEqualTo(orderLineItem.getMenuId());
        assertThat(collect.get(0).getQuantity()).isEqualTo(orderLineItem.getQuantity());
    }

    private Order changeOrderStatusTo(OrderStatus orderStatus, Order order) {
        Map<String, Object> body = new HashMap<>();

        body.put("orderStatus", orderStatus);

        return
            given()
                .body(body)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .accept(MediaType.APPLICATION_JSON_VALUE)
            .when()
                .put("/api/orders/" + order.getId() + "/order-status")
            .then()
                .statusCode(HttpStatus.OK.value())
                .extract().as(Order.class);
    }

    static class OrderLineItemForTest {

        private Long menuId;
        private int quantity;

        OrderLineItemForTest(Long menuId, int quantity) {
            this.menuId = menuId;
            this.quantity = quantity;
        }

        Long getMenuId() {
            return menuId;
        }

        int getQuantity() {
            return quantity;
        }
    }
}