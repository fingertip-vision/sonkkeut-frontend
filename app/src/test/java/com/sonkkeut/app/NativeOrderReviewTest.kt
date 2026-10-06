package com.sonkkeut.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeOrderReviewTest {
    @Test fun reviewIncludesEveryItemQuantityOptionAndDineChoice() {
        val order=NativeOrder(listOf(
            NativeOrderItem("아메리카노",2,3000,"hot","라지"),
            NativeOrderItem("카페라떼",1,4000,"ice","톨")
        ),"포장")
        assertEquals("따뜻한 아메리카노 라지 2개\n아이스 카페라떼 톨 1개\n포장",order.reviewText())
        assertEquals("따뜻한 아메리카노 라지 2개, 아이스 카페라떼 톨 1개, 포장. 이 주문으로 안내합니다.",order.confirmation())
    }
    @Test fun reviewDoesNotInventOptionsOrPriceWhenMissing() {
        val order=NativeOrder(listOf(NativeOrderItem("물",1,null)),null)
        assertEquals("물 1개",order.reviewText())
        assertEquals("물 1개. 이 주문으로 안내합니다.",order.confirmation())
    }
}
